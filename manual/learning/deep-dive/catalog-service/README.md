# 📦 Catalog Service Deep-Dive & Architecture Hub

Chào mừng bạn đến với chuyên mục nghiên cứu chuyên sâu (Deep-Dive) về **Catalog Service** trong hệ thống Backend V2 (`file_mngt_microservice`).

---

## 📚 Danh Mục Tài Liệu Chuyên Sâu:

1. **[🧭 Toàn Cảnh Dòng Chảy & Tiến Trình Tối Ưu Hóa Catalog Service 1.000.000 Records (FT-054 $\to$ FT-057 $\to$ FT-058)](./01-catalog-coalescing-and-reconciliation-deep-dive.md)**
   * Bản chất bài toán gom nhóm dữ liệu (Coalescing / Map-Reduce).
   * Mổ xẻ nguyên nhân thất bại của 4 thế hệ trước (V19, V20, V21, V22).
   * 4 Trụ cột kiến trúc đột phá của FT-057 (Append Ingest, Bulk Seal, Coarse Units, Sliding Relay).
   * Capacity model đạt $\ge 30.000 - 40.000\text{ records/s}$.
   * Cẩm nang câu hỏi phỏng vấn Senior/Architect.

2. **[🎲 Bí Mật Phân Bổ Đồng Đều Dữ Liệu Qua 4.096 Routing Buckets & Luật Số Lớn](./02-routing-buckets-and-hash-distribution-deep-dive.md)**
   * Tại sao hàm băm không biết số lượng Asset mà vẫn chia đều được?
   * Bằng chứng toán học: Luật số lớn (Law of Large Numbers) & Phân phối chuẩn đều.
   * Tại sao cần tầng đệm 4.096 Routing Buckets thay vì chia thẳng `mod 16`?
   * 2 Lớp phòng vệ khi gặp Subject "Siêu quái vật" (Dynamic Worker Stealing & Payload Cap).

---

## 🗺️ Vị Trí Trong Toàn Bộ Hệ Thống

```text
[Scan Service] ──(Kafka: 1M Files)──► [Catalog Service] ──(Kafka: 100k Snapshots)──► [Query Service]
                                      ▲
                                      └─── CHUYÊN MỤC NÀY
```
