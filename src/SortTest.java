import java.util.Arrays;
import java.util.Random;

/**
 * Correctness and stability verification tests for Insertion, Merge, and MergeOptimized
 * across edge cases, various sizes, distributions, and cutoffs.
 */
public class SortTest {

    /** Helper Comparable item that carries an original index to verify stability. */
    private static class Item implements Comparable<Item> {
        final int key;
        final int origIndex;

        Item(int key, int origIndex) {
            this.key = key;
            this.origIndex = origIndex;
        }

        @Override
        public int compareTo(Item other) {
            return Integer.compare(this.key, other.key);
        }
    }

    private static void assertSortedAndSameMultiset(Integer[] actual, Integer[] original, String label) {
        Integer[] expected = original.clone();
        Arrays.sort(expected);
        if (!Arrays.equals(actual, expected)) {
            throw new AssertionError("Sort mismatch for " + label +
                " (len=" + original.length + ")");
        }
    }

    private static void assertStable(Item[] actual, String label) {
        for (int i = 1; i < actual.length; i++) {
            if (actual[i].key < actual[i - 1].key) {
                throw new AssertionError("Not sorted at i=" + i + " for " + label);
            }
            if (actual[i].key == actual[i - 1].key && actual[i].origIndex < actual[i - 1].origIndex) {
                throw new AssertionError("Stability violated at i=" + i + " for " + label);
            }
        }
    }

    public static void main(String[] args) {
        int[] cutoffs = {1, 2, 3, 5, 7, 10, 15, 25, 64};
        int[] sizes = {0, 1, 2, 3, 4, 5, 7, 8, 9, 10, 15, 16, 17, 31, 32, 63, 100, 1000, 5000};
        Random rng = new Random(42L);
        int passed = 0;

        // 1. Test Insertion.sort on edge and random cases
        for (int n : sizes) {
            Integer[] base = new Integer[n];
            for (int i = 0; i < n; i++) base[i] = rng.nextInt(500);
            Integer[] copy = base.clone();
            Insertion.sort(copy);
            assertSortedAndSameMultiset(copy, base, "Insertion random");
            passed++;
        }

        // 2. Test Merge & MergeOptimized across sizes, distributions, and cutoffs
        for (int cutoff : cutoffs) {
            for (int n : sizes) {
                Integer[][] dists = new Integer[4][n];
                for (int i = 0; i < n; i++) {
                    dists[0][i] = rng.nextInt(10_000);   // random
                    dists[1][i] = i;                     // sorted
                    dists[2][i] = n - i;                 // reverse
                    dists[3][i] = rng.nextInt(5);        // heavy duplicates
                }
                String[] distNames = {"random", "sorted", "reverse", "duplicates"};

                for (int d = 0; d < dists.length; d++) {
                    Integer[] a1 = dists[d].clone();
                    Merge.sort(a1, cutoff);
                    assertSortedAndSameMultiset(a1, dists[d], "Merge cutoff=" + cutoff + " " + distNames[d]);
                    passed++;

                    Integer[] a2 = dists[d].clone();
                    MergeOptimized.sort(a2, cutoff);
                    assertSortedAndSameMultiset(a2, dists[d], "MergeOptimized cutoff=" + cutoff + " " + distNames[d]);
                    passed++;
                }
            }

            // 3. Stability test with duplicate keys
            for (int n : new int[]{10, 50, 500}) {
                Item[] baseItems = new Item[n];
                for (int i = 0; i < n; i++) {
                    baseItems[i] = new Item(rng.nextInt(7), i);
                }
                Item[] c1 = baseItems.clone();
                Merge.sort(c1, cutoff);
                assertStable(c1, "Merge stability cutoff=" + cutoff + " n=" + n);
                passed++;

                Item[] c2 = baseItems.clone();
                MergeOptimized.sort(c2, cutoff);
                assertStable(c2, "MergeOptimized stability cutoff=" + cutoff + " n=" + n);
                passed++;
            }
        }

        // 4. Verify default sort(Comparable[]) entry points (using DEFAULT_CUTOFF)
        Integer[] defBase = new Integer[2000];
        for (int i = 0; i < defBase.length; i++) defBase[i] = rng.nextInt(10_000);
        Integer[] def1 = defBase.clone();
        Merge.sort(def1);
        assertSortedAndSameMultiset(def1, defBase, "Merge default sort");
        passed++;

        Integer[] def2 = defBase.clone();
        MergeOptimized.sort(def2);
        assertSortedAndSameMultiset(def2, defBase, "MergeOptimized default sort");
        passed++;

        System.out.println("ALL TESTS PASSED (" + passed + " test cases verified).");
    }
}
