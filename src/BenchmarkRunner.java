import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Empirical benchmark harness following Sedgewick & Wayne (1.3 Algorithm Analysis):
 *  - Exp 1: Direct crossover on small arrays n = 2..40 (Insertion vs pure Merge vs pure MergeOptimized)
 *  - Exp 2: CUTOFF sweep across fixed sizes (100k, 500k, 1M) and 5 input distributions (500k)
 *  - Exp 3: Octave-averaged CUTOFF sweep across 16 log-spaced sizes in [100k, 200k) to eliminate
 *           power-of-2 leaf-size quantization plateaus
 *  - Exp 4: Doubling Test (N = 10k .. 1,280k) for CUTOFF = 1, 7, 10, and empirical optima
 *  - Exp 5: Exact operation counts (# compares, # array accesses, # insertion/merge calls)
 */
public class BenchmarkRunner {

    private static final Path DATA_DIR = Paths.get("data");
    private static final Path RESULTS_DIR = Paths.get("results");
    private static volatile int BLACKHOLE = 0;

    private static final int[] CUTOFF_GRID_FULL = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        16, 17, 18, 19, 20, 22, 24, 28, 32, 40, 50, 64
    };

    private static final int[] CUTOFF_GRID_DIST = {
        1, 2, 4, 6, 7, 8, 10, 12, 14, 16, 18, 20, 24, 32, 48, 64
    };

    private static final int[] CUTOFF_GRID_1M = {
        1, 5, 7, 10, 12, 14, 16, 18, 20, 24, 32, 48, 64
    };

    private static final int[] OCTAVE_CUTOFF_GRID = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15,
        16, 17, 18, 19, 20, 22, 24, 28, 32, 40, 50
    };

    private static Integer[] loadDataset(String filename) throws IOException {
        Path p = DATA_DIR.resolve(filename);
        List<Integer> list = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    list.add(Integer.valueOf(line));
                }
            }
        }
        return list.toArray(new Integer[0]);
    }

    private static void verifySorted(Integer[] a, String context) {
        for (int i = 1; i < a.length; i++) {
            if (a[i].compareTo(a[i - 1]) < 0) {
                throw new AssertionError("Sort failed at index " + i + " in " + context);
            }
        }
        BLACKHOLE ^= a[0].hashCode() ^ a[a.length - 1].hashCode();
    }

    private static void runSort(String algo, Integer[] arr, int cutoff) {
        if ("Merge".equals(algo)) {
            Merge.sort(arr, cutoff);
        } else if ("MergeOptimized".equals(algo)) {
            MergeOptimized.sort(arr, cutoff);
        } else if ("Insertion".equals(algo)) {
            Insertion.sort(arr);
        } else {
            throw new IllegalArgumentException("Unknown algo: " + algo);
        }
    }

    private static void warmupJVM(Integer[] sample100k, Integer[] sample500k) {
        System.out.println("[Warmup] Warming up JVM JIT compiler (100k & 500k)...");
        long start = System.nanoTime();
        String[] algos = {"Merge", "MergeOptimized"};
        int[] warmCutoffs = {1, 4, 7, 10, 16, 32};
        for (int iter = 0; iter < 10; iter++) {
            for (String algo : algos) {
                for (int c : warmCutoffs) {
                    Integer[] copy = sample100k.clone();
                    runSort(algo, copy, c);
                    verifySorted(copy, "warmup");
                }
            }
            for (int off = 0; off < 2000; off += 20) {
                Integer[] slice = Arrays.copyOfRange(sample100k, off, off + 20);
                Insertion.sort(slice);
                verifySorted(slice, "warmup-ins");
            }
        }
        // Warm up larger 500k allocations & cache lines
        for (int iter = 0; iter < 3; iter++) {
            for (String algo : algos) {
                Integer[] copy = sample500k.clone();
                runSort(algo, copy, 10);
                verifySorted(copy, "warmup-500k");
            }
        }
        System.gc();
        double elapsed = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.US, "[Warmup] Completed in %.2f s.%n", elapsed);
    }

    private static void runPilot() throws IOException {
        Integer[] r100k = loadDataset("random_100000.txt");
        Integer[] r500k = loadDataset("random_500000.txt");
        warmupJVM(r100k, r500k);
        for (String algo : new String[]{"Merge", "MergeOptimized"}) {
            for (int c : new int[]{1, 7, 10, 14, 20, 32}) {
                long totalNs = 0;
                int trials = 5;
                for (int t = 0; t < trials; t++) {
                    Integer[] work = r500k.clone();
                    long t0 = System.nanoTime();
                    runSort(algo, work, c);
                    totalNs += (System.nanoTime() - t0);
                    verifySorted(work, "pilot");
                }
                System.out.printf(Locale.US, "PILOT %-14s | N=500,000 | cutoff=%2d | avg=%.2f ms%n",
                    algo, c, (totalNs / (double) trials) / 1e6);
            }
        }
    }

    /**
     * Experiment 1: Direct crossover on small arrays n = 2..100.
     * Slices B = 4,000 distinct subarrays from random_1000000.txt per n.
     */
    private static void runExp1Crossover(Integer[] pool1M) throws IOException {
        System.out.println("[Exp 1] Running small-array crossover benchmark (n = 2..100)...");
        List<Integer> smallSizes = new ArrayList<>();
        for (int n = 2; n <= 30; n++) smallSizes.add(n);
        for (int n : new int[]{32, 34, 36, 38, 40, 45, 50, 55, 60, 70, 80, 90, 100}) {
            smallSizes.add(n);
        }

        final int batchSize = 4000;
        final int trials = 15;
        final String[] algos = {"Insertion", "Merge", "MergeOptimized"};
        Random rng = new Random(1001L);

        Path out = RESULTS_DIR.resolve("raw_crossover.csv");
        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write("algorithm,n,trial,batch_size,total_ns,ns_per_sort\n");

            for (int n : smallSizes) {
                Integer[][] baseBatch = new Integer[batchSize][n];
                for (int b = 0; b < batchSize; b++) {
                    int start = (b * 241) % (pool1M.length - n);
                    System.arraycopy(pool1M, start, baseBatch[b], 0, n);
                }

                for (int t = 1; t <= trials; t++) {
                    List<String> order = new ArrayList<>(Arrays.asList(algos));
                    Collections.shuffle(order, rng);
                    for (String algo : order) {
                        Integer[][] work = new Integer[batchSize][];
                        for (int b = 0; b < batchSize; b++) {
                            work[b] = baseBatch[b].clone();
                        }

                        long t0 = System.nanoTime();
                        for (int b = 0; b < batchSize; b++) {
                            runSort(algo, work[b], 1);
                        }
                        long elapsed = System.nanoTime() - t0;

                        verifySorted(work[0], "exp1");
                        verifySorted(work[batchSize - 1], "exp1");

                        double perSort = elapsed / (double) batchSize;
                        bw.write(String.format(Locale.US, "%s,%d,%d,%d,%d,%.3f%n",
                            algo, n, t, batchSize, elapsed, perSort));
                    }
                }
            }
        }
        System.out.println("[Exp 1] Saved " + out);
    }

    /**
     * Experiment 2: CUTOFF sweep on fixed dataset sizes and distributions.
     */
    private static void runExp2SingleN() throws IOException {
        System.out.println("[Exp 2] Running Single-N CUTOFF sweeps across sizes & distributions...");
        Object[][] specs = {
            // filename, dist, n, batch_size, trials, cutoff_grid
            {"random_100000.txt",        "random",        100000,  2, 8,  CUTOFF_GRID_DIST},
            {"random_500000.txt",        "random",        500000,  1, 10, CUTOFF_GRID_FULL},
            {"sorted_500000.txt",        "sorted",        500000,  4, 6,  CUTOFF_GRID_DIST},
            {"reverse_500000.txt",       "reverse",       500000,  1, 6,  CUTOFF_GRID_DIST},
            {"nearly_sorted_500000.txt", "nearly_sorted", 500000,  1, 6,  CUTOFF_GRID_DIST},
            {"duplicates_500000.txt",    "duplicates",    500000,  1, 6,  CUTOFF_GRID_DIST},
            {"random_1000000.txt",       "random",        1000000, 1, 5,  CUTOFF_GRID_1M},
        };

        Random rng = new Random(2002L);
        Path out = RESULTS_DIR.resolve("raw_cutoff_single_n.csv");
        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write("algorithm,distribution,n,cutoff,trial,batch_size,time_ms\n");

            for (Object[] spec : specs) {
                String fname = (String) spec[0];
                String dist = (String) spec[1];
                int n = (Integer) spec[2];
                int batchSize = (Integer) spec[3];
                int trials = (Integer) spec[4];
                int[] grid = (int[]) spec[5];

                Integer[] base = loadDataset(fname);
                System.out.printf(Locale.US, "  -> Dataset %-26s (N=%,d, trials=%d, grid=%d, batch=%d)%n",
                    fname, n, trials, grid.length, batchSize);

                // Unrecorded per-dataset warmup + GC compaction before timed trials
                for (String algo : new String[]{"Merge", "MergeOptimized"}) {
                    Integer[] w = base.clone();
                    runSort(algo, w, 10);
                    verifySorted(w, "prewarm-" + fname);
                }
                System.gc();

                for (int t = 1; t <= trials; t++) {
                    if (t % 2 == 1) {
                        System.gc();
                    }
                    List<String[]> tasks = new ArrayList<>();
                    for (String algo : new String[]{"Merge", "MergeOptimized"}) {
                        for (int c : grid) {
                            tasks.add(new String[]{algo, String.valueOf(c)});
                        }
                    }
                    Collections.shuffle(tasks, rng);

                    for (String[] task : tasks) {
                        String algo = task[0];
                        int cutoff = Integer.parseInt(task[1]);

                        Integer[][] clones = new Integer[batchSize][];
                        for (int b = 0; b < batchSize; b++) {
                            clones[b] = base.clone();
                        }

                        long t0 = System.nanoTime();
                        for (int b = 0; b < batchSize; b++) {
                            runSort(algo, clones[b], cutoff);
                        }
                        long elapsed = System.nanoTime() - t0;

                        verifySorted(clones[0], "exp2-" + fname);
                        double msPerSort = (elapsed / (double) batchSize) / 1e6;
                        bw.write(String.format(Locale.US, "%s,%s,%d,%d,%d,%d,%.4f%n",
                            algo, dist, n, cutoff, t, batchSize, msPerSort));
                    }
                }
            }
        }
        System.out.println("[Exp 2] Saved " + out);
    }

    /**
     * Experiment 3: Octave-averaged CUTOFF sweep across 16 log-spaced sizes in [100,000, 200,000)
     * so leaf-subarray sizes span every integer smoothly without power-of-2 plateau artifacts.
     */
    private static void runExp3Octave(Integer[] pool1M) throws IOException {
        System.out.println("[Exp 3] Running Octave-averaged CUTOFF sweep (16 sizes in [100k, 200k))...");
        final int numSizes = 16;
        final int rounds = 3; // 16 * 3 = 48 independent runs per cutoff per algorithm
        int[] sizes = new int[numSizes];
        for (int k = 0; k < numSizes; k++) {
            sizes[k] = (int) Math.floor(100_000.0 * Math.pow(2.0, k / (double) numSizes));
        }

        Random rng = new Random(3003L);
        Path out = RESULTS_DIR.resolve("raw_cutoff_octave.csv");
        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write("algorithm,size_index,n,cutoff,round,time_ms,ns_per_nlogn\n");

            for (int k = 0; k < numSizes; k++) {
                int n = sizes[k];
                int offset = (k * 41_000) % (pool1M.length - n);
                Integer[] base = Arrays.copyOfRange(pool1M, offset, offset + n);
                double nLog2N = n * (Math.log(n) / Math.log(2.0));

                for (int r = 1; r <= rounds; r++) {
                    List<String[]> tasks = new ArrayList<>();
                    for (String algo : new String[]{"Merge", "MergeOptimized"}) {
                        for (int c : OCTAVE_CUTOFF_GRID) {
                            tasks.add(new String[]{algo, String.valueOf(c)});
                        }
                    }
                    Collections.shuffle(tasks, rng);

                    for (String[] task : tasks) {
                        String algo = task[0];
                        int cutoff = Integer.parseInt(task[1]);

                        Integer[] work = base.clone();
                        long t0 = System.nanoTime();
                        runSort(algo, work, cutoff);
                        long elapsedNs = System.nanoTime() - t0;

                        verifySorted(work, "exp3-n" + n);
                        double ms = elapsedNs / 1e6;
                        double normNs = elapsedNs / nLog2N;
                        bw.write(String.format(Locale.US, "%s,%d,%d,%d,%d,%.4f,%.4f%n",
                            algo, k, n, cutoff, r, ms, normNs));
                    }
                }
                System.out.printf(Locale.US, "  -> Completed octave size %2d/16 (N=%,d)%n", k + 1, n);
            }
        }
        System.out.println("[Exp 3] Saved " + out);
    }

    /**
     * Experiment 4: Doubling Test (1.3 Algorithm Analysis) for N = 10k .. 1,280k.
     */
    private static void runExp4DoublingTest(int[] extraCutoffs) throws IOException {
        System.out.println("[Exp 4] Running Doubling Test (N = 10,000 .. 1,280,000)...");
        int[] doublingSizes = {10_000, 20_000, 40_000, 80_000, 160_000, 320_000, 640_000, 1_280_000};
        int[] batchSizes    = {20,     10,     5,      3,      2,       1,       1,       1};

        Set<Integer> cutoffSet = new LinkedHashSet<>();
        cutoffSet.add(1);
        cutoffSet.add(7);
        cutoffSet.add(10);
        cutoffSet.add(16);
        cutoffSet.add(20);
        for (int c : extraCutoffs) cutoffSet.add(c);
        List<Integer> cutoffs = new ArrayList<>(cutoffSet);

        final int trials = 8;
        Random rng = new Random(4004L);
        Path out = RESULTS_DIR.resolve("raw_doubling_test.csv");
        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write("algorithm,n,cutoff,trial,batch_size,time_ms\n");

            for (int idx = 0; idx < doublingSizes.length; idx++) {
                int n = doublingSizes[idx];
                int batchSize = batchSizes[idx];
                Integer[] base = loadDataset("random_" + n + ".txt");
                System.out.printf(Locale.US, "  -> Doubling step N=%,9d (batch=%d, trials=%d)%n",
                    n, batchSize, trials);

                for (String algo : new String[]{"Merge", "MergeOptimized"}) {
                    Integer[] w = base.clone();
                    runSort(algo, w, 10);
                    verifySorted(w, "prewarm-doubling");
                }
                System.gc();

                for (int t = 1; t <= trials; t++) {
                    if (t % 2 == 1) {
                        System.gc();
                    }
                    List<String[]> tasks = new ArrayList<>();
                    for (String algo : new String[]{"Merge", "MergeOptimized"}) {
                        for (int c : cutoffs) {
                            tasks.add(new String[]{algo, String.valueOf(c)});
                        }
                    }
                    Collections.shuffle(tasks, rng);

                    for (String[] task : tasks) {
                        String algo = task[0];
                        int cutoff = Integer.parseInt(task[1]);

                        Integer[][] clones = new Integer[batchSize][];
                        for (int b = 0; b < batchSize; b++) {
                            clones[b] = base.clone();
                        }

                        long t0 = System.nanoTime();
                        for (int b = 0; b < batchSize; b++) {
                            runSort(algo, clones[b], cutoff);
                        }
                        long elapsedNs = System.nanoTime() - t0;

                        verifySorted(clones[0], "exp4-n" + n);
                        double msPerSort = (elapsedNs / (double) batchSize) / 1e6;
                        bw.write(String.format(Locale.US, "%s,%d,%d,%d,%d,%.4f%n",
                            algo, n, cutoff, t, batchSize, msPerSort));
                    }
                }
            }
        }
        System.out.println("[Exp 4] Saved " + out);
    }

    /**
     * Experiment 5: Exact operation counts (# compares, # array accesses, # calls)
     * across CUTOFF values on N = 500,000 distributions.
     */
    private static void runExp5OperationCounts() throws IOException {
        System.out.println("[Exp 5] Counting exact compares and array accesses...");
        String[][] specs = {
            {"random_500000.txt",        "random"},
            {"sorted_500000.txt",        "sorted"},
            {"reverse_500000.txt",       "reverse"},
            {"nearly_sorted_500000.txt", "nearly_sorted"},
            {"duplicates_500000.txt",    "duplicates"},
        };

        Path out = RESULTS_DIR.resolve("raw_operation_counts.csv");
        try (BufferedWriter bw = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            bw.write("algorithm,distribution,n,cutoff,compares,array_accesses,insertion_calls,merge_calls,skipped_merges\n");

            for (String[] spec : specs) {
                Integer[] base = loadDataset(spec[0]);
                String dist = spec[1];
                int n = base.length;

                for (int c : CUTOFF_GRID_FULL) {
                    Integer[] w1 = base.clone();
                    OperationAnalyzer.Counts c1 = OperationAnalyzer.countMerge(w1, c);
                    verifySorted(w1, "op-merge-" + dist);
                    bw.write(String.format(Locale.US, "Merge,%s,%d,%d,%d,%d,%d,%d,%d%n",
                        dist, n, c, c1.compares, c1.arrayAccesses, c1.insertionCalls, c1.mergeCalls, c1.skippedMerges));

                    Integer[] w2 = base.clone();
                    OperationAnalyzer.Counts c2 = OperationAnalyzer.countMergeOptimized(w2, c);
                    verifySorted(w2, "op-mergeopt-" + dist);
                    bw.write(String.format(Locale.US, "MergeOptimized,%s,%d,%d,%d,%d,%d,%d,%d%n",
                        dist, n, c, c2.compares, c2.arrayAccesses, c2.insertionCalls, c2.mergeCalls, c2.skippedMerges));
                }
            }
        }
        System.out.println("[Exp 5] Saved " + out);
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(RESULTS_DIR);
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "all";

        if ("pilot".equals(mode)) {
            runPilot();
            return;
        }

        Integer[] pool100k = loadDataset("random_100000.txt");
        Integer[] pool500k = loadDataset("random_500000.txt");
        Integer[] pool1M = loadDataset("random_1000000.txt");
        warmupJVM(pool100k, pool500k);

        int[] extraDoublingCutoffs = new int[0];
        if (args.length > 1) {
            String[] parts = args[1].split(",");
            extraDoublingCutoffs = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                extraDoublingCutoffs[i] = Integer.parseInt(parts[i].trim());
            }
        }

        String[] tokens = mode.split(",");
        java.util.Set<String> modes = new java.util.HashSet<>(Arrays.asList(tokens));

        long tAll = System.nanoTime();
        if (modes.contains("all") || modes.contains("1")) runExp1Crossover(pool1M);
        if (modes.contains("all") || modes.contains("2")) runExp2SingleN();
        if (modes.contains("all") || modes.contains("3")) runExp3Octave(pool1M);
        if (modes.contains("all") || modes.contains("4")) runExp4DoublingTest(extraDoublingCutoffs);
        if (modes.contains("all") || modes.contains("5")) runExp5OperationCounts();

        System.out.printf(Locale.US, "%nBENCHMARKS FINISHED in %.2f s (blackhole=%d)%n",
            (System.nanoTime() - tAll) / 1e9, BLACKHOLE);
    }
}
