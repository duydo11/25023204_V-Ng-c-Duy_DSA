"""
Generate deterministic experimental datasets for Mergesort CUTOFF experiments.

Each dataset is saved as a plain-text file in data/ (one integer per line)
along with metadata in data/manifest.json for full reproducibility.
"""

import json
import os
import random
from pathlib import Path

DATA_DIR = Path(__file__).resolve().parent.parent / "data"
MAX_VAL = 1_000_000_000


def write_array(path: Path, arr: list[int]) -> None:
    with open(path, "w", encoding="utf-8", buffering=1024 * 1024) as f:
        f.write("\n".join(map(str, arr)))
        f.write("\n")


def gen_random(n: int, seed: int) -> list[int]:
    rng = random.Random(seed)
    return [rng.randrange(MAX_VAL) for _ in range(n)]


def gen_sorted(n: int) -> list[int]:
    return list(range(n))


def gen_reverse(n: int) -> list[int]:
    return list(range(n - 1, -1, -1))


def gen_nearly_sorted(n: int, seed: int, window: int = 8, swaps_ratio: float = 0.5) -> list[int]:
    """
    Start from a sorted sequence 0..n-1 and apply local random swaps within
    distance <= window, keeping inversions bounded O(n) (partially ordered).
    """
    rng = random.Random(seed)
    arr = list(range(n))
    num_swaps = int(n * swaps_ratio)
    for _ in range(num_swaps):
        i = rng.randrange(n)
        j = min(n - 1, max(0, i + rng.randint(-window, window)))
        arr[i], arr[j] = arr[j], arr[i]
    return arr


def gen_duplicates(n: int, seed: int, distinct_keys: int = 10) -> list[int]:
    rng = random.Random(seed)
    return [rng.randrange(distinct_keys) for _ in range(n)]


def main() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    manifest = {"description": "Experimental datasets for Mergesort CUTOFF study", "datasets": []}

    specs = [
        # Multi-distribution suite at N = 500,000
        ("random_500000.txt", 500_000, "random", 202601, lambda: gen_random(500_000, 202601),
         "Uniform random integers in [0, 10^9)"),
        ("sorted_500000.txt", 500_000, "sorted", None, lambda: gen_sorted(500_000),
         "Strictly ascending sequence 0..499999"),
        ("reverse_500000.txt", 500_000, "reverse", None, lambda: gen_reverse(500_000),
         "Strictly descending sequence 499999..0"),
        ("nearly_sorted_500000.txt", 500_000, "nearly_sorted", 202604,
         lambda: gen_nearly_sorted(500_000, 202604, window=8, swaps_ratio=0.5),
         "Sorted 0..499999 with 250,000 local swaps (|i-j| <= 8)"),
        ("duplicates_500000.txt", 500_000, "duplicates", 202605,
         lambda: gen_duplicates(500_000, 202605, distinct_keys=10),
         "Uniform random integers with only 10 distinct keys [0..9]"),

        # Doubling test + size scaling suite (random uniform)
        ("random_10000.txt", 10_000, "random", 202610, lambda: gen_random(10_000, 202610),
         "Uniform random N=10,000 (Doubling Test step 1)"),
        ("random_20000.txt", 20_000, "random", 202611, lambda: gen_random(20_000, 202611),
         "Uniform random N=20,000 (Doubling Test step 2)"),
        ("random_40000.txt", 40_000, "random", 202612, lambda: gen_random(40_000, 202612),
         "Uniform random N=40,000 (Doubling Test step 3)"),
        ("random_80000.txt", 80_000, "random", 202613, lambda: gen_random(80_000, 202613),
         "Uniform random N=80,000 (Doubling Test step 4)"),
        ("random_100000.txt", 100_000, "random", 202614, lambda: gen_random(100_000, 202614),
         "Uniform random N=100,000 (Medium single-N benchmark)"),
        ("random_160000.txt", 160_000, "random", 202615, lambda: gen_random(160_000, 202615),
         "Uniform random N=160,000 (Doubling Test step 5)"),
        ("random_320000.txt", 320_000, "random", 202616, lambda: gen_random(320_000, 202616),
         "Uniform random N=320,000 (Doubling Test step 6)"),
        ("random_640000.txt", 640_000, "random", 202617, lambda: gen_random(640_000, 202617),
         "Uniform random N=640,000 (Doubling Test step 7)"),
        ("random_1000000.txt", 1_000_000, "random", 202618, lambda: gen_random(1_000_000, 202618),
         "Uniform random N=1,000,000 (Large single-N, octave slicing & small-batch pool)"),
        ("random_1280000.txt", 1_280_000, "random", 202619, lambda: gen_random(1_280_000, 202619),
         "Uniform random N=1,280,000 (Doubling Test step 8)"),
    ]

    total_bytes = 0
    for filename, n, dist, seed, builder, desc in specs:
        out_path = DATA_DIR / filename
        arr = builder()
        write_array(out_path, arr)
        size_bytes = out_path.stat().st_size
        total_bytes += size_bytes
        manifest["datasets"].append({
            "file": filename,
            "n": n,
            "distribution": dist,
            "seed": seed,
            "size_bytes": size_bytes,
            "description": desc,
        })
        print(f"Generated {filename:26s} | N={n:>9,d} | {size_bytes / 1024 / 1024:5.2f} MB")

    manifest_path = DATA_DIR / "manifest.json"
    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2, ensure_ascii=False)

    print(f"\nDone! Total dataset size: {total_bytes / 1024 / 1024:.2f} MB ({len(specs)} files).")


if __name__ == "__main__":
    main()
