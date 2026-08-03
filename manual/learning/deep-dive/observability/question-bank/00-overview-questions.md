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

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Metrics phát hiện sự cố nhanh (**TẮC Ở ĐÂU**), Logs tìm nguyên nhân chi tiết (**TẠI SAO LỖI**), Traces nối luồng đi xuyên hệ thống (**ĐI QUA ĐÂU**). Không thể dùng 1 loại duy nhất vì đánh đổi giữa Chi phí lưu trữ và Độ chi tiết dữ liệu."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Metrics dùng làm gì?** ➔ 💡 **Phát hiện sự cố & Alerting** (Dữ liệu số time-series nhẹ, nén cao: Latency p95, Error Rate, Outbox Backlog).
- ❓ **Structured Logs dùng làm gì?** ➔ 💡 **Chẩn đoán nguyên nhân gốc (Root-cause Analysis)** (Văn bản JSON chi tiết: Exception StackTrace, Payload).
- ❓ **Traces dùng làm gì?** ➔ 💡 **Nối vết dòng chảy dữ liệu xuyên service** (Graph nhân quả: Correlation ID, OpenTelemetry).
- ❓ **Tại sao không dùng Log thay Metrics?** ➔ 💡 **Log quá nặng, chi phí lưu trữ bùng nổ, tính toán aggregation cực chậm**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Metrics (Nhẹ / Alert) — Logs (Chi tiết / Root-cause) — Traces (Nối luồng / CorrelationId)**.

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

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Prometheus chọn Pull (Scrape) để **tự chủ kiểm soát tải (Load Control)** chống sập Server khi Spiking Traffic, **phát hiện service sập tức thì (`up == 0`)** và **giữ Microservice code siêu đơn giản** chỉ bằng 1 static HTTP endpoint."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Lợi ích lớn nhất của Pull Model?** ➔ 💡 **Kiểm soát tải (Load Shedding)** (Prometheus tự quyết định tốc độ pull 5s/lần, không bị app dồn nén khi nổ traffic).
- ❓ **Làm sao phát hiện service sập ngay?** ➔ 💡 **Chỉ số `up == 0`** (Scrape thất bại ở chu kỳ tiếp theo ➔ Báo động sập tức thì).
- ❓ **Microservice cần làm gì đối với Pull Model?** ➔ 💡 **Chỉ mở HTTP Endpoint `/actuator/prometheus`** (Không cần Client SDK hay connection retry).
- ❓ **Ngoại lệ nào của Prometheus cần dùng Push?** ➔ 💡 **Short-lived Batch Jobs / Cronjobs** (Dùng qua `Pushgateway`).
- 🔑 **Keyword cốt lõi cần nhớ**: **Pull Model ➔ Kiểm soát tải (Load Control) + Đơn giản hóa App + Phát hiện Target Down (`up == 0`)**.

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
**Question:** Làm thế nào để thiết kế Observability theo hướng Opt-in và giảm coupling để hạn chế Cascading Failure vào API nghiệp vụ?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Tư duy thiết kế Resilience, Decoupled Architecture và phòng ngừa Cascading Failures trong Microservices.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Dùng **file shipping** để tách app khỏi network collector, **Prometheus pull** để collector chủ động scrape và **Compose profile opt-in** để stack phụ không chặn core runtime. Thiết kế này giảm coupling nhưng không bảo đảm zero impact: synchronous appender, disk full, backlog và scrape overhead vẫn phải được giới hạn, đo và cảnh báo."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **File shipping giảm coupling gì?** ➔ 💡 App không gửi từng log event qua network tới collector; Logstash tail file độc lập.
- ❓ **Elasticsearch/Logstash sập còn rủi ro nào cho app?** ➔ 💡 Backlog làm tăng dung lượng file; disk chậm/full hoặc appender đồng bộ vẫn có thể tăng latency hay làm mất log.
- ❓ **Metrics thu thập kiểu gì để không tốn CPU?** ➔ 💡 **Atomic Counters trong RAM + Pull bất đồng bộ qua Prometheus Scrape**.
- ❓ **Làm sao để chạy app nhẹ khi thiếu RAM local?** ➔ 💡 **Opt-in Compose Profiles** (`docker compose --profile observability up -d`).
- 🔑 **Keyword cốt lõi cần nhớ**: **File Shipping + Prometheus Pull + Opt-in Profile = Reduced Coupling, not Zero Risk**.

**Answer outline:**
1. **Ghi Log Độc lập qua File (Decoupled File Shipping)**:
   - Application ghi log ra file local (`/logs/*.json`); project hiện không cấu hình `AsyncAppender`, vì vậy không được giả định caller hoàn toàn non-blocking.
   - Logstash / Filebeat chạy ở container riêng biệt, đọc file ngầm bất đồng bộ để ship về Elasticsearch.
   - Nếu Elasticsearch/Logstash sập hoặc mất mạng, file backlog có thể tích lũy; API không phụ thuộc trực tiếp collector nhưng vẫn phụ thuộc filesystem và capacity local.
2. **Metrics Pull Bất đồng bộ (Opt-in Actuator)**:
   - Micrometer lưu các counter/gauge trong bộ nhớ RAM (Atomic Long/Double) của JVM với overhead tiệm cận 0ms.
   - Prometheus tự scrape bất đồng bộ qua endpoint riêng biệt `/actuator/prometheus`.
3. **Đóng gói Opt-in Compose Profiles**:
   - Trong `compose.yaml`, các container Observability (Elasticsearch, Logstash, Kibana, Prometheus, Grafana) được gắn profile `observability`.
   - Lệnh khởi động core backend `docker compose up -d` có thể chạy hoàn toàn độc lập mà không bắt buộc bật stack Observability nếu môi trường thiếu RAM.<br>
**Required trade-offs:** Ghi log ra đĩa local có rủi ro tràn ổ đĩa nếu không cấu hình Log Rotation (`max-size`, `max-history`).<br>
**Follow-up ladder:** Nếu đĩa local bị đầy (100% Disk Full), Spring Boot app sẽ ứng xử thế nào khi ghi log?<br>
**Red flags:** Cấu hình Application gửi log trực tiếp qua HTTP/Socket Appender đồng bộ tới Logstash mà không có circuit breaker.
