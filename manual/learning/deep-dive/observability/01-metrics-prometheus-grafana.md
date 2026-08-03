# 📈 Metrics Deep-Dive: Prometheus & Grafana

Tài liệu đi sâu vào kiến trúc thu thập chỉ số (Metrics), cấu hình Spring Boot Actuator, Micrometer, cơ chế Scrape của Prometheus, thiết kế Grafana Dashboard và cách truy vấn PromQL qua UI lẫn CLI.

---

## 1. Kiến trúc Thu thập Metrics với Micrometer & Spring Actuator

Tất cả 5 microservices (`gateway-service`, `scan-service`, `catalog-service`, `query-service`, `media-worker`) tích hợp sẵn `micrometer-registry-prometheus`.

### 1.1. Scrape Endpoint Security & Separation
- Endpoint `/actuator/prometheus` **không được expose qua API Gateway** để tránh lộ chỉ số nội bộ ra bên ngoài.
- Dịch vụ phơi endpoint này trực tiếp trên Port riêng của từng service:
  - `gateway-service`: `http://localhost:18100/actuator/prometheus`
  - `catalog-service`: `http://localhost:18101/actuator/prometheus`
  - `scan-service`: `http://localhost:18102/actuator/prometheus`
  - `query-service`: `http://localhost:18103/actuator/prometheus`
  - `media-worker`: `http://localhost:18104/actuator/prometheus`

### 1.2. Cấu hình Prometheus Scrape (`infra/observability/prometheus/prometheus.yml`)
```yaml
global:
  scrape_interval: 5s
  evaluation_interval: 5s

scrape_configs:
  - job_name: 'file-mngt-v2-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:18100' # gateway-service
          - 'host.docker.internal:18101' # catalog-service
          - 'host.docker.internal:18102' # scan-service
          - 'host.docker.internal:18103' # query-service
          - 'host.docker.internal:18104' # media-worker
```

---

## 2. Phân loại Metric Types & Quy tắc High Cardinality

### 2.1. Các loại Metric chính trong Hệ thống
1. **Counter**: Chuỗi tăng đơn điệu (Monotonic). Dùng đếm tổng số HTTP Requests, số lỗi 5xx, số events đã publish. Trích xuất lưu lượng bằng hàm `rate()`.
2. **Gauge**: Giá trị biến thiên 2 chiều (bật/tắt hoặc tăng/giảm). Dùng đo bộ nhớ JVM Heap (`jvm_memory_used_bytes`), số kết nối DB active (`hikaricp_connections_active`), số backlog outbox (`catalog_outbox_pending`).
3. **Histogram / Timer**: Đo phân bố độ trễ (Latency Distribution) và phân bố kích thước. Dùng tính p50, p95, p99 Latency bằng hàm `histogram_quantile()`.

### 2.2. Quy tắc Chống Bùng nổ Nhãn (High Cardinality Prevention)
- **Khái niệm**: High Cardinality xảy ra khi một Label của Metric chứa quá nhiều giá trị duy nhất (như `userId`, `scanRunId`, raw URL `GET /api/v2/scans/64932fb9...`).
- **Nguy hiểm**: Prometheus khởi tạo một Time-Series riêng cho mỗi tổ hợp Label unique. High Cardinality làm bùng nổ RAM Prometheus Server.
- **Quy tắc dự án**:
  - Chỉ dùng các label có tập hợp cố định nhỏ: `application`, `method`, `status`, `uri` (chuẩn hóa dạng `/api/v2/scans/{scanId}`).
  - Tuyệt đối KHÔNG dùng ID, path động, file name, hay exception stack trace làm metric label.

---

## 3. Chi tiết Panels trên Grafana Dashboard (`File Management V2 overview`)

Dashboard tự động provision tại `http://localhost:18117` từ file `infra/observability/grafana/dashboards/file-mngt-v2-overview.json`:

| Ten Panel | Loai Panel | PromQL Expression | Y nghia & Nguong theo doi |
| :--- | :---: | :--- | :--- |
| **Services Up** | Stat | `sum(up{job="file-mngt-v2-services"})` | Đảm bảo đủ `5/5` services đang RUNNING. |
| **HTTP Request Rate** | Timeseries | `sum by (application) (rate(http_server_requests_seconds_count[5m]))` | Lưu lượng request/giây trên từng service. |
| **HTTP 5xx Errors** | Stat | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))` | Cảnh báo ngay nếu có lỗi 5xx xuất hiện (`> 0`). |
| **JVM Heap Used** | Timeseries | `sum by (application) (jvm_memory_used_bytes{area="heap"})` | Đo RAM tiêu thụ của từng JVM application. |
| **Process CPU** | Timeseries | `max by (application) (process_cpu_usage)` | Tỷ lệ % CPU sử dụng của từng microservice. |
| **Active DB Connections** | Timeseries | `max by (application) (hikaricp_connections_active)` | Số lượng connection HikariPool active tới Postgres. |
| **Pending Outbox Work** | Bar Gauge | `max by (__name__, application) ({__name__=~"catalog_outbox_pending\|query_search_outbox_pending"})` | Đo lượng message đang nghẽn tại Outbox Relay. |

---

## 4. Hướng dẫn Truy vấn PromQL qua HTTP API / CLI

Ngoài Grafana UI, bạn có thể gửi truy vấn PromQL trực tiếp tới Prometheus HTTP API tại port `18116` từ thư mục gốc dự án:

### 4.1. Truy vấn CPU Usage
```powershell
curl.exe -s "http://localhost:18116/api/v1/query?query=process_cpu_usage"
```

### 4.2. Truy vấn Active Database Connections
```powershell
curl.exe -s "http://localhost:18116/api/v1/query?query=hikaricp_connections_active"
```

### 4.3. Truy vấn JVM Memory Heap
```powershell
curl.exe -s "http://localhost:18116/api/v1/query?query=jvm_memory_used_bytes"
```

---

## 5. Tài liệu Tham khảo Liên quan
- [00. Tổng quan Observability](00-overview.md)
- [02. Structured Logging: Spring Boot ECS & ELK](02-structured-logging-elk.md)
- [Ngân hàng Câu hỏi Phỏng vấn Metrics & Prometheus](question-bank/01-metrics-questions.md)
