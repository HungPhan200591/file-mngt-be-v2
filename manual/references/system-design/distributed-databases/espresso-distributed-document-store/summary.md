# Summary: Espresso

Espresso giải quyết bài toán primary online store cho dữ liệu lớn bằng cách kết hợp document model, partitioning, synchronous secondary indexes, local transactions, Helix-managed replication và ordered change capture.

## Mental model

`Client -> Router -> partition master/slave -> MySQL/InnoDB`  
`commit log -> Databus -> search/cache/remote cluster`  
`partition backup -> Snapshot Service -> Avro/HDFS/bootstrap`

## Điểm đáng học

1. Transaction boundary đặt tại single partition; không hứa hẹn distributed transaction giữa các collection khác partition.
2. Secondary index được cập nhật synchronous với base data để query-after-write không thấy stale index.
3. SCN theo partition cung cấp thứ tự ổn định cho replication và CDC, đồng thời chịu được partition movement giữa node.
4. Helix tách cluster state management khỏi request routing và tự động điều phối failover.
5. Snapshot không chỉ phục vụ ETL mà còn tạo bootstrap view cho consumer đã lỡ mất CDC events.

## Liên hệ khi thiết kế hệ thống

Các trade-off này hữu ích khi đánh giá Catalog/Query projection, outbox/CDC, replay, partition ownership, index consistency và recovery watermark trong Backend V2. Không nên suy ra rằng Espresso guarantees global ordering hoặc cross-partition atomicity.
