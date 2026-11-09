"""
Statistical analysis and plotting script for Mergesort CUTOFF experiments
(aligned with Sedgewick & Wayne 1.3 Algorithm Analysis & 2.2 Mergesort).

Reads raw CSV outputs from results/, computes descriptive & inferential
statistics (mean, median, std, 95% CI, speedup, doubling ratios), writes
results/summary_stats.md, and generates 7 figures in results/figures/.
"""

import math
from pathlib import Path
import numpy as np
import pandas as pd
import matplotlib.pyplot as plt

ROOT = Path(__file__).resolve().parent.parent
RESULTS_DIR = ROOT / "results"
FIG_DIR = RESULTS_DIR / "figures"

# Student's t critical values (two-tailed 95% CI, alpha=0.05) by df = n - 1
T_CRIT_95 = {
    1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571,
    6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262, 10: 2.228,
    11: 2.201, 12: 2.179, 13: 2.160, 14: 2.145, 15: 2.131,
    16: 2.120, 17: 2.110, 18: 2.101, 19: 2.093, 20: 2.086,
    24: 2.064, 29: 2.045, 30: 2.042, 40: 2.021, 47: 2.012,
    48: 2.011, 60: 2.000,
}


def t_crit(n: int) -> float:
    df = max(1, n - 1)
    if df in T_CRIT_95:
        return T_CRIT_95[df]
    if df > 60:
        return 1.96
    # Linear interpolation between nearest keys
    keys = sorted(T_CRIT_95.keys())
    for lo, hi in zip(keys, keys[1:]):
        if lo <= df <= hi:
            return T_CRIT_95[lo] + (T_CRIT_95[hi] - T_CRIT_95[lo]) * (df - lo) / (hi - lo)
    return 1.96


def summarize(series: pd.Series) -> dict:
    vals = series.to_numpy(dtype=float)
    n = len(vals)
    mean = float(np.mean(vals))
    median = float(np.median(vals))
    std = float(np.std(vals, ddof=1)) if n > 1 else 0.0
    se = std / math.sqrt(n) if n > 0 else 0.0
    ci95 = t_crit(n) * se
    return {
        "count": n,
        "mean": mean,
        "median": median,
        "std": std,
        "ci95": ci95,
        "min": float(np.min(vals)),
        "max": float(np.max(vals)),
    }


def agg_group(df: pd.DataFrame, group_cols: list[str], val_col: str) -> pd.DataFrame:
    rows = []
    for keys, grp in df.groupby(group_cols, sort=True):
        if not isinstance(keys, tuple):
            keys = (keys,)
        row = dict(zip(group_cols, keys))
        row.update(summarize(grp[val_col]))
        rows.append(row)
    return pd.DataFrame(rows)


def setup_plot_style() -> None:
    plt.rcParams.update({
        "figure.dpi": 150,
        "savefig.dpi": 300,
        "font.size": 10,
        "axes.titlesize": 12,
        "axes.titleweight": "bold",
        "axes.labelsize": 10,
        "axes.grid": True,
        "grid.alpha": 0.3,
        "grid.linestyle": "--",
        "legend.framealpha": 0.92,
    })


