# Summary: Figma Scaled PostgreSQL

## Ứng dụng trực tiếp cho Backend V2
Bài viết của Figma là một bài học đắt giá cho kiến trúc lưu trữ của dự án `file-mngt-be-v2`:
1. **Tránh bẫy NewSQL/NoSQL sớm:**
   * Không cần vội vã vứt bỏ PostgreSQL để chuyển sang NoSQL/Distributed SQL khi chưa thực sự cạn kiệt các giải pháp tối ưu hóa cốt lõi của Postgres (như Vertical Partitioning, Read Replicas, PgBouncer, Keyset Pagination, Binary COPY).
2. **Nguyên tắc Phân vùng Service & Database Ownership:**
   * Rất khớp với kiến trúc Backend V2: Tách biệt database của `scan-service`, `catalog-service`, `query-service`. Không dùng Cross-DB foreign keys hay Cross-DB ACID transactions.
3. **Chiến lược Triển khai 2 Pha (Logical trước Physical):**
   * Khi muốn chia nhỏ bảng lớn (như `scan_proposal`, `scan_file_inventory`), hãy thử nghiệm logic partitioning/routing trên cùng DB trước khi tách cụm vật lý để giảm thiểu rủi ro migration.
