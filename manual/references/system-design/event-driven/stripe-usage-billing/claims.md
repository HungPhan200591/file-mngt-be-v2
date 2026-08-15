# Claims

- Async ingestion cần observability riêng vì lỗi không xuất hiện ngay trên request path.
- Metadata nguồn giúp reconciliation giữa các stream/region.
- Fast path và durable slow path có thể phục vụ hai SLA khác nhau.
- Delayed/out-of-order events cần ledger hoặc cơ chế replay.