def plot_exp1_crossover(df1: pd.DataFrame, md_lines: list[str]) -> dict:
    agg = agg_group(df1, ["algorithm", "n"], "ns_per_sort")

    piv_mean = agg.pivot(index="n", columns="algorithm", values="mean")
    piv_median = agg.pivot(index="n", columns="algorithm", values="median")

    # Find crossover: largest n where Insertion mean < Merge mean (and MergeOptimized mean)
    ins_lt_merge = piv_mean[piv_mean["Insertion"] < piv_mean["Merge"]].index.tolist()
    ins_lt_opt = piv_mean[piv_mean["Insertion"] < piv_mean["MergeOptimized"]].index.tolist()
    cross_merge = max(ins_lt_merge) if ins_lt_merge else None
    cross_opt = max(ins_lt_opt) if ins_lt_opt else None

    fig, ax = plt.subplots(figsize=(9, 5.2))
    colors = {"Insertion": "#d62728", "Merge": "#1f77b4", "MergeOptimized": "#2ca02c"}
    labels = {
        "Insertion": "Insertion Sort (Slide 52)",
        "Merge": "Merge Sort Pure (cutoff=1, Slide 8)",
        "MergeOptimized": "MergeOptimized Pure (cutoff=1, Slide 21)",
    }

    for algo in ["Insertion", "Merge", "MergeOptimized"]:
        sub = agg[agg["algorithm"] == algo].sort_values("n")
        ax.errorbar(
            sub["n"], sub["mean"], yerr=sub["ci95"],
            label=labels[algo], color=colors[algo], marker="o", markersize=4,
            linewidth=1.8, capsize=2.5
        )

    if cross_merge is not None:
        ax.axvline(cross_merge, color="#1f77b4", linestyle=":", alpha=0.8,
                   label=f"Crossover Insertion vs Merge: n = {cross_merge}")
    if cross_opt is not None:
        ax.axvline(cross_opt, color="#2ca02c", linestyle="--", alpha=0.8,
                   label=f"Crossover Insertion vs MergeOpt: n = {cross_opt}")

    ax.set_title("Thí nghiệm 1: So sánh trực tiếp trên mảng nhỏ (n = 2..100, ngẫu nhiên)")
    ax.set_xlabel("Kích thước mảng con n")
    ax.set_ylabel("Thời gian trung bình mỗi lần sắp xếp (ns) ± 95% CI")
    ax.legend(loc="upper left", fontsize=8.8)
    fig.tight_layout()
    fig.savefig(FIG_DIR / "fig1_crossover_small_n.png")
    plt.close(fig)

    md_lines.append("## 1. Thí nghiệm 1: Điểm cắt trực tiếp trên mảng nhỏ (n = 2..100)\n")
    md_lines.append(f"- **Crossover Insertion vs Merge (pure):** `n <= {cross_merge}` (mean), "
                    f"`n <= {max(piv_median[piv_median['Insertion'] < piv_median['Merge']].index.tolist(), default=None)}` (median)")
    md_lines.append(f"- **Crossover Insertion vs MergeOptimized (pure):** `n <= {cross_opt}` (mean)\n")
    md_lines.append("| n | Insertion (ns) | Merge Pure (ns) | MergeOpt Pure (ns) | Insertion nhanh hơn Merge? |")
    md_lines.append("|---:|---:|---:|---:|:---:|")
    for n in [2, 4, 6, 7, 8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 40, 50, 60, 70, 80, 90, 100]:
        if n in piv_mean.index:
            i_v = piv_mean.loc[n, "Insertion"]
            m_v = piv_mean.loc[n, "Merge"]
            o_v = piv_mean.loc[n, "MergeOptimized"]
            flag = "Có" if i_v < m_v else "Không"
            md_lines.append(f"| {n} | {i_v:.1f} | {m_v:.1f} | {o_v:.1f} | {flag} |")
    md_lines.append("")
    return {"cross_merge": cross_merge, "cross_opt": cross_opt}


