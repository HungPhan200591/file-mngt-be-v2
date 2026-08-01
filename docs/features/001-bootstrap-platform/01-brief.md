# 001 Bootstrap platform

Owner: platform (cross-service)

## Vấn đề

Backend V2 mới có kiến trúc và context nhưng chưa có một nền code/infrastructure thống nhất để Agent tạo service mà không tự phát minh cấu trúc, version hoặc local config.

## Mục tiêu và acceptance criteria

- Có Maven multi-module monorepo cho Gateway, Catalog, Scan, Query, Media Worker, event-contracts và test-support.
- Mỗi service là Spring Boot application độc lập, có health endpoint nhưng chưa có nghiệp vụ.
- Có Docker Compose local cho PostgreSQL, Kafka KRaft và Redis; image được pin, không dùng `latest`.
- Có config local bằng environment variable và `.env.example`, không commit secret.
- Có database/user ownership rõ; chưa có cross-service database access.
- Cấu trúc build, port, service name và local runbook đủ rõ để feature sau làm theo.

## Ngoài phạm vi

- Không tạo `media_subject`, migration nghiệp vụ, REST business API hoặc Kafka business topic.
- Không import dữ liệu V1, không sửa V1 hay frontend.
- Không chạy Docker Compose, build, test hoặc service trong feature tài liệu này.
- Không thêm Kubernetes, observability stack, Schema Registry hoặc authentication.

## Câu hỏi/rủi ro mở

- Docker image version/digest phải được xác minh lại tại lượt implementation, nhưng bắt buộc pin thay vì dùng tag động.
- Worker không cần database ở P0; nếu feature sau cần persistence riêng thì tạo database owner ở feature đó, không tạo trước.
