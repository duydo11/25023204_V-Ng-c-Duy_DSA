/**
 * Instrumented versions of Insertion, Merge, and MergeOptimized that mirror
 * the lecture implementations line-for-line while counting exact element
 * comparisons (compares) and array reads/writes (arrayAccesses).
 *
 * Kept separate from Insertion.java / Merge.java / MergeOptimized.java so that
 * wall-clock timing benchmarks remain 100% free of instrumentation overhead.
 */
public class OperationAnalyzer {

    public static class Counts {
        public long compares = 0;
        public long arrayAccesses = 0;
        public long insertionCalls = 0;
        public long mergeCalls = 0;
        public long skippedMerges = 0;
    }

    private OperationAnalyzer() {}

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean less(Comparable v, Comparable w, Counts c) {
        c.compares++;
        return v.compareTo(w) < 0;
    }

    @SuppressWarnings("rawtypes")
    private static void insertionSort(Comparable[] a, int lo, int hi, Counts c) {
        c.insertionCalls++;
        for (int i = lo; i <= hi; i++) {
            for (int j = i; j > lo; j--) {
                // Read a[j] and a[j-1] for less(a[j], a[j-1])
                c.arrayAccesses += 2;
                if (!less(a[j], a[j - 1], c)) {
                    break;
                }
                // exch(a, j, j-1): 2 reads + 2 writes
                Comparable swap = a[j];
                a[j] = a[j - 1];
                a[j - 1] = swap;
                c.arrayAccesses += 4;
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void mergeBasic(Comparable[] a, Comparable[] aux, int lo, int mid, int hi, Counts c) {
        c.mergeCalls++;
        for (int k = lo; k <= hi; k++) {
            aux[k] = a[k];
            c.arrayAccesses += 2; // 1 read a[k], 1 write aux[k]
        }

        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) {
                a[k] = aux[j++];
                c.arrayAccesses += 2;
            } else if (j > hi) {
                a[k] = aux[i++];
                c.arrayAccesses += 2;
            } else {
                c.arrayAccesses += 2; // read aux[j], aux[i] for comparison
                if (less(aux[j], aux[i], c)) {
                    a[k] = aux[j++];
                } else {
                    a[k] = aux[i++];
                }
                c.arrayAccesses += 2; // read aux[...], write a[k]
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void sortBasicNoCutoff(Comparable[] a, Comparable[] aux, int lo, int hi, Counts c) {
        if (hi <= lo) return;
        int mid = lo + (hi - lo) / 2;
        sortBasicNoCutoff(a, aux, lo, mid, c);
        sortBasicNoCutoff(a, aux, mid + 1, hi, c);
        mergeBasic(a, aux, lo, mid, hi, c);
    }

    @SuppressWarnings("rawtypes")
    private static void sortBasicWithCutoff(Comparable[] a, Comparable[] aux, int lo, int hi, int cutoff, Counts c) {
        if (hi <= lo + cutoff - 1) {
            insertionSort(a, lo, hi, c);
            return;
        }
        int mid = lo + (hi - lo) / 2;
        sortBasicWithCutoff(a, aux, lo, mid, cutoff, c);
        sortBasicWithCutoff(a, aux, mid + 1, hi, cutoff, c);
        mergeBasic(a, aux, lo, mid, hi, c);
    }

    @SuppressWarnings("rawtypes")
    public static Counts countMerge(Comparable[] a, int cutoff) {
        Counts c = new Counts();
        if (a == null || a.length <= 1) return c;
        Comparable[] aux = new Comparable[a.length];
        if (cutoff <= 1) {
            sortBasicNoCutoff(a, aux, 0, a.length - 1, c);
        } else {
            sortBasicWithCutoff(a, aux, 0, a.length - 1, cutoff, c);
        }
        return c;
    }

    @SuppressWarnings("rawtypes")
    private static void mergeOpt(Comparable[] src, Comparable[] dst, int lo, int mid, int hi, Counts c) {
        c.mergeCalls++;
        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) {
                dst[k] = src[j++];
                c.arrayAccesses += 2;
            } else if (j > hi) {
                dst[k] = src[i++];
                c.arrayAccesses += 2;
            } else {
                c.arrayAccesses += 2; // read src[j], src[i]
                if (less(src[j], src[i], c)) {
                    dst[k] = src[j++];
                } else {
                    dst[k] = src[i++];
                }
                c.arrayAccesses += 2; // read src[...], write dst[k]
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static void sortOptNoCutoff(Comparable[] src, Comparable[] dst, int lo, int hi, Counts c) {
        if (hi <= lo) return;
        int mid = lo + (hi - lo) / 2;
        sortOptNoCutoff(dst, src, lo, mid, c);
        sortOptNoCutoff(dst, src, mid + 1, hi, c);

        c.arrayAccesses += 2; // read src[mid+1], src[mid]
        if (!less(src[mid + 1], src[mid], c)) {
            int len = hi - lo + 1;
            System.arraycopy(src, lo, dst, lo, len);
            c.arrayAccesses += 2L * len;
            c.skippedMerges++;
            return;
        }
        mergeOpt(src, dst, lo, mid, hi, c);
    }

    @SuppressWarnings("rawtypes")
    private static void sortOptWithCutoff(Comparable[] src, Comparable[] dst, int lo, int hi, int cutoff, Counts c) {
        if (hi <= lo + cutoff - 1) {
            insertionSort(dst, lo, hi, c);
            return;
        }
        int mid = lo + (hi - lo) / 2;
        sortOptWithCutoff(dst, src, lo, mid, cutoff, c);
        sortOptWithCutoff(dst, src, mid + 1, hi, cutoff, c);

        c.arrayAccesses += 2; // read src[mid+1], src[mid]
        if (!less(src[mid + 1], src[mid], c)) {
            int len = hi - lo + 1;
            System.arraycopy(src, lo, dst, lo, len);
            c.arrayAccesses += 2L * len;
            c.skippedMerges++;
            return;
        }
        mergeOpt(src, dst, lo, mid, hi, c);
    }

    @SuppressWarnings("rawtypes")
    public static Counts countMergeOptimized(Comparable[] a, int cutoff) {
        Counts c = new Counts();
        if (a == null || a.length <= 1) return c;
        Comparable[] aux = a.clone();
        c.arrayAccesses += 2L * a.length; // initial copy a -> aux
        if (cutoff <= 1) {
            sortOptNoCutoff(aux, a, 0, a.length - 1, c);
        } else {
            sortOptWithCutoff(aux, a, 0, a.length - 1, cutoff, c);
        }
        return c;
    }
}