def plot_exp2_single_n(df2: pd.DataFrame, md_lines: list[str]) -> dict:
    agg = agg_group(df2, ["algorithm", "distribution", "n", "cutoff"], "time_ms")

    # 1. Primary N=500,000 random plot (fig2)
    r500 = agg[(agg["distribution"] == "random") & (agg["n"] == 500_000)]
    fig, axes = plt.subplots(1, 2, figsize=(12.5, 5.0), sharey=False)
    optima_500 = {}

    for idx, algo in enumerate(["Merge", "MergeOptimized"]):
        ax = axes[idx]
        sub = r500[r500["algorithm"] == algo].sort_values("cutoff")
        best_row = sub.loc[sub["mean"].idxmin()]
        best_c = int(best_row["cutoff"])
        optima_500[algo] = (best_c, float(best_row["mean"]), float(best_row["median"]))

        base_mean = float(sub[sub["cutoff"] == 1]["mean"].iloc[0])
        ax.errorbar(
            sub["cutoff"], sub["mean"], yerr=sub["ci95"],
            color="#1f77b4" if algo == "Merge" else "#2ca02c",
            marker="o", markersize=4, linewidth=1.8, capsize=2.5,
            label="Mean ± 95% CI"
        )
        ax.plot(sub["cutoff"], sub["median"], color="#ff7f0e", linestyle="--",
                marker="x", markersize=4, linewidth=1.2, label="Median")

        ax.axvline(7, color="gray", linestyle=":", linewidth=1.3, label="CUTOFF = 7 (cũ)")
        ax.axvline(10, color="purple", linestyle="-.", linewidth=1.3, label="CUTOFF = 10 (mới)")
        ax.axvline(best_c, color="red", linestyle="-", linewidth=1.5,
                   label=f"Min đo được: CUTOFF = {best_c} ({best_row['mean']:.2f} ms)")
        ax.axhline(base_mean, color="black", linestyle=":", alpha=0.5,
                   label=f"Baseline CUTOFF=1 ({base_mean:.2f} ms)")

        ax.set_title(f"{algo} (N = 500,000, Random)")
        ax.set_xlabel("Ngưỡng CUTOFF")
        ax.set_ylabel("Thời gian thực thi (ms)")
        ax.set_xlim(0, 66)
        ymin, ymax = ax.get_ylim()
        ax.set_ylim(ymin, ymax + (ymax - ymin) * 0.18)
        ax.legend(fontsize=8.0, loc="upper right", ncol=2)

    fig.suptitle("Thí nghiệm 2A: Ảnh hưởng của CUTOFF trên N = 500,000 (hiệu ứng bậc thang theo độ sâu cây)",
                 fontsize=13, fontweight="bold")
    fig.tight_layout()
    fig.savefig(FIG_DIR / "fig2_cutoff_single_n.png")
    plt.close(fig)

    # 2. Multi-distribution comparison at N=500,000 (fig4)
    dists = ["random", "nearly_sorted", "duplicates", "reverse", "sorted"]
    dist_titles = {
        "random": "Random (Ngẫu nhiên)",
        "nearly_sorted": "Nearly Sorted (Gần như đã sắp)",
        "duplicates": "Duplicates (10 giá trị phân biệt)",
        "reverse": "Reverse (Giảm dần)",
        "sorted": "Sorted (Tăng dần)",
    }
    fig, axes = plt.subplots(2, 3, figsize=(14, 8.2))
    axes_flat = axes.flatten()
    dist_summary = []

    for i, dist in enumerate(dists):
        ax = axes_flat[i]
        dsub = agg[(agg["distribution"] == dist) & (agg["n"] == 500_000)]
        for algo, col in [("Merge", "#1f77b4"), ("MergeOptimized", "#2ca02c")]:
            asub = dsub[dsub["algorithm"] == algo].sort_values("cutoff")
            best_r = asub.loc[asub["mean"].idxmin()]
            base_r = asub[asub["cutoff"] == 1].iloc[0]
            gain = (base_r["mean"] - best_r["mean"]) / base_r["mean"] * 100.0
            dist_summary.append({
                "distribution": dist,
                "algorithm": algo,
                "base_ms": base_r["mean"],
                "best_cutoff": int(best_r["cutoff"]),
                "best_ms": best_r["mean"],
                "speedup_pct": gain,
            })
            ax.errorbar(
                asub["cutoff"], asub["mean"], yerr=asub["ci95"],
                color=col, marker="o", markersize=3.5, linewidth=1.6, capsize=2,
                label=f"{algo} (best={int(best_r['cutoff'])}, {best_r['mean']:.1f}ms)"
            )
        ax.axvline(7, color="gray", linestyle=":", alpha=0.8)
        ax.axvline(10, color="purple", linestyle="-.", alpha=0.8)
        ax.set_title(dist_titles[dist])
        ax.set_xlabel("CUTOFF")
        ax.set_ylabel("Thời gian (ms)")
        ax.legend(fontsize=7.8)

    axes_flat[5].axis("off")
    ax_note = axes_flat[5]
    note_txt = (
        "Ghi chú phân tích phân phối (N = 500,000):\n\n"
        "• Random & Duplicates: Tối ưu nằm ở vùng CUTOFF trung bình.\n"
        "• Nearly Sorted: Insertion sort tận dụng độ trật tự sẵn có O(N).\n"
        "• Sorted: MergeOptimized gần như O(N) nhờ kiểm tra\n"
        "  !less(src[mid+1], src[mid]).\n"
        "• Reverse: Insertion sort tốn ~M^2/2 trên mảng con đảo ngược,\n"
        "  nên CUTOFF lớn làm thời gian tăng vọt rõ rệt."
    )
    ax_note.text(0.05, 0.5, note_txt, va="center", fontsize=9.5,
                 bbox=dict(boxstyle="round,pad=0.6", facecolor="#f7f7f7", edgecolor="#cccccc"))

    fig.suptitle("Thí nghiệm 2B: Ảnh hưởng của CUTOFF theo 5 phân phối dữ liệu (N = 500,000)",
                 fontsize=13, fontweight="bold")
    fig.tight_layout()
    fig.savefig(FIG_DIR / "fig4_distributions_heatmap.png")
    plt.close(fig)

    # 3. Scale invariance across N = 100k, 500k, 1M (fig5)
    fig, axes = plt.subplots(1, 2, figsize=(12.5, 5.0))
    for idx, algo in enumerate(["Merge", "MergeOptimized"]):
        ax = axes[idx]
        for n_val, col, mk in [(100_000, "#1f77b4", "s"), (500_000, "#ff7f0e", "o"), (1_000_000, "#2ca02c", "^")]:
            sub = agg[(agg["algorithm"] == algo) & (agg["distribution"] == "random") & (agg["n"] == n_val)].copy()
            sub = sub.sort_values("cutoff")
            n_lgn = n_val * math.log2(n_val)
            sub["ns_per_nlgn"] = (sub["mean"] * 1e6) / n_lgn
            best_r = sub.loc[sub["mean"].idxmin()]
            ax.plot(sub["cutoff"], sub["ns_per_nlgn"], marker=mk, markersize=4, linewidth=1.6,
                    color=col, label=f"N={n_val:,} (best={int(best_r['cutoff'])})")
        ax.axvline(7, color="gray", linestyle=":", label="CUTOFF = 7")
        ax.axvline(10, color="purple", linestyle="-.", label="CUTOFF = 10")
        ax.set_title(f"{algo}: Thời gian chuẩn hóa T(N) / (N lg N)")
        ax.set_xlabel("Ngưỡng CUTOFF")
        ax.set_ylabel("ns / (N lg N)")
        ymin, ymax = ax.get_ylim()
        ax.set_ylim(ymin, ymax + (ymax - ymin) * 0.15)
        ax.legend(fontsize=8.2, loc="upper left")
    fig.suptitle("Thí nghiệm 2C: Tính bất biến theo kích thước mảng (N = 100k, 500k, 1M)",
                 fontsize=13, fontweight="bold")
    fig.tight_layout()
    fig.savefig(FIG_DIR / "fig5_size_invariance.png")
    plt.close(fig)

    md_lines.append("## 2. Thí nghiệm 2: Quét CUTOFF trên N = 500,000 (đa phân phối & đa kích thước)\n")
    md_lines.append("### 2A. Chi tiết N = 500,000 Random (n = 10 trials/cutoff)\n")
    md_lines.append("| CUTOFF | Merge Mean ± CI95 (ms) | Merge Median (ms) | MergeOpt Mean ± CI95 (ms) | MergeOpt Median (ms) |")
    md_lines.append("|---:|---:|---:|---:|---:|")
    for c in sorted(r500["cutoff"].unique()):
        m_row = r500[(r500["algorithm"] == "Merge") & (r500["cutoff"] == c)].iloc[0]
        o_row = r500[(r500["algorithm"] == "MergeOptimized") & (r500["cutoff"] == c)].iloc[0]
        md_lines.append(
            f"| {int(c)} | {m_row['mean']:.2f} ± {m_row['ci95']:.2f} | {m_row['median']:.2f} | "
            f"{o_row['mean']:.2f} ± {o_row['ci95']:.2f} | {o_row['median']:.2f} |"
        )
    md_lines.append("\n### 2B. Tóm tắt theo 5 phân phối dữ liệu (N = 500,000)\n")
    md_lines.append("| Phân phối | Thuật toán | Baseline CUTOFF=1 (ms) | Best CUTOFF | Best Mean (ms) | Cải thiện (%) |")
    md_lines.append("|:---|:---|---:|---:|---:|---:|")
    for row in dist_summary:
        md_lines.append(
            f"| {row['distribution']} | {row['algorithm']} | {row['base_ms']:.2f} | "
            f"{row['best_cutoff']} | {row['best_ms']:.2f} | {row['speedup_pct']:+.2f}% |"
        )
    md_lines.append("")
    return optima_500


