# FT-064 — Catalog Hybrid Streaming Reconciliation

Owner: `catalog-service`

## Vấn đề

Reducer V23/V59 để PostgreSQL vừa tìm winner, gom quan hệ, bầu primary, mutation canonical, dựng JSON và checkpoint
trong một stored procedure lớn. Trên local một PostgreSQL dùng chung CPU/RAM/WAL, tăng số reconciliation worker đã
scale âm; FT-063 event-driven control plane cũng chỉ cải thiện 4,8%. Đường chạy hiện tại đúng nhưng khó debug và
không tận dụng được CPU Java cho phần reduction độc lập theo subject.

## Mục tiêu và acceptance criteria

- Mặc định một reconciliation page tối đa 2.500 subject, tương ứng workload chuẩn 25.000 discovery records.
- Đọc đầy đủ input theo durable subject workset; tuyệt đối không checkpoint theo raw input cursor.
- Java dùng virtual threads để reduce từng subject độc lập và deterministic.
- COPY kết quả reduction vào PostgreSQL temp staging trên cùng transaction connection.
- PostgreSQL dùng set-based mutation cho canonical, relationship, version, outbox và durable checkpoint.
- Giữ exact cardinality, primary election, tombstone, idempotency, fence, retry và final broker ACK.
- Targeted UT/IT phải pass trước khi chạy đúng benchmark combined 25K một lần; chỉ hạ page size khi 2.500 gây
  timeout, OOM hoặc lỗi correctness/liveness có evidence.

## Ngoài phạm vi

- Không đổi REST, Kafka payload, topic, partition key, completion shard protocol hoặc database ownership.
- Không chạy benchmark 250K/1M trong FT-064.
- Không chạy nhiều transaction persistence song song trên cùng operation.
- Không tuyên bố production SLO từ benchmark local.

## Rủi ro

- Virtual threads không làm CPU-bound code tự nhanh hơn nếu fan-out quá nhỏ hoặc allocation/JSON chi phối.
- COPY thêm một lượt encode/decode staging; phải đo net end-to-end, không chỉ Java compute.
- Một subject có asset cardinality bất thường vẫn có thể làm page nặng; snapshot byte ceiling tiếp tục block an toàn.
- Set-based persistence vẫn sinh WAL và index maintenance; FT này giảm SQL reduction complexity chứ không loại giới
  hạn vật lý của PostgreSQL local.
