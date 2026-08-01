# ADR-001: V2 service và data ownership

Status: ACCEPTED
Date: 2026-08-01

## Context

Backend V2 cần học microservice/event-driven nhưng vẫn dễ chạy local. Rủi ro chính là service dùng chung entity/database hoặc frontend phải biết quá nhiều service.

## Decision

- Giữ năm deployable: Gateway, Catalog, Scan, Query, Media Worker.
- Catalog sở hữu write model chuẩn; Scan sở hữu proposal; Query sở hữu projection; Worker sở hữu processing; Gateway chỉ cross-cutting/routing.
- Dùng một PostgreSQL instance local nhưng database/user riêng theo service; không cross-database read/write trong application code.
- Đồng bộ business state xuyên service bằng Kafka event + transactional outbox; Query chấp nhận eventual consistency.
- V1 và V2 chạy song song cho đến khi từng luồng V2 được xác minh.

## Alternatives

- Modular monolith: dễ hơn nhưng giảm bài học về distributed boundary/event delivery.
- Database chung không ownership: nhanh ban đầu nhưng tạo coupling và làm CQRS/event-driven trở thành hình thức.
- Database instance riêng cho từng service ngay từ đầu: đúng microservice hơn nhưng quá nặng cho local learning.

## Consequences

- Cần contract/version/idempotency rõ ràng ngay từ feature đầu.
- Local Compose vẫn gọn, nhưng Agent phải tôn trọng ownership và không viết join tắt.
- Một số màn hình đọc có độ trễ projection; Query API phải biểu diễn được trạng thái đó khi cần.