def plot_exp3_octave(df3: pd.DataFrame, md_lines: list[str]) -> dict:
    # Aggregate across all 16 octave sizes x 3 rounds = 48 observations per cutoff
    agg = agg_group(df3, ["algorithm", "cutoff"], "ns_per_nlogn")
    optima_oct = {}

    fig, ax = plt.subplots(figsize=(10.2, 5.5))
    for algo, col, mk in [("Merge", "#1f77b4", "o"), ("MergeOptimized", "#2ca02c", "s")]:
        sub = agg[agg["algorithm"] == algo].sort_values("cutoff")
        best_r = sub.loc[sub["mean"].idxmin()]
        best_c = int(best_r["cutoff"])
        base_v = float(sub[sub["cutoff"] == 1]["mean"].iloc[0])
        v7 = float(sub[sub["cutoff"] == 7]["mean"].iloc[0])
        v10 = float(sub[sub["cutoff"] == 10]["mean"].iloc[0])
        optima_oct[algo] = {
            "best_cutoff": best_c,
            "best_mean": float(best_r["mean"]),
            "best_median": float(best_r["median"]),
            "base_mean": base_v,
            "v7": v7,
            "v10": v10,
            "gain_vs_1": (base_v - float(best_r["mean"])) / base_v * 100.0,
        }

        ax.errorbar(
            sub["cutoff"], sub["mean"], yerr=sub["ci95"],
            color=col, marker=mk, markersize=4.5, linewidth=2.0, capsize=2.8,
            label=f"{algo} (Tối ưu = {best_c}, giảm {optima_oct[algo]['gain_vs_1']:.2f}%)"
        )
        ax.scatter([best_c], [best_r["mean"]], color="red", s=90, zorder=6)
        ax.annotate(
            f"Opt={best_c}\n({best_r['mean']:.2f})",
            xy=(best_c, best_r["mean"]),
            xytext=(best_c + 1.5, best_r["mean"] - (1.8 if algo == "Merge" else -1.5)),
            arrowprops=dict(arrowstyle="->", color="red", lw=1.2),
            fontsize=9, fontweight="bold", color="darkred"
        )

    ax.axvline(7, color="gray", linestyle=":", linewidth=1.4, label="CUTOFF = 7 (giáo trình cũ)")
    ax.axvline(10, color="purple", linestyle="-.", linewidth=1.4, label="CUTOFF = 10 (giáo trình mới)")
    ax.set_title("Thí nghiệm 3: Đường cong trơn trung bình qua 16 kích thước N ∈ [100k, 200k) (n = 48 mẫu/điểm)")
    ax.set_xlabel("Ngưỡng CUTOFF")
    ax.set_ylabel("Thời gian chuẩn hóa T(N) / (N lg N) (ns) ± 95% CI")
    ax.set_xlim(0, 52)
    ax.set_ylim(13.0, 22.8)
    ax.legend(loc="upper right", fontsize=8.8)
    fig.tight_layout()
    fig.savefig(FIG_DIR / "fig3_cutoff_octave_smooth.png")
    plt.close(fig)

    md_lines.append("## 3. Thí nghiệm 3: Quét CUTOFF trung bình qua 16 kích thước phủ 1 octave [100k, 200k)\n")
    for algo, info in optima_oct.items():
        md_lines.append(
            f"- **{algo}:** Tối ưu tại `CUTOFF = {info['best_cutoff']}` "
            f"({info['best_mean']:.3f} ns/(N lg N), cải thiện **{info['gain_vs_1']:.2f}%** so với CUTOFF=1; "
            f"so với CUTOFF=7 là {info['v7']:.3f} và CUTOFF=10 là {info['v10']:.3f})."
        )
    md_lines.append("")
    md_lines.append("| CUTOFF | Merge ns/(N lg N) ± CI95 | Cải thiện vs 1 | MergeOpt ns/(N lg N) ± CI95 | Cải thiện vs 1 |")
    md_lines.append("|---:|---:|---:|---:|---:|")
    m_base = optima_oct["Merge"]["base_mean"]
    o_base = optima_oct["MergeOptimized"]["base_mean"]
    for c in sorted(agg["cutoff"].unique()):
        mr = agg[(agg["algorithm"] == "Merge") & (agg["cutoff"] == c)].iloc[0]
        orr = agg[(agg["algorithm"] == "MergeOptimized") & (agg["cutoff"] == c)].iloc[0]
        mg = (m_base - mr["mean"]) / m_base * 100.0
        og = (o_base - orr["mean"]) / o_base * 100.0
        md_lines.append(
            f"| {int(c)} | {mr['mean']:.3f} ± {mr['ci95']:.3f} | {mg:+.2f}% | "
            f"{orr['mean']:.3f} ± {orr['ci95']:.3f} | {og:+.2f}% |"
        )
    md_lines.append("")
    return optima_oct


