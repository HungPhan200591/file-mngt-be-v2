# FT-035 — Review triển khai hiện tại

`ScanExecutor` parse changed candidate; `ScanCatalogExistenceFilter` chia tuần tự micro-batch tối đa 500 và gọi `CatalogExistenceClient` ngoài transaction persistence. Client correlate bằng `clientRef` và fail closed khi Catalog timeout/503 hoặc protocol sai. `EXACT_ASSET_EXISTS` bị skip proposal; classification khác được `ScanEvidenceCodec` gắn evidence trước `ScanChunkCommitter`.

Không retry tự động để tránh kéo dài run và tạo retry storm; recovery hiện tại là fail run rồi chạy lại. Catalog call không giữ DB lock, nhưng timeout phải nằm trong ngân sách lease/run. Cần verify exact skip, conflict evidence, response mismatch, Catalog outage/timeout và transaction không mở trong lúc chờ HTTP.
