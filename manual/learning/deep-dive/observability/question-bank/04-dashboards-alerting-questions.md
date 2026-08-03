# ❓ Dashboards, Alerting & Incidents — Interview Question Bank

Bộ câu hỏi phỏng vấn Chuyên sâu (Senior / Lead / SRE) về Thiết kế Dashboard, Prometheus Alertmanager, SLO/SLA Error Budget, Incident Response và E2E Troubleshooting.

---

## 📊 Bảng Ma trận Coverage

| Level | Foundation | Senior | Architect | Tổng số câu |
| :--- | :---: | :---: | :---: | :---: |
| **Số lượng** | 1 | 1 | 1 | 3 |

---

## 🎯 Danh sách Câu hỏi Chi tiết & Đáp án Chuẩn

### OBS-ALERT-001 — `FOUNDATION`
**Question:** Bốn Tín hiệu Vàng (Four Golden Signals) trong SRE Monitoring là gì và được áp dụng như thế nào trong Dashboard Grafana của Backend V2?<br>
**Target depth:** `D1-D2` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Hiểu biết chuẩn SRE của Google về 4 chỉ số cốt lõi khi giám sát bất kỳ hệ thống phân tán nào.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"4 Tín hiệu Vàng gồm: **Latency** (độ trễ p95/p99), **Traffic** (lưu lượng RPS), **Errors** (tỷ lệ lỗi 5xx) và **Saturation** (mức bão hòa RAM/Connection/Outbox Backlog). Đây là 4 chỉ số hiển thị ở vị trí trên cùng của Grafana Dashboard."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **4 Tín hiệu Vàng trong SRE là gì?** ➔ 💡 **Latency (Độ trễ) — Traffic (Tải RPS) — Errors (Tỷ lệ lỗi) — Saturation (Bão hòa đĩa/RAM)**.
- ❓ **Latency hiển thị theo chỉ số nào chuẩn nhất?** ➔ 💡 **Quantile p95 / p99 (không dùng trung bình cộng Average)**.
- ❓ **Saturation đo cái gì trong Backend V2?** ➔ 💡 **JVM Heap RAM, HikariCP Connection Pool và Outbox Backlog Count**.
- 🔑 **Keyword cốt lõi cần nhớ**: **4 Golden Signals ➔ Latency (p95) + Traffic (RPS) + Errors (5xx) + Saturation (Queue/RAM)**.

**Answer outline:**
- **1. Latency (Độ trễ)**: Thời gian xử lý thành công và thất bại của request. Áp dụng: Biểu đồ p95/p99 HTTP latency trên Grafana (`http_server_requests_seconds_bucket`).
- **2. Traffic (Lưu lượng)**: Mức độ tải của hệ thống (RPS - Requests per second). Áp dụng: Biểu đồ `http_server_requests_seconds_count`.
- **3. Errors (Tỷ lệ Lỗi)**: Số lượng hoặc tỷ lệ phần trăm request bị lỗi. Áp dụng: Stat panel đếm số lỗi HTTP 5xx (`status=~"5.."`).
- **4. Saturation (Mức độ Đầy/Bão hòa Tài nguyên)**: Đo lường tài nguyên phần cứng/phần mềm bị chiếm dụng. Áp dụng: Panel đo % RAM JVM Heap (`jvm_memory_used_bytes`), Connection Pool (`hikaricp_connections_active`), và Backlog Outbox (`catalog_outbox_pending`).<br>
**Required trade-offs:** Không dồn quá nhiều chart vào 1 dashboard gây rối mắt (chỉ tập trung 4 golden signals ở vị trí trên cùng).<br>
**Follow-up ladder:** Khác biệt giữa Latency p50, p95 và p99 là gì? Tại sao p99 lại quan trọng với SLA?<br>
**Red flags:** Chỉ xem CPU/RAM mà không theo dõi Latency và Error Rate.

---

