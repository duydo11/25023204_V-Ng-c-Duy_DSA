# Thí nghiệm tìm giá trị `CUTOFF` tối ưu cho Mergesort kết hợp Insertion Sort

Dự án thực nghiệm thuộc môn Cấu trúc dữ liệu & Giải thuật (DSA), đối chiếu lý thuyết từ bài giảng **1.3 Algorithm Analysis** và **2.2 Mergesort** (*Algorithms, 4th Edition* — Robert Sedgewick & Kevin Wayne) để giải đáp câu hỏi: giữa **`CUTOFF = 7`** (giáo trình cũ) và **`CUTOFF = 10`** (giáo trình mới), giá trị nào phù hợp nhất trên cấu hình máy thực tế và cài đặt Mergesort dựng từ bài giảng.

## Kết quả tóm tắt (trên AMD Ryzen 7 H 260, OpenJDK 21.0.11 LTS)

| Phiên bản cài đặt (theo bài giảng 2.2) | `CUTOFF` tối ưu đo được | Vùng tối ưu | Thời gian tại tối ưu (`ns / N lg N`) | Cải thiện so với `CUTOFF=1` | So với `7` / `10` |
|---|---:|---|---:|---:|---|
| `Merge` (Slides 7, 8, 18) | **16** | `12..16` | `17.939 ± 0.920` | **+11.35%** | `7`: +8.37% \| `10`: +9.00% |
| `MergeOptimized` (Slides 18, 20–22) | **10** | `10..15` | `14.175 ± 0.803` | **+12.19%** | `7`: +7.34% \| `10`: **+12.19%** |

> Đọc phân tích đầy đủ, bảng thống kê và 7 đồ thị tại **[`report/REPORT.md`](report/REPORT.md)**.

## Cấu trúc thư mục

```text
DSA/
├── README.md                          # Tổng quan & hướng dẫn chạy lại thí nghiệm
├── .gitignore
├── src/                               # Mã nguồn Java dựng từ bài giảng 2.2
│   ├── Insertion.java                 # Insertion Sort (Slides 18, 52)
│   ├── Merge.java                     # Top-down Mergesort + CUTOFF (Slides 7, 8, 18)
│   ├── MergeOptimized.java            # Mergesort + CUTOFF + stop-if-sorted + eliminate-copy (Slides 18, 20-22)
│   ├── OperationAnalyzer.java         # Bộ đếm compares & array accesses (tách riêng khỏi phần đo giờ)
│   ├── SortTest.java                  # 1.441 unit tests (correctness + stability)
│   └── BenchmarkRunner.java           # Chương trình chạy 5 thí nghiệm (warmup, randomized order)
├── scripts/                           # Scripts sinh dữ liệu, chạy benchmark & phân tích thống kê
│   ├── generate_datasets.py           # Sinh 15 tập dữ liệu chuẩn (seed cố định) vào data/
│   ├── run_experiments.ps1            # Chạy end-to-end toàn bộ pipeline trên PowerShell
│   └── analyze_and_plot.py            # Tính Mean/Median/Std/95% CI, Doubling Test & vẽ 7 đồ thị
├── data/                              # Bộ dữ liệu thí nghiệm (~55.65 MB, 15 file + manifest.json)
├── results/                           # Dữ liệu đo thô CSV, summary_stats.md & 7 đồ thị PNG
│   └── figures/
├── report/
│   └── REPORT.md                      # Báo cáo thí nghiệm chi tiết (tiếng Việt)
└── ai_logs/
    └── chat_log.md                    # Nhật ký trao đổi & sử dụng AI
```

## Hướng dẫn chạy lại toàn bộ thí nghiệm

Yêu cầu: **JDK 17+** (đã thử trên OpenJDK 21.0.11), **Python 3.10+** với `numpy`, `pandas`, `matplotlib`.

1. **Biên dịch và chạy kiểm thử đúng đắn & tính ổn định:**
   ```powershell
   New-Item -ItemType Directory -Force -Path build | Out-Null
   javac -d build src/*.java
   java -cp build SortTest
   ```
2. **Tái sinh bộ dữ liệu (nếu cần):**
   ```powershell
   python scripts/generate_datasets.py
   ```
3. **Chạy toàn bộ 5 thí nghiệm đo đạc:**
   ```powershell
   java -Xms4g -Xmx4g -XX:G1HeapRegionSize=16m -cp build BenchmarkRunner all
   ```
   *(Hoặc chạy chọn lọc từng thí nghiệm bằng tham số `"1,2,3,4,5"`, ví dụ `BenchmarkRunner "3,5"`).*
4. **Phân tích thống kê & vẽ lại toàn bộ đồ thị:**
   ```powershell
   python scripts/analyze_and_plot.py
   ```
   Hoặc chạy một lệnh duy nhất bằng PowerShell:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/run_experiments.ps1
   ```
