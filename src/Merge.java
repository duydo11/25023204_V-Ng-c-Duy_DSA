/**
 * Top-down Mergesort with configurable CUTOFF to Insertion sort,
 * built strictly from Sedgewick & Wayne, Algorithms (4th Edition),
 * Lecture 2.2 Mergesort (Slides 7, 8, 18).
 */
public class Merge {

    /** Empirical optimal cutoff on AMD Ryzen 7 H 260 + OpenJDK 21 (Experiment 3). */
    public static final int DEFAULT_CUTOFF = 16;

    private Merge() {}

    /**
     * Stably merges sorted subarrays a[lo..mid] and a[mid+1..hi] using aux[].
     * (Lecture 2.2, Slide 7)
     */
    @SuppressWarnings("rawtypes")
    private static void merge(Comparable[] a, Comparable[] aux, int lo, int mid, int hi) {
        // Copy to aux[]
        for (int k = lo; k <= hi; k++) {
            aux[k] = a[k];
        }

        // Merge back to a[]
        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if      (i > mid)              a[k] = aux[j++];
            else if (j > hi)               a[k] = aux[i++];
            else if (less(aux[j], aux[i])) a[k] = aux[j++];
            else                           a[k] = aux[i++];
        }
    }

    /**
     * Pure top-down mergesort without insertion-sort cutoff (Slide 8).
     */
    @SuppressWarnings("rawtypes")
    private static void sortNoCutoff(Comparable[] a, Comparable[] aux, int lo, int hi) {
        if (hi <= lo) return;
        int mid = lo + (hi - lo) / 2;
        sortNoCutoff(a, aux, lo, mid);
        sortNoCutoff(a, aux, mid + 1, hi);
        merge(a, aux, lo, mid, hi);
    }

    /**
     * Top-down mergesort with cutoff to insertion sort (Slide 18).
     */
    @SuppressWarnings("rawtypes")
    private static void sortWithCutoff(Comparable[] a, Comparable[] aux, int lo, int hi, int cutoff) {
        if (hi <= lo + cutoff - 1) {
            Insertion.sort(a, lo, hi);
            return;
        }
        int mid = lo + (hi - lo) / 2;
        sortWithCutoff(a, aux, lo, mid, cutoff);
        sortWithCutoff(a, aux, mid + 1, hi, cutoff);
        merge(a, aux, lo, mid, hi);
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
     * When cutoff <= 1, pure mergesort (Slide 8) is used without insertion sort.
     */
    @SuppressWarnings("rawtypes")
    public static void sort(Comparable[] a, int cutoff) {
        if (a == null || a.length <= 1) return;
        Comparable[] aux = new Comparable[a.length];
        if (cutoff <= 1) {
            sortNoCutoff(a, aux, 0, a.length - 1);
        } else {
            sortWithCutoff(a, aux, 0, a.length - 1, cutoff);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean less(Comparable v, Comparable w) {
        return v.compareTo(w) < 0;
    }
}
