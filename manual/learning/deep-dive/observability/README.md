# 📊 Observability Deep-Dive & Architecture Hub

Tài liệu tổng hợp toàn bộ hệ thống chuyên sâu về **Observability** (Metrics, Structured Logging, Distributed Tracing, Dashboards & Alerting) trong dự án **Backend V2** (`file_mngt_microservice`).

---

## 🗂️ Cấu trúc Tài liệu & Ngân hàng Câu hỏi Phỏng vấn

### 1. Tài liệu Lý thuyết & Vận hành
- 📑 **[00. Observability Overview & Architecture](00-overview.md)**: Tổng quan triết lý thiết kế, sơ đồ kiến trúc tổng thể, phân bổ port local và mục lục chi tiết.
- 📈 **[01. Metrics: Prometheus & Grafana](01-metrics-prometheus-grafana.md)**: Deep-dive Micrometer, Spring Actuator, PromQL, cơ chế Scrape, Grafana Dashboard panels và CLI queries.
- 📜 **[02. Structured Logging: Spring Boot ECS & ELK Stack](02-structured-logging-elk.md)**: Đi từ log event và component boundary đến runtime, backpressure, loss/duplicate, retention và quyết định production.
- 🔗 **[03. Correlation ID & Distributed Tracing](03-correlation-id-tracing.md)**: Deep-dive Correlation ID propagation, SLF4J MDC ThreadLocal, Entity Business Key tracing model và OpenTelemetry roadmap.
- 🚨 **[04. Dashboards, Alerting & Incident Response](04-dashboards-alerting-incidents.md)**: Four Golden Signals, Prometheus Alertmanager rules và quy trình khoanh vùng sự cố E2E 4 bước.

---

### ❓ 2. Ngân hàng Câu hỏi Phỏng vấn Chuyên sâu (Interview Question Banks)
Tất cả câu hỏi phỏng vấn được phân cấp độ (`FOUNDATION`, `SENIOR`, `ARCHITECT`), đi kèm ma trận coverage, tiêu chí đánh giá của người phỏng vấn, lời giải chi tiết theo dự án, trade-offs và red flags:

- ❓ **[Overview Question Bank](question-bank/00-overview-questions.md)**: Metrics vs Logs vs Traces, Pull vs Push Model, Non-blocking Architecture.
- ❓ **[Metrics Question Bank](question-bank/01-metrics-questions.md)**: High Cardinality Prevention, PromQL rate/histogram_quantile, Pending Outbox Work.
- ❓ **[Logging Question Bank](question-bank/02-logging-questions.md)**: 5 question chain hỏi-đáp nhanh cùng anchor Foundation/Senior/Architect về ECS, async, file shipping, checkpoint và storage isolation.

### ⚡ 3. Summary ôn nhanh

- **[Structured Logging Core Recall Sheet](summary/02-structured-logging.md)**: North star, keyword spine, core truths, decision rules, câu trả lời 30 giây/2 phút và active recall.
- ❓ **[Tracing Question Bank](question-bank/03-tracing-questions.md)**: MDC ThreadLocal Leak Prevention, Entity Business Key Tracing, OpenTelemetry W3C `traceparent` Header.
- ❓ **[Dashboards & Alerting Question Bank](question-bank/04-dashboards-alerting-questions.md)**: Four Golden Signals, Alert Fatigue & Error Budget Burn Rate, Incident Response Workflow.
