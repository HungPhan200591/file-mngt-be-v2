# Quan sát Backend V2 ở local

Tài liệu này dành cho chủ dự án thao tác. Đây không phải context mặc định của AI Agent. Quyết định kỹ
thuật và port chuẩn nằm trong FT014 cùng ADR-002/003/004.

## 1. Chuẩn bị

Các application phải được chạy lại sau khi nhận FT014 để có Prometheus registry và ECS file logging.
Mặc định mỗi app ghi file `logs/<service>.json.log` theo working directory của IntelliJ. Compose đã
mount cả `logs/` tại root và `apps/*/logs/`, nên không cần thêm environment variable thủ công trong
trường hợp thông thường.

Nếu muốn đặt file ở vị trí khác, thêm riêng cho Run Configuration:

```text
OBSERVABILITY_LOG_FILE=D:/Pesonal/file-management/v2/file-mngt-be-v2/logs/<service>.json.log
```

## 2. Bật stack

Từ root project:

```powershell
docker compose --profile observability --env-file .env -f infra/compose/compose.yaml up -d
docker compose --profile observability --env-file .env -f infra/compose/compose.yaml ps
```

Sau đó chạy năm app từ IntelliJ JDK 25. Không cần bật thêm profile Spring.

## 3. Kiểm tra metrics

Mở trực tiếp một endpoint để xác nhận registry:

```text
http://localhost:18100/actuator/prometheus
http://localhost:18101/actuator/prometheus
http://localhost:18102/actuator/prometheus
http://localhost:18103/actuator/prometheus
http://localhost:18104/actuator/prometheus
```

Mở Prometheus tại `http://localhost:18116/targets`: năm target phải `UP`. Mở Grafana tại
`http://localhost:18117`, đăng nhập bằng `GRAFANA_ADMIN_USER/GRAFANA_ADMIN_PASSWORD` trong `.env`, rồi
vào folder `File Management V2` và dashboard `File Management V2 overview`.

Dashboard chỉ có số liệu HTTP sau khi tạo traffic. Có thể chạy các scenario `.http`/npm E2E hiện có để
tạo request; không cần tạo test riêng chỉ để làm đầy biểu đồ.

Kiểm tra toàn bộ metrics foundation bằng file `tests/e2e/observability/001-foundation.http` trong
IntelliJ, hoặc từ `tests/e2e` chạy:

```powershell
npm run observability:local
```

## 4. Kiểm tra structured logs

Gọi một API qua Gateway với header tự chọn:

```powershell
Invoke-WebRequest http://localhost:18100/api/v2/query/subjects?size=1 `
  -Headers @{ "X-Correlation-Id" = "manual-observe-001" }
```

Kiểm tra thư mục `logs/` có JSON log. Khi Logstash đã ingest, chạy request trong
`infra/observability/kibana/data-view.http` một lần, rồi mở Kibana tại `http://localhost:18114` và tìm
`correlationId: "manual-observe-001"` trong Discover.

Gateway chuẩn hóa correlation ID. Bốn downstream service dùng module `platform/observability` để đưa
header vào MDC cả khi gọi trực tiếp. Logstash hoặc Elasticsearch tắt không làm application request lỗi;
file log vẫn được ghi local.

## 5. Dừng

```powershell
docker compose --profile observability --env-file .env -f infra/compose/compose.yaml down
```

Không thêm `-v` nếu muốn giữ Elasticsearch, Prometheus và Grafana local data.
