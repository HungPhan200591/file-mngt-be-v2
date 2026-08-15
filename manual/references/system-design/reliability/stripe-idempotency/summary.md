# Summary: Idempotency

Áp dụng trực tiếp cho durable bulk job, targeted recheck, Gateway retry và Catalog batch API. Identity của operation phải tồn tại độc lập với request attempt; duplicate request phải hội tụ về cùng kết quả.
