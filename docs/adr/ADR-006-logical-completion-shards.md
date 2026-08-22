# ADR-006: Logical subject shards làm completion boundary cho bulk approval

Status: ACCEPTED
Date: 2026-08-22

## Context

FT-058 đã đưa Catalog về control plane có deadline/retry rõ ràng nhưng 1M workload vẫn không hoàn tất trong
120 giây. Transaction reconciliation khoảng 6.250 subjects/unit vượt statement timeout 20 giây; rollback làm
checkpoint của unit không tiến. Global approval watermark cũng buộc Catalog chờ đủ toàn operation trước khi
reconcile, nên ingest, canonical write, relay và Query projection không overlap theo completion boundary nhỏ.

Kafka topic partition count là hạ tầng có thể thay đổi. Scan worker shard theo `proposal.id` hiện tại cũng có thể
tách nhiều file của cùng subject sang các shard khác nhau. Cả hai đều không đủ an toàn để kết luận một subject
đã nhận đủ input trước khi phát final snapshot.

## Decision

- Dùng logical completion shard theo canonical subject key
  `region:subjectType:identityKey`, không dùng Kafka partition vật lý hoặc proposal ID.
- Routing contract version đầu là `SUBJECT_KEY_MD5_12_RANGE_V1`: lấy 12 bit đầu MD5 UTF-8 thành bucket
  `0..4095`, sau đó map contiguous range vào immutable `completionShardCount` của operation.
- Candidate mặc định là 64 logical shards; shard count là durable work-unit cardinality, độc lập với Scan/Catalog
  worker concurrency và không tạo physical business tables.
- Thêm additive event [media.approval.shard.completed.v1](../contracts/events/media.approval.shard.completed.v1.md)
  từ Scan sang Catalog. Marker được ghi transactional với Scan shard completion; Catalog seal bằng manifest +
  unique count equality, không dựa vào cross-topic ordering.
- Catalog reconcile sealed shard bằng durable bounded pages. Page commit canonical state, final snapshot outbox
  và checkpoint atomic; retry chỉ chạy page chưa commit.
- Global `media.approval.watermark.v1` tiếp tục là terminal stage contract. `media.file.discovered.v2` và
  `media.subject.changed.v2` không đổi payload/version.
- Mỗi service tiếp tục chỉ ghi database của mình; không có distributed transaction hoặc cross-database query.

## Alternatives

- **Kafka partition completion marker:** không chọn vì coupling business correctness với partition topology;
  tăng partition có thể remap key và cần drain/cutover đặc biệt.
- **Giữ global completion và tăng timeout/retry/worker:** không chọn vì FT-058 đã chứng minh rollback lặp lại
  cùng transaction shape; tăng budget không tạo durable progress nhỏ hơn.
- **Shard theo proposal ID:** không chọn vì các asset của cùng subject có thể ở shard khác nhau, làm snapshot
  sớm thiếu input hoặc buộc quay lại global barrier.
- **Java whole-operation reducer/Kafka Streams:** không chọn ở feature này vì tăng state/data movement và vẫn
  cần Catalog PostgreSQL + transactional outbox làm source of truth.
- **Physical database/table sharding:** chưa có evidence về storage/host ceiling; tăng complexity migration,
  rebalancing và operation mà không giải quyết trực tiếp failure-domain transaction hiện tại.

## Consequences

- Scan và Catalog phải dùng shared routing implementation/golden vectors; routing version trở thành durable
  contract và không được đổi giữa operation.
- Scan approval query/index đổi từ proposal-ID shard sang subject-key bucket; cần benchmark để tránh regression.
- Thêm O(shard) marker/ledger rows và coordination state, đổi lại shard có thể seal/retry độc lập và Query nhận
  output sớm hơn.
- Completion shard count lớn không đồng nghĩa nhiều DB writers; pressure gate vẫn giới hạn connection/WAL/lock.
- Old global-only operation và new shard-aware operation phải tách bằng processing version. Rollback không được
  chuyển protocol giữa chừng.
- Kiến trúc mới là hypothesis chưa qualified; chỉ FT-059 benchmark mới quyết định có đạt 1M/120s hay không.