def plot_exp4_doubling(df4: pd.DataFrame, md_lines: list[str]) -> None:
    agg = agg_group(df4, ["algorithm", "cutoff", "n"], "time_ms")
    avail_cutoffs = sorted(agg["cutoff"].unique())
    focus = [c for c in [1, 7, 10, 14, 16, 20] if c in avail_cutoffs]

    fig, axes = plt.subplots(2, 2, figsize=(13.5, 9.0))
    md_lines.append("## 4. Thí nghiệm 4: Doubling Test (1.3 Algorithm Analysis, N = 10,000 .. 1,280,000)\n")

    for idx, algo in enumerate(["Merge", "MergeOptimized"]):
        ax_log = axes[0, idx]
        ax_rat = axes[1, idx]
        md_lines.append(f"### 4.{idx+1} {algo}\n")
        md_lines.append("| CUTOFF | N | Mean ± CI95 (ms) | Tỷ lệ T(N)/T(N/2) | Số mũ b = lg(ratio) |")
        md_lines.append("|---:|---:|---:|---:|---:|")

        n_list = sorted(agg["n"].unique())
        for c in focus:
            sub = agg[(agg["algorithm"] == algo) & (agg["cutoff"] == c)].sort_values("n")
            ax_log.loglog(sub["n"], sub["mean"], marker="o", linewidth=1.7, label=f"CUTOFF = {c}", base=2)

            ratios_x, ratios_y = [], []
            prev_t = None
            for _, row in sub.iterrows():
                n_val = int(row["n"])
                t_val = float(row["mean"])
                if prev_t is not None and prev_t > 0:
                    ratio = t_val / prev_t
                    b_exp = math.log2(ratio)
                    r_str = f"{ratio:.3f}"
                    b_str = f"{b_exp:.3f}"
                    ratios_x.append(n_val)
                    ratios_y.append(ratio)
                else:
                    r_str = "-"
                    b_str = "-"
                md_lines.append(f"| {c} | {n_val:,} | {t_val:.3f} ± {row['ci95']:.3f} | {r_str} | {b_str} |")
                prev_t = t_val

            ax_rat.plot(ratios_x, ratios_y, marker="s", linewidth=1.5, label=f"CUTOFF = {c}")

        # Theoretical N lg N doubling ratio: 2 * lg(N) / lg(N/2)
        theo_x = [n for n in n_list[1:]]
        theo_y = [2.0 * math.log2(n) / math.log2(n / 2.0) for n in theo_x]
        ax_rat.plot(theo_x, theo_y, color="black", linestyle="--", linewidth=1.8,
                    label="Lý thuyết N lg N: 2·lg(N)/lg(N/2)")
        ax_rat.set_xscale("log", base=2)
        ax_rat.set_title(f"{algo}: Tỷ số Doubling Test T(N)/T(N/2)")
        ax_rat.set_xlabel("Kích thước mảng N (log₂)")
        ax_rat.set_ylabel("Tỷ số T(N) / T(N/2)")
        ax_rat.legend(fontsize=8.0)

        sub10 = agg[(agg["algorithm"] == algo) & (agg["cutoff"] == 10)].sort_values("n")
        if len(sub10) >= 2:
            x_log = np.log2(sub10["n"].to_numpy(dtype=float))
            y_log = np.log2(sub10["mean"].to_numpy(dtype=float))
            slope, _ = np.polyfit(x_log, y_log, 1)
            ax_log.set_title(f"{algo}: Đồ thị Log-Log (độ dốc b ≈ {slope:.3f})")
        else:
            ax_log.set_title(f"{algo}: Đồ thị Log-Log")

        ax_log.set_xlabel("Kích thước mảng N (thang log cơ số 2)")
        ax_log.set_ylabel("Thời gian T(N) (ms, thang log cơ số 2)")
        ax_log.legend(fontsize=8.2)
        md_lines.append("")

    fig.suptitle("Thí nghiệm 4: Kiểm chứng quy luật tăng trưởng O(N lg N) bằng Doubling Test",
                 fontsize=13, fontweight="bold")
    fig.tight_layout()
    fig.savefig(FIG_DIR / "fig6_doubling_loglog.png")
    plt.close(fig)


