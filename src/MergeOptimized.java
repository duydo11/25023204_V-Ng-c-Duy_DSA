/**
 * Optimized top-down Mergesort combining all three practical improvements from
 * Sedgewick & Wayne, Lecture 2.2 Mergesort (Slides 18, 20, 21, 22):
 *   1. Cutoff to Insertion sort for small subarrays
 *   2. Stop if already sorted (test !less(src[mid+1], src[mid]))
 *   3. Eliminate copy to auxiliary array by switching roles of src[] and dst[]
 */
public class MergeOptimized {

    /** Empirical optimal cutoff on AMD Ryzen 7 H 260 + OpenJDK 21 (Experiment 3). */
    public static final int DEFAULT_CUTOFF = 10;

    private MergeOptimized() {}

    /**
     * Merges sorted subarrays src[lo..mid] and src[mid+1..hi] directly into dst[lo..hi].
     * (Lecture 2.2, Slide 21)
     */
    @SuppressWarnings("rawtypes")
    private static void merge(Comparable[] src, Comparable[] dst, int lo, int mid, int hi) {
        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if      (i > mid)              dst[k] = src[j++];
            else if (j > hi)               dst[k] = src[i++];
            else if (less(src[j], src[i])) dst[k] = src[j++];
            else                           dst[k] = src[i++];
        }
    }

    @SuppressWarnings("rawtypes")
    private static void sortNoCutoff(Comparable[] src, Comparable[] dst, int lo, int hi) {
        if (hi <= lo) return;
        int mid = lo + (hi - lo) / 2;
        sortNoCutoff(dst, src, lo, mid);
        sortNoCutoff(dst, src, mid + 1, hi);

        if (!less(src[mid + 1], src[mid])) {
            System.arraycopy(src, lo, dst, lo, hi - lo + 1);
            return;
        }
        merge(src, dst, lo, mid, hi);
    }

    @SuppressWarnings("rawtypes")
    private static void sortWithCutoff(Comparable[] src, Comparable[] dst, int lo, int hi, int cutoff) {
        if (hi <= lo + cutoff - 1) {
            Insertion.sort(dst, lo, hi);
            return;
        }
        int mid = lo + (hi - lo) / 2;
        sortWithCutoff(dst, src, lo, mid, cutoff);
        sortWithCutoff(dst, src, mid + 1, hi, cutoff);

        if (!less(src[mid + 1], src[mid])) {
            System.arraycopy(src, lo, dst, lo, hi - lo + 1);
            return;
        }
        merge(src, dst, lo, mid, hi);
    }

    /**
     * Rearranges the array in ascending order using the default cutoff.
     */
    @SuppressWarnings("rawtypes")
    public static void sort(Comparable[] a) {
        sort(a, DEFAULT_CUTOFF);
    }

    /**
     * Rearranges the array in ascending order using the specified cutoff.
     */
    @SuppressWarnings("rawtypes")
    public static void sort(Comparable[] a, int cutoff) {
        if (a == null || a.length <= 1) return;
        Comparable[] aux = a.clone();
        if (cutoff <= 1) {
            sortNoCutoff(aux, a, 0, a.length - 1);
        } else {
            sortWithCutoff(aux, a, 0, a.length - 1, cutoff);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean less(Comparable v, Comparable w) {
        return v.compareTo(w) < 0;
    }
}
