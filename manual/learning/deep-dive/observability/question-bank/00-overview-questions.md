# ❓ Observability Overview — Interview Question Bank

Bộ câu hỏi phỏng vấn Chuyên sâu (Senior / Solution Architect) về Tổng quan & Triết lý Kiến trúc Observability trong hệ thống Microservices & Event-Driven Architecture.

---

## 📊 Bảng Ma trận Coverage

| Level | Foundation | Senior | Architect | Tổng số câu |
| :--- | :---: | :---: | :---: | :---: |
| **Số lượng** | 1 | 1 | 1 | 3 |

---

## 🎯 Danh sách Câu hỏi Chi tiết & Đáp án Chuẩn

### OBS-OVERVIEW-001 — `FOUNDATION`
**Question:** Bản chất sự khác biệt giữa Metrics, Logs và Traces (3 trụ cột Observability) là gì? Tại sao không thể chỉ dùng 1 loại cho tất cả nhu cầu?<br>
**Target depth:** `D1-D2` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Khả năng phân biệt bản chất dữ liệu telemetry, chi phí lưu trữ/tính toán và mục đích sử dụng từng trụ cột.<br>
**Answer outline:**
- **Metrics (Prometheus)**: Là dữ liệu số học định lượng dạng chuỗi thời gian (*Time-series aggregations*). Dung lượng cực nhẹ, nén cao, dùng để trả lời câu hỏi: *“Hệ thống đang sống hay chết? Latency p95 hiện tại là bao nhiêu? Outbox backlog có đang nghẽn không?”* ➔ Phù hợp để theo dõi sức khỏe tổng quan và phát cảnh báo (Alerting) ngay tức thì.
- **Structured Logs (Spring Boot ECS + ELK)**: Là bản ghi sự kiện chi tiết dạng văn bản cấu trúc (*Context-rich text*). Dung lượng lớn, tốn tài nguyên lưu trữ và tìm kiếm, dùng để trả lời câu hỏi: *“Tại sao request bị lỗi 500? Nguyên nhân nổ NullPointerException ở dòng code nào? Payload request cụ thể là gì?”* ➔ Phù hợp để chẩn đoán nguyên nhân gốc (Root-cause Analysis).
- **Traces (Correlation ID / OpenTelemetry)**: Dữ liệu theo vết thể hiện mối quan hệ nguyên nhân - kết quả (*Causality & Dependency Graph*) xuyên qua các ranh giới service/thread/broker.
- **Không thể dùng 1 loại duy nhất vì**:
  - Dùng Log làm Metrics: Chi phí lưu trữ bùng nổ, tốc độ tính toán aggregation cực chậm, khó tính quantile p99 thực tế.
  - Dùng Metrics làm Log: Không lưu được stack trace, context chi tiết hay payload request.<br>
**Required trade-offs:** Thu thập càng nhiều telemetry data thì chi phí phần cứng (Disk/RAM/CPU) và Network Overhead càng tăng.<br>
**Follow-up ladder:** Trong 3 trụ cột, trụ cột nào tốn chi phí lưu trữ trên Production nhất? Kỹ thuật Sampling giúp ích gì?<br>
**Red flags:** Trả lời "Có Elasticsearch rồi thì không cần Prometheus" hoặc "Có Tracing rồi thì không cần Log".

---

### OBS-OVERVIEW-002 — `SENIOR`
**Question:** Tại sao Prometheus lại chọn mô hình Scrape (Pull) thay vì Push (Service chủ động gửi metric về Prometheus Server)?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Hiểu biết về hệ thống phân tán, kiểm soát tải (Load Shedding), cơ chế phát hiện sự cố (Healthcheck) và kiến trúc Prometheus.<br>
**Answer outline:**
- **Kiểm soát tải (Load Control & Protection)**: Prometheus tự điều phối tần suất scrape (ví dụ 5s/lần). Nếu hệ thống gặp Spiking Traffic (hàng triệu request/giây), Prometheus Server không bị nghẽn hay tràn bộ nhớ (OOM) vì tốc độ pull do Prometheus tự quyết định, không bị dồn ép bởi application.
- **Phát hiện sự cố tức thì (Target Down)**: Với mô hình Pull, nếu một instance microservice bị crash hoặc nổ OOM, Prometheus lập tức phát hiện chỉ số `up == 0` ở lần scrape tiếp theo mà không cần chờ timeout hay heartbeat.
- **Đơn giản hóa Application Code**: Phía Microservice chỉ cần phơi một static HTTP endpoint `/actuator/prometheus`, không cần cài clientSDK phức tạp, không cần quản lý retry mechanism hay connection pool để đẩy tin về Monitoring Server.
- **Ngoại lệ (Pushgateway)**: Với các tác vụ ngắn hạn (Short-lived Batch Jobs / Cronjobs) hoàn thành quá nhanh trước lần scrape, Prometheus mới cần công cụ trung gian `Pushgateway`.<br>
**Required trade-offs:** Mô hình Pull yêu cầu Prometheus phải truy cập được network IP/Port của target endpoint (phải cấu hình Service Discovery hoặc Static Targets).<br>
**Follow-up ladder:** Trong môi trường Kubernetes/Docker Compose, Prometheus dùng cơ chế nào để tự phát hiện target IP mới?<br>
**Red flags:** Cho rằng Push model luôn tốt hơn Pull model trong mọi trường hợp.

---

### OBS-OVERVIEW-003 — `ARCHITECT`
**Question:** Làm thế nào để thiết kế hệ thống Observability hoạt động theo nguyên tắc Non-blocking & Opt-in, đảm bảo sự cố hạ tầng Logging/Metrics không bao giờ làm sập API nghiệp vụ (Cascading Failure)?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Tư duy thiết kế Resilience, Decoupled Architecture và phòng ngừa Cascading Failures trong Microservices.<br>
**Answer outline:**
1. **Ghi Log Độc lập qua File (Decoupled File Shipping)**:
   - Application ghi log ra đĩa local (`/logs/*.json`) bằng OS Buffered Write. Quá trình này diễn ra cực nhanh ở mức kernel.
   - Logstash / Filebeat chạy ở container riêng biệt, đọc file ngầm bất đồng bộ để ship về Elasticsearch.
   - Nếu Elasticsearch/Logstash sập hoặc sập mạng, file log chỉ tạm tích lũy trên đĩa; **API nghiệp vụ vẫn phản hồi HTTP 200 bình thường**.
2. **Metrics Pull Bất đồng bộ (Opt-in Actuator)**:
   - Micrometer lưu các counter/gauge trong bộ nhớ RAM (Atomic Long/Double) của JVM với overhead tiệm cận 0ms.
   - Prometheus tự scrape bất đồng bộ qua endpoint riêng biệt `/actuator/prometheus`.
3. **Đóng gói Opt-in Compose Profiles**:
   - Trong `compose.yaml`, các container Observability (Elasticsearch, Logstash, Kibana, Prometheus, Grafana) được gắn profile `observability`.
   - Lệnh khởi động core backend `docker compose up -d` có thể chạy hoàn toàn độc lập mà không bắt buộc bật stack Observability nếu môi trường thiếu RAM.<br>
**Required trade-offs:** Ghi log ra đĩa local có rủi ro tràn ổ đĩa nếu không cấu hình Log Rotation (`max-size`, `max-history`).<br>
**Follow-up ladder:** Nếu đĩa local bị đầy (100% Disk Full), Spring Boot app sẽ ứng xử thế nào khi ghi log?<br>
**Red flags:** Cấu hình Application gửi log trực tiếp qua HTTP/Socket Appender đồng bộ tới Logstash mà không có circuit breaker.
