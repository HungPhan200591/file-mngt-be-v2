# Claims: Dropbox Magic Pocket

- **Separation of Blob and Metadata**: Tách rời tuyệt đối giữa lưu trữ file thô (dùng Block Storage) và lưu trữ metadata quan hệ (dùng Sharded MySQL / PostgreSQL) giúp hệ thống đạt độ co giãn hàng Exabytes mà không làm nghẽn database.
- **Immutable Block Primitive (4MB)**: Coi khối dữ liệu là bất biến (Immutable) loại bỏ hoàn toàn nhu cầu về Distributed Locking và Multi-Version Concurrency Control (MVCC) ở tầng đĩa.
- **Content-Addressable Storage (CAS)**: Định danh khối dữ liệu bằng mã băm SHA-256 giúp phát hiện và loại bỏ trùng lặp dữ liệu (Deduplication) tự động và tức thì.
- **Hierarchical Aggregation (Block $\to$ Bucket $\to$ Volume)**: Gom các khối 4MB thành các thùng 1GB (Buckets) giúp giảm thiểu số lượng bản ghi chỉ mục siêu dữ liệu mà hệ thống phân tán phải theo dõi.
- **Erasure Coding over 3x Replication**: Sử dụng mã hóa xóa (Erasure Coding) cho dữ liệu nguội (cold data) giúp giảm chi phí lưu trữ phần cứng tới 50% trong khi vẫn giữ nguyên độ bền vững dữ liệu trước việc mất nhiều ổ cứng đồng thời.
- **Off-Data-Path Coordination**: Bộ điều phối Master chỉ hoạt động ngầm (sửa lỗi, dọn rác, gom bucket) và nằm hoàn toàn ngoài luồng đọc/ghi trực tiếp (hot data path), giúp tránh nghẽn cổ chai tập trung.