### OBS-ALERT-002 — `SENIOR`
**Question:** Làm thế nào để thiết kế các quy tắc cảnh báo (Alerting Rules) có tính thực thi cao (Actionable Alerts) và tránh hiện tượng Kiệt sức Cảnh báo (Alert Fatigue) cho đội ngũ Vận hành/On-Call?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Kinh nghiệm thực tế về Alertmanager, cách tính Error Budget Burn Rate và tư duy On-call Operations.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Cảnh báo phải **Actionable** (chỉ alert khi ảnh hưởng người dùng, đi kèm link Grafana & Runbook hướng dẫn khắc phục) và **Chống Alert Fatigue** bằng mệnh đề duy trì thời gian (`for: 2m`), cảnh báo theo **Error Budget Burn Rate** và phân cấp Severity (Critical page đên, Warning tin Slack)."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Actionable Alert là gì?** ➔ 💡 **Cảnh báo đi kèm Service Name, Severity, Link Dashboard & Link Runbook hướng dẫn sửa**.
- ❓ **Alert Fatigue là gì và làm sao chống?** ➔ 💡 **Mệt mỏi vì báo động giả ➔ Chống bằng `for: 2m` clause và tính Error Budget Burn Rate**.
- ❓ **Phân cấp Severity trong Alertmanager thế nào?** ➔ 💡 **Critical (Page điện thoại khẩn) — Warning (Tin Slack giờ làm việc) — Info (Tạo ticket JIRA)**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Actionable Alert ➔ kèm Runbook + for clause (2m) + Error Budget Burn Rate = No Alert Fatigue**.

**Answer outline:**
- **Nguyên tắc Actionable Alert**: Mọi thông báo gửi về Telegram/Slack BẮT BUỘC phải thỏa mãn:
  1. Chỉ cảnh báo khi có **ảnh hưởng thực tế đến người dùng** (User Impact) hoặc tài nguyên sắp bão hòa ngắn hạn.
  2. Mỗi Alert phải đi kèm: Service name, mức độ nghiêm trọng (Severity), Link Dashboard Grafana và **Link Runbook hướng dẫn khắc phục**.
- **Chống Alert Fatigue (Báo động giả)**:
  1. **Sử dụng cửa sổ thời gian (`for` clause)**: Không alert ngay ở giây đầu tiên nổ spike; yêu cầu vi phạm duy trì trong 1 - 3 phút (ví dụ `for: 2m`).
  2. **Alert theo SLO Burn Rate**: Cảnh báo dựa trên tốc độ tiêu tốn Error Budget thay vì alert theo từng exception lẻ tẻ.
  3. **Phân cấp Severity**: `Critical` (Page điện thoại đêm cho SRE), `Warning` (Gửi tin nhắn Slack giờ hành chính), `Info` (Tạo ticket JIRA tự động).<br>
**Required trade-offs:** Ngưỡng cảnh báo quá nhạy sẽ gây Alert Fatigue; ngưỡng quá lỏng sẽ phát hiện sự cố muộn.<br>
**Follow-up ladder:** Khác biệt giữa SLI, SLO và SLA là gì? Tính Error Budget như thế nào?<br>
**Red flags:** Cấu hình gửi mail/slack thông báo cho MỌI câu log `ERROR` xuất hiện trong hệ thống.

---

### OBS-ALERT-003 — `ARCHITECT`
**Question:** Trình bày quy trình khoanh vùng và xử lý sự cố (Incident Response & Debugging Workflow) thực tế trong Backend V2 khi người dùng báo "Approve Proposal rồi nhưng dữ liệu không hiển thị trên Gallery V2"?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Khả năng kết nối toàn bộ kỹ năng Observability (Grafana, Kibana, Outbox, Event Tracing, Kafka) vào một kịch bản troubleshooting thực tế.<br>

