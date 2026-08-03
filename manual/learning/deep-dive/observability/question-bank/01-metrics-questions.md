# ❓ Metrics & Prometheus — Interview Question Bank

Bộ câu hỏi phỏng vấn Chuyên sâu (Senior / Lead) về Metrics, PromQL, Micrometer, Spring Boot Actuator và Prometheus / Grafana Dashboard.

---

## 📊 Bảng Ma trận Coverage

| Level | Foundation | Senior | Architect | Tổng số câu |
| :--- | :---: | :---: | :---: | :---: |
| **Số lượng** | 1 | 1 | 1 | 3 |

---

## 🎯 Danh sách Câu hỏi Chi tiết & Đáp án Chuẩn

### OBS-METRICS-001 — `SENIOR`
**Question:** High Cardinality trong Prometheus Metric là gì? Tại sao nó gây bùng nổ bộ nhớ (RAM Explosion) và dự án Backend V2 phòng tránh như thế nào?<br>
**Target depth:** `D2-D3` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_SCENARIO`<br>
**Interviewer evaluates:** Hiểu biết về cấu trúc lưu trữ Time-Series của Prometheus, tác hại của unbounded labels và quy tắc thiết kế metric chuẩn.<br>
**Answer outline:**
- **Khái niệm High Cardinality**: Xảy ra khi một Label của Metric chứa quá nhiều giá trị duy nhất (Unique Values), ví dụ: `userId`, `orderId`, `scanRunId`, hoặc URL raw chưa chuẩn hóa dạng `/api/v2/scans/64932fb9-83d9-418f-8d88-b187c1729392`.
- **Tác hại bùng nổ RAM**: Prometheus khởi tạo và duy trì một **Time-Series riêng biệt trong RAM** cho mỗi tổ hợp Label unique. Nếu có 1.000.000 user, metric `http_requests_total{user_id="123"}` sẽ tạo ra 1.000.000 time-series ➔ Tràn RAM Prometheus Server (OOM Crash).
- **Quy tắc Phòng tránh trong Dự án**:
  1. **Quy định nhãn cố định (Bounded Labels)**: Chỉ dùng các nhãn có tập hợp giá trị nhỏ và cố định (`service.name`, `http_method`, `normalized_uri`, `status`).
  2. **Chuẩn hóa URI Path Variable**: Biến `/api/v2/scans/64932fb9...` thành `/api/v2/scans/{scanId}`.
  3. **Cấm tuyệt đối**: Không bao giờ đưa UUID, filename, relative path, email, hay stack trace vào Metric Label.<br>
**Required trade-offs:** Không dùng high-cardinality label làm mất khả năng filter theo ID cá thể trong Prometheus (phải dùng Kibana Log hoặc Distributed Trace để soi chi tiết ID).<br>
**Follow-up ladder:** Công cụ nào giúp phát hiện các metric có High Cardinality cao nhất trên Prometheus?<br>
**Red flags:** Trả lời "Cho UUID vào Label để lên Grafana search cho tiện".

---

### OBS-METRICS-002 — `FOUNDATION`
**Question:** Phân biệt 4 loại Metric cơ bản (Counter, Gauge, Histogram, Summary). Khi nào dùng loại nào và các hàm PromQL như `rate()` hay `histogram_quantile()` hoạt động ra sao?<br>
**Target depth:** `D1-D2` · **Interview likelihood:** `HIGH` · **Question type:** `COMMON_CORE`<br>
**Interviewer evaluates:** Nắm vững các kiểu dữ liệu Micrometer/Prometheus và cú pháp PromQL cơ bản.<br>
**Answer outline:**
- **Counter**: Chuỗi số tăng đơn điệu (chỉ tăng hoặc reset về 0 khi restart app). Dùng đếm tổng số HTTP Request, số Exception. Dùng hàm `rate(counter[5m])` để tính tốc độ trung bình request/giây trong 5 phút.
- **Gauge**: Giá trị biến thiên 2 chiều (tăng/giảm). Dùng đo trạng thái hiện tại: RAM tiêu thụ (`jvm_memory_used_bytes`), số connection DB đang active (`hikaricp_connections_active`), số message backlog.
- **Histogram**: Chia các giá trị quan sát vào các khoảng cố định (Buckets: `le="0.1"`, `le="0.5"`, `le="1.0"`). Cho phép tính quantile p95, p99 chính xác trên Prometheus Server bằng hàm `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))`.
- **Summary**: Tính sẵn quantile p95, p99 phía Application client. Tốn CPU client hơn và không thể aggregate (gộp) từ nhiều instance microservice lại với nhau.<br>
**Required trade-offs:** Histogram tạo ra nhiều series `_bucket`, cần chọn danh sách bucket hợp lý với SLO.<br>
**Follow-up ladder:** Tại sao không thể lấy trung bình cộng (Average) các giá trị p95 từ nhiều service về?<br>
**Red flags:** Dùng Gauge để đếm tổng số request hoặc dùng Counter để đo lượng RAM tiêu thụ.

---

### OBS-METRICS-003 — `ARCHITECT`
**Question:** Trong kiến trúc Event-Driven Microservices của dự án, làm thế nào để theo dõi sự tắc nghẽn của Outbox Relay bằng Metrics và chẩn đoán nguyên nhân qua Grafana?<br>
**Target depth:** `D3-D4` · **Interview likelihood:** `HIGH` · **Question type:** `PROJECT_APPLICATION`<br>
**Interviewer evaluates:** Khả năng kết nối Metric nghiệp vụ/hạ tầng với kiến trúc Transactional Outbox Pattern.<br>
**Answer outline:**
1. **Metric Tự định nghĩa (Custom Micrometer Gauge)**:
   - Trong `catalog-service` và `query-service`, hệ thống đăng ký Gauge đếm số bản ghi pending trong bảng Outbox: `catalog_outbox_pending` và `query_search_outbox_pending`.
2. **Theo dõi trên Grafana Dashboard**:
   - Panel **Pending Outbox Work** hiển thị dạng Bar Gauge tại `http://localhost:18117`.
   - Ngưỡng bình thường: `= 0` hoặc nảy lên ngắn hạn khi có batch transaction rồi về `0` ngay lập tức.
3. **Kịch bản Chẩn đoán khi Outbox Pending > 0 kéo dài**:
   - Nếu `catalog_outbox_pending > 0`: Outbox Publisher Scheduled Task bị crash, Kafka Broker hỏng, hoặc ngắt kết nối Network.
   - Nếu `query_search_outbox_pending > 0`: Tiến trình đồng bộ dữ liệu sang Elasticsearch Search Index bị chậm hoặc ES cluster bị High CPU/Disk Full.<br>
**Required trade-offs:** Việc query `COUNT(*)` từ bảng outbox định kỳ có thể gây overhead nhẹ cho Database nếu không có index phù hợp trên cột `processed = false`.<br>
**Follow-up ladder:** Bạn làm thế nào để tối ưu câu truy vấn Gauge outbox đếm nhanh nhất trên PostgreSQL?<br>
**Red flags:** Không theo dõi outbox backlog bằng metric mà chờ người dùng báo mất dữ liệu mới biết outbox bị dừng.
