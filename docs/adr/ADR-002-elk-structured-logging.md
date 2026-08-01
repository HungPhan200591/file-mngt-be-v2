# ADR-002: ELK cho structured logging

Status: ACCEPTED
Date: 2026-08-01

## Context

Microservice và Kafka làm việc theo nhiều process; console log rời rạc không đủ để trace lỗi hoặc học quan sát hệ thống. Dự án cần ELK nhưng không được để logging làm nặng core flow.

## Decision

- Dùng Elasticsearch, Logstash và Kibana cho structured application log.
- Chạy ELK trong Docker Compose profile `observability`, sau P0; pin cùng version/digest và không dùng `latest`.
- Service log JSON có `correlationId`, service name, environment, level và exception fields.
- Logstash ghi vào Elasticsearch logs data stream; Kibana dùng để search/dashboard log.
- Micrometer/Prometheus/Grafana vẫn phụ trách metrics; OpenTelemetry phụ trách distributed trace.

## Alternatives

- Chỉ console log: nhẹ nhưng khó tìm lỗi xuyên nhiều service.
- Elasticsearch trực tiếp từ service: coupling cao và xử lý retry/backpressure trong từng app.
- Dùng Elasticsearch vừa làm log store vừa làm canonical database: ngoài phạm vi và làm sai ownership của Query/Catalog.

## Consequences

- Observability cần thêm tài nguyên local, nên tách profile và không chặn P0.
- Shipping log phải bất đồng bộ/best-effort; Logstash/Elasticsearch lỗi không làm business request lỗi theo.
- Cần pin version tương thích trước implementation và tránh log secret/PII.