def plot_exp5_operations(df5: pd.DataFrame, df2: pd.DataFrame, md_lines: list[str]) -> None:
    r500_ops = df5[(df5["distribution"] == "random") & (df5["n"] == 500_000)].copy()
    r500_time = agg_group(
        df2[(df2["distribution"] == "random") & (df2["n"] == 500_000)],
        ["algorithm", "cutoff"],
        "time_ms"
    )

    fig, axes = plt.subplots(1, 3, figsize=(15, 4.8))
    for algo, col in [("Merge", "#1f77b4"), ("MergeOptimized", "#2ca02c")]:
        ops = r500_ops[r500_ops["algorithm"] == algo].sort_values("cutoff")
        tms = r500_time[r500_time["algorithm"] == algo].sort_values("cutoff")

        axes[0].plot(ops["cutoff"], ops["compares"] / 1e6, marker="o", ms=3.5, lw=1.8, color=col, label=algo)
        axes[1].plot(ops["cutoff"], ops["array_accesses"] / 1e6, marker="s", ms=3.5, lw=1.8, color=col, label=algo)
        axes[2].plot(tms["cutoff"], tms["mean"], marker="^", ms=4, lw=1.8, color=col, label=algo)

    axes[0].set_title("Số phép so sánh (Compares)")
    axes[0].set_ylabel("Triệu phép so sánh (×10⁶)")
    axes[1].set_title("Số lần truy cập mảng (Array Accesses)")
    axes[1].set_ylabel("Triệu lượt truy cập (×10⁶)")
    axes[2].set_title("Thời gian chạy thực tế (Wall-clock)")
    axes[2].set_ylabel("Thời gian (ms)")

    for ax in axes:
        ax.set_xlabel("Ngưỡng CUTOFF")
        ax.axvline(7, color="gray", linestyle=":", alpha=0.8)
        ax.axvline(10, color="purple", linestyle="-.", alpha=0.8)
        ax.legend(fontsize=8.5)

    fig.suptitle("Thí nghiệm 5: Đánh đổi giữa số phép so sánh, truy cập mảng và thời gian chạy (N = 500,000 Random)",
                 fontsize=12.5, fontweight="bold")
    fig.tight_layout()
    fig.savefig(FIG_DIR / "fig7_ops_vs_time.png")
    plt.close(fig)

    md_lines.append("## 5. Thí nghiệm 5: Số phép so sánh & truy cập mảng (N = 500,000 Random)\n")
    md_lines.append("| CUTOFF | Merge Compares | Merge Array Accesses | MergeOpt Compares | MergeOpt Array Accesses | Insertion Calls |")
    md_lines.append("|---:|---:|---:|---:|---:|---:|")
    for c in [1, 2, 4, 6, 7, 8, 10, 12, 14, 16, 20, 24, 32, 50, 64]:
        mr = r500_ops[(r500_ops["algorithm"] == "Merge") & (r500_ops["cutoff"] == c)]
        orr = r500_ops[(r500_ops["algorithm"] == "MergeOptimized") & (r500_ops["cutoff"] == c)]
        if not mr.empty and not orr.empty:
            m = mr.iloc[0]
            o = orr.iloc[0]
            md_lines.append(
                f"| {c} | {int(m['compares']):,} | {int(m['array_accesses']):,} | "
                f"{int(o['compares']):,} | {int(o['array_accesses']):,} | {int(m['insertion_calls']):,} |"
            )
    md_lines.append("")


