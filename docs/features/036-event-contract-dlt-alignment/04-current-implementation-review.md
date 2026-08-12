# FT-036 — Review triển khai hiện tại

Scan publish `media.file.discovered.v2` sau approval. `MediaFileDiscoveredConsumer` đọc `eventType` trước deserialize và reject type khác vào error handler. `DefaultErrorHandler` retry 2 lần rồi `DeadLetterPublishingRecoverer` chuyển sang `<topic>.DLT`; `CatalogDeadLetterObserver` theo dõi `media.file.discovered.v2.DLT` và `CatalogDeadLetterService` ghi durable operator record, dedupe theo `(originalTopic, partition, offset)`.

Delivery vẫn at-least-once: crash sau Kafka ack trước khi mark DB có thể tạo duplicate, consumer dedupe theo `eventId` là safety net. Chưa có runtime proof cho serialization, malformed/unknown event, retry/DLT, duplicate/out-of-order và observer restart.
