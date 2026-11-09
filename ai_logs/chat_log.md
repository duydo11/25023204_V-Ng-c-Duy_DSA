# Nhật ký trao đổi và sử dụng AI (AI Chat Log)

**Dự án:** Thí nghiệm xác định ngưỡng `CUTOFF` tối ưu cho Mergesort kết hợp Insertion Sort  
**Nguyên tắc tuân thủ:** Mã nguồn cài đặt thuật toán (`Insertion.java`, `Merge.java`, `MergeOptimized.java`) được dựng trực tiếp từ nội dung slide bài giảng *2.2 Mergesort* (Sedgewick & Wayne), không sử dụng cài đặt từ thư viện hay nguồn khác. AI được sử dụng để thảo luận phương pháp thí nghiệm theo *1.3 Algorithm Analysis*, viết script sinh dữ liệu, harness đo đạc, phân tích thống kê, trực quan hóa và hỗ trợ tổng hợp báo cáo.

---

## Phiên 1: Thiết kế thuật toán và phương pháp thí nghiệm
- **Mục tiêu thảo luận:**
  - Chốt ngôn ngữ lập trình và phạm vi cài đặt bám sát bài giảng *2.2 Mergesort*.
  - Xác định lý do giáo trình cũ dùng `CUTOFF = 7` (Java 6 `Arrays.sort`, Slide 22) còn giáo trình mới nêu `CUTOFF ≈ 10` (Slide 18).
- **Quyết định kỹ thuật đạt được:**
  1. Chọn **Java (OpenJDK 21.0.11)** để khớp trực tiếp kiểu `Comparable[]`, phương thức `less()`, `exch()`, `merge()`, `sort()` trên Slide 7, 8, 18, 20–22, 52.
  2. Xây dựng cả hai biến thể từ bài giảng:
     - `Merge.java`: Bản Top-Down Mergesort cơ bản + `CUTOFF` (Slides 7, 8, 18).
     - `MergeOptimized.java`: Bản kết hợp đủ 3 cải tiến trên Slide 22 (`CUTOFF` + kiểm tra `!less(src[mid+1], src[mid])` + tráo vai trò `src[]`/`dst[]` loại bỏ copy `aux[]`).
  3. Phát hiện phân tích quan trọng trước khi chạy benchmark: Trên một kích thước mảng $N$ cố định, cây đệ quy chia đôi chỉ tạo ra các mảng con ở độ sâu $d$ có kích thước $\lfloor N/2^d \rfloor$ hoặc $\lceil N/2^d \rceil$, dẫn đến **hiệu ứng bậc thang phẳng** giữa các giá trị `CUTOFF` lân cận (ví dụ `8, 10, 12, 14` ở $N=500{,}000$). Do đó thiết kế thêm **Thí nghiệm 3 (Octave Sweep)** qua 16 kích thước trong $[100{,}000, 200{,}000)$ để thu được đường cong chữ U mượt.

## Phiên 2: Kiểm chứng thuật toán & Xây dựng bộ dữ liệu
- **Thực hiện:**
  - Viết `SortTest.java` kiểm chứng **1.441 ca test** cho tính đúng đắn và **tính ổn định (stability)** với kiểu `Item(key, origIndex)` qua nhiều ngưỡng `CUTOFF` (`1..64`) và các kích thước biên (`0, 1, 2, ...`). Tất cả 1.441 ca đạt `ALL TESTS PASSED`.
  - Viết `scripts/generate_datasets.py` tạo 15 tập dữ liệu cố định seed trong `data/` kèm `data/manifest.json` (gồm 5 phân phối ở $N=500{,}000$ và dải Doubling Test từ $10{,}000$ đến $1{,}280{,}000$).
  - Tách riêng `OperationAnalyzer.java` khỏi các lớp benchmark thời gian để đếm chính xác `compares` và `arrayAccesses` mà không gây sai số đo thời gian.

## Phiên 3: Chạy thử nghiệm (Pilot), phát hiện vấn đề & hiệu chỉnh đo đạc
- **Vấn đề phát hiện sau lượt chạy đầu (`-Xms2g -Xmx2g`):**
  1. Trong `Exp 1` (`n = 2..40`), `Insertion.sort` vẫn nhanh hơn `Merge.sort` tại `n = 40` vì `Merge.sort` phải cấp phát `new Comparable[n]` trên heap mỗi lần gọi độc lập -> **Mở rộng `Exp 1` lên `n = 2..100`**, tìm được giao điểm đứng riêng tại `n = 70..80` (`MergeOptimized`) và `n ≈ 100` (`Merge`).
  2. Trong `Exp 2` (`N = 500,000` và `sorted_500000`), xuất hiện các đỉnh trễ GC đột biến (+100–200 ms) khi dùng `-Xms2g -Xmx2g` G1GC mặc định (do kích thước G1 region mặc định là 1 MB khiến mảng tham chiếu $\ge 128{,}000$ phần tử bị coi là *Humongous Object*).
- **Giải pháp hiệu chỉnh & chạy lại:**
  - Cấu hình `-Xms4g -Xmx4g -XX:G1HeapRegionSize=16m` (nâng ngưỡng humongous lên 8 MB), bổ sung warmup cho mảng `500k`, gom batch (`batch=4`) cho `sorted_500000` để tránh nhiễu timer dưới 5 ms, và chạy `System.gc()` ngoài vùng đo thời gian giữa các vòng.
  - Kết quả thu được sạch và ổn định rõ rệt (đặc biệt `sorted_500000` trên `MergeOptimized` giảm mượt từ `22.86 ms` xuống `5.07 ms`).

## Phiên 4: Phân tích thống kê, vẽ biểu đồ & hoàn thiện báo cáo
- **Thực hiện:**
  - Viết `scripts/analyze_and_plot.py` tính Mean, Median, Std, 95% CI (Student-$t$), Doubling Ratio $T(N)/T(N/2)$, hồi quy log-log và xuất 7 biểu đồ 300 DPI trong `results/figures/` cùng bảng tổng hợp `results/summary_stats.md`.
  - Cập nhật hằng số `DEFAULT_CUTOFF` tối ưu đo được vào `Merge.java` (`16`) và `MergeOptimized.java` (`10`), biên dịch và chạy kiểm tra lại `SortTest` (1.441/1.441 pass).
  - Hoàn thiện `report/REPORT.md` và `README.md`.
