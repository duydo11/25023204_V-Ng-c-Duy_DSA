/**
 * Insertion sort implementation based on Sedgewick & Wayne,
 * Algorithms (4th Edition), Lecture 2.2 Mergesort (Slides 18, 52).
 */
public class Insertion {

    private Insertion() {}

    /**
     * Rearranges the array in ascending order, using the natural order.
     *
     * @param a the array to be sorted
     */
    @SuppressWarnings("rawtypes")
    public static void sort(Comparable[] a) {
        if (a == null || a.length <= 1) return;
        sort(a, 0, a.length - 1);
    }

    /**
     * Rearranges the subarray a[lo..hi] in ascending order, using the natural order.
     *
     * @param a  the array to be sorted
     * @param lo left endpoint (inclusive)
     * @param hi right endpoint (inclusive)
     */
    @SuppressWarnings("rawtypes")
    public static void sort(Comparable[] a, int lo, int hi) {
        for (int i = lo; i <= hi; i++) {
            for (int j = i; j > lo && less(a[j], a[j - 1]); j--) {
                exch(a, j, j - 1);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean less(Comparable v, Comparable w) {
        return v.compareTo(w) < 0;
    }

    private static void exch(Object[] a, int i, int j) {
        Object swap = a[i];
        a[i] = a[j];
        a[j] = swap;
    }
}
