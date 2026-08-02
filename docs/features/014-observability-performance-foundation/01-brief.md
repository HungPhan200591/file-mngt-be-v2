# 014 Observability và performance foundation

Owner: `platform/observability`; `infra/observability` sở hữu runtime stack, `gateway-service` là entry point để đo request.

## Vấn đề

Backend V2 đã có nhiều luồng HTTP, Kafka, PostgreSQL, Redis và Elasticsearch nhưng việc debug vẫn dựa vào log rời rạc và gọi Actuator thủ công. Chưa có một màn hình tổng quan để biết service nào lỗi, request chậm ở đâu, JVM/DB/cache đang ra sao hoặc thay đổi code có làm hiệu năng xấu đi không.

FT013 đã `READY` nhưng được để lại sau. Trước khi thêm processing pipeline, chủ dự án muốn quan sát và tiêu thụ chắc các luồng hiện có.

## Mục tiêu và acceptance criteria

1. Compose profile `observability` khởi động được Prometheus, Grafana, Logstash, Kibana và Elasticsearch đã pin version; profile mặc định vẫn nhẹ và chạy độc lập.
2. Cả năm service expose metrics Prometheus qua Actuator nội bộ; Grafana có dashboard tổng quan `up`, HTTP rate/error/latency, JVM, GC, thread, CPU và Hikari khi service có database.
3. Các custom metric hiện có của Catalog/Query xuất hiện trong Prometheus; metric mới không dùng `subjectId`, `assetId`, `correlationId`, raw path hoặc exception message làm label.
4. Spring Boot ghi structured log ECS bằng tính năng built-in. Kibana tìm được log theo `service.name`, `log.level`, `correlationId` và khoảng thời gian.
5. `X-Correlation-Id` từ Gateway xuất hiện trong MDC/log của downstream HTTP service; không yêu cầu distributed trace qua Kafka trong feature này.
6. Khi Prometheus/ELK/Grafana tắt hoặc lỗi, năm service vẫn khởi động và xử lý nghiệp vụ bình thường; log shipping không nằm trên critical path.
7. Có runbook ngắn để bật/tắt stack, mở dashboard và tìm log theo correlation ID.

## Ngoài phạm vi

- OpenTelemetry distributed tracing xuyên HTTP/Kafka, trace backend và service map.
- Alert manager, paging, SLO/error budget production-grade.
- JFR/JMC automation, JMH, Gatling, continuous profiling.
- k6/load test và performance benchmark chủ động; tách thành feature sau khi chủ dự án đã quen dashboard và log.
- Tối ưu query/index/cache/concurrency cụ thể; FT014 chỉ tạo khả năng quan sát và bằng chứng từ traffic hiện có.
- Centralized authentication, TLS và hardening production cho dashboard/Actuator.
- Thay đổi REST/Kafka business contract, database schema hoặc luồng FT013.

## Câu hỏi/rủi ro mở

- Không còn quyết định kiến trúc chặn triển khai. Rủi ro chính là RAM local khi bật đồng thời ELK và Grafana stack; profile phải opt-in và cấu hình heap nhỏ cho môi trường học tập.