def main() -> None:
    FIG_DIR.mkdir(parents=True, exist_ok=True)
    setup_plot_style()

    md_lines = ["# Tóm tắt kết quả thống kê thí nghiệm CUTOFF Mergesort\n"]

    df1 = pd.read_csv(RESULTS_DIR / "raw_crossover.csv")
    plot_exp1_crossover(df1, md_lines)

    df2 = pd.read_csv(RESULTS_DIR / "raw_cutoff_single_n.csv")
    plot_exp2_single_n(df2, md_lines)

    df3_path = RESULTS_DIR / "raw_cutoff_octave.csv"
    if df3_path.exists():
        df3 = pd.read_csv(df3_path)
        plot_exp3_octave(df3, md_lines)

    df4_path = RESULTS_DIR / "raw_doubling_test.csv"
    if df4_path.exists():
        df4 = pd.read_csv(df4_path)
        plot_exp4_doubling(df4, md_lines)

    df5_path = RESULTS_DIR / "raw_operation_counts.csv"
    if df5_path.exists():
        df5 = pd.read_csv(df5_path)
        plot_exp5_operations(df5, df2, md_lines)

    summary_path = RESULTS_DIR / "summary_stats.md"
    with open(summary_path, "w", encoding="utf-8") as f:
        f.write("\n".join(md_lines))
    print(f"Saved summary statistics to {summary_path}")
    print(f"Saved figures to {FIG_DIR}")


if __name__ == "__main__":
    main()