⚡ **Trả lời siêu ngắn (Elevator Pitch)**:
> *"Quy trình 4 bước: **1. Grafana check hạ tầng/outbox backlog** (`:18117`) ➔ **2. Thu thập Business Keys** (`scanRunId`, `identityKey`) ➔ **3. Kibana trace chuỗi log 4 bước** (`scan decision` ➔ `outbox publish` ➔ `catalog consume` ➔ `query projection`) ➔ **4. Khoanh vùng điểm đứt gãy** (DLT/Kafka lag/DB lock)."*

🧠 **Chuỗi Hỏi - Đáp Keyword (Memory Flashcard Chain)**:
- ❓ **Bước đầu tiên khi có sự cố dữ liệu không tới Gallery?** ➔ 💡 **Mở Grafana (:18117) kiểm tra Services UP và Outbox Pending Work**.
- ❓ **Nếu Outbox Pending Work > 0 kéo dài?** ➔ 💡 **Sự cố ở Outbox Publisher Scheduled Task hoặc Kafka Broker/Network**.
- ❓ **Nếu Grafana xanh 100% thì làm gì tiếp theo?** ➔ 💡 **Vào Kibana (:18114) search KQL theo `identityKey` để trace luồng 4 bước log**.
- ❓ **Nếu thiếu log ở bước Catalog Consumer?** ➔ 💡 **Kiểm tra Kafka Consumer Lag hoặc Deserialization Error trong DLT Table**.
- 🔑 **Keyword cốt lõi cần nhớ**: **Grafana (Hạ tầng/Outbox Backlog) ➔ Kibana (KQL Trace Log 4 Bước) ➔ Khoanh vùng Root Cause**.

**Answer outline:**
1. **Bước 1: Kiểm tra Hạ tầng Tổng quan trên Grafana (`:18117`)**:
   - Mở dashboard `File Management V2 overview`.
   - Kiểm tra `Services Up == 5/5` và xem thanh `Pending Outbox Work`.
   - *Trường hợp A*: Nếu `catalog_outbox_pending > 0` kéo dài ➔ Outbox Relay của Catalog Service bị dừng hoặc Kafka Broker hỏng.
   - *Trường hợp B*: Nếu mọi chỉ số Grafana xanh ➔ Sự cố nằm ở logic xử lý dữ liệu của từng event cụ thể.
2. **Bước 2: Lấy thông tin Vết (Context Identification)**:
   - Thu thập `scanRunId`, `proposalId`, hoặc tên file từ yêu cầu người dùng (`identityKey: JOKE-011`).
3. **Bước 3: Tra cứu Kibana Discover (`:18114`)**:
   - Nhập KQL: `message : "*JOKE-011*"` trên Kibana.
   - Kiểm tra chuỗi log:
     - Log 1: `scan-service` - `Decided scan proposal decision=APPROVE` ➔ Đã lưu DB `scan_db` & Outbox thành công.
     - Log 2: `scan-service` - `Published outbox event eventId=c67c59b5...` ➔ Outbox Relay đã đẩy tin sang Kafka.
     - Log 3: `catalog-service` - `Processed media discovery eventId=c67c59b5... subjectId=f190c29e...` ➔ Catalog đã tiêu thụ event và lưu DB `catalog_db`.
     - Log 4: `query-service` - `Processed query subject projection eventId=...` ➔ Query đã cập nhật Read Model.
4. **Bước 4: Xác định điểm đứt gãy (Root Cause Analysis)**:
   - Nếu thiếu Log 3: Kiểm tra Kafka Consumer Group lag hoặc Deserialization Error tại `catalog-service`.
   - Nếu thiếu Log 4: Kiểm tra Outbox Relay tại `catalog-service` hoặc DB lock tại `query-service`.<br>
**Required trade-offs:** Cần log chuẩn hóa đủ các ID liên kết ở các điểm chạm chính.<br>
**Follow-up ladder:** Dead Letter Topic (DLT) giúp ích gì trong việc xử lý các message bị lỗi Deserialization?<br>
**Red flags:** Đoán mò nguyên nhân và remote debug trực tiếp trên môi trường Production mà không đọc Grafana/Kibana.
