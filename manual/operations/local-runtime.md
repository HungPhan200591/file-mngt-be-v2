# Vận hành Backend V2 ở local

Tài liệu này dành cho người vận hành project bằng IntelliJ và Docker Desktop. Đây không phải context, rule hay tài liệu bắt buộc cho AI Agent.

## Mục tiêu runtime

- Năm app Spring Boot chạy từ IntelliJ.
- PostgreSQL, Kafka KRaft và Redis chạy bằng Docker Compose; Elasticsearch chạy khi bật profile `search`.
- Port host V2 luôn theo [ADR-004](../../docs/adr/ADR-004-local-port-allocation.md). Không dùng lại port V1.

## Chuẩn bị lần đầu

1. Mở folder project trong IntelliJ.
2. Chọn JDK 25 tại `File > Project Structure > Project SDK`; trong `Settings > Build Tools > Maven`, đặt Maven JRE là Project JDK 25. Không cần đổi JDK hệ thống.
3. Trong root project, tạo local environment file:

```powershell
Copy-Item .env.example .env
```

`.env` không được commit. Có thể đổi password local trong đó trước khi chạy Docker.

## Khởi động hạ tầng Docker

Từ root project:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml config
docker compose --env-file .env -f infra/compose/compose.yaml up -d
docker compose --env-file .env -f infra/compose/compose.yaml ps -a
```

Khi cần fuzzy search/autocomplete của Query, bật Elasticsearch thay vì lệnh `up` thường:

```powershell
docker compose --profile search --env-file .env -f infra/compose/compose.yaml up -d
```

Trạng thái mong đợi:

- `postgres`, `kafka`, `redis`: `healthy`.
- `kafka-volume-init`: `Exited (0)`. Đây là init job cấp quyền cho Kafka volume, không phải service chạy nền.

Kiểm tra database/user mặc định:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml exec postgres psql -U platform_admin -d postgres -c "\l"
docker compose --env-file .env -f infra/compose/compose.yaml exec postgres psql -U platform_admin -d postgres -c "\du"
```

Phải có `catalog_db/catalog_user`, `scan_db/scan_user`, `query_db/query_user`.

## Chạy năm application từ IntelliJ

Chạy từng main class:

1. `GatewayApplication`
2. `CatalogApplication`
3. `ScanApplication`
4. `QueryApplication`
5. `MediaWorkerApplication`

Kiểm tra health sau khi app khởi động:

```powershell
Invoke-RestMethod http://localhost:18100/actuator/health/liveness
Invoke-RestMethod http://localhost:18101/actuator/health/liveness
Invoke-RestMethod http://localhost:18102/actuator/health/liveness
Invoke-RestMethod http://localhost:18103/actuator/health/liveness
Invoke-RestMethod http://localhost:18104/actuator/health/liveness
```

Mỗi lệnh phải trả `status: UP`. Dùng endpoint `readiness` thay `liveness` khi cần kiểm tra sẵn sàng nhận request.

## Media search Elasticsearch

Query vẫn phục vụ detail/list từ PostgreSQL khi Elasticsearch chưa chạy hoặc lỗi. Request có `search` khi đó trả `searchBackend: POSTGRESQL_FALLBACK`, `degraded: true`; autocomplete trả danh sách rỗng. Search outbox retry theo exponential backoff từ 5 giây đến tối đa 5 phút và tự hội tụ sau khi Elasticsearch ready, tránh ghi DB/log mỗi giây khi profile `search` đang tắt.

Khởi tạo hoặc thay mapping index một lần sau khi bật profile `search`:

```powershell
Invoke-RestMethod -Method Post http://localhost:18103/api/v2/query/operations/search-index/rebuild
```

Lệnh rebuild tạo index physical mới, nạp Query projection, rồi atomically chuyển alias. Nếu lỗi, alias cũ không bị thay. Không xóa volume `elasticsearch-data` trừ khi chủ động muốn build lại toàn bộ index.

Sau khi năm service và Elasticsearch đều chạy, vào `tests/e2e` và dùng `npm run scan:search:local` để bắt buộc xác minh luồng Scan → Catalog → Query → Elasticsearch. `npm run scan:local` vẫn là baseline và cho phép PostgreSQL fallback.

## Chạy lại hằng ngày

Khởi động lại hạ tầng đã có dữ liệu:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml up -d
```

Sau đó chạy app từ IntelliJ như trên. Khi dừng, stop app trong IntelliJ rồi:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml down
```

Không thêm `-v`: lệnh đó sẽ xóa volume PostgreSQL, Kafka và Redis.

## Sự cố thường gặp

### Kafka báo `AccessDeniedException` tại `kraft-combined-logs`

Compose đã có `kafka-volume-init` để tự cấp quyền. Chạy lại:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml down
docker compose --env-file .env -f infra/compose/compose.yaml up -d
docker compose --env-file .env -f infra/compose/compose.yaml logs kafka-volume-init kafka
```

Không chạy Kafka broker bằng root và không cần xóa volume chỉ vì lỗi này.

### Maven/IntelliJ dùng sai Java

Kiểm tra Maven Runner JRE trong IntelliJ là JDK 25. Với IntelliJ Terminal, kiểm tra:

```powershell
$env:JAVA_HOME
(Get-Command java).Source
java -version
.\mvnw.cmd -version
```

Maven Wrapper cố định Maven 3.9.16; JDK được quyết định bởi Maven runner hoặc terminal session.

### Port bị chiếm

Không tự đổi port trong app hay Compose. Kiểm tra [ADR-004](../../docs/adr/ADR-004-local-port-allocation.md), sau đó kiểm tra process local:

```powershell
Get-NetTCPConnection -State Listen | Sort-Object LocalPort
```

Nếu cần đổi/mở rộng dải port V2, cập nhật ADR-004 trước.
