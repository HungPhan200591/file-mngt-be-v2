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

## Query detail cache Redis

`GET /api/v2/query/subjects/{id}` dùng cache-aside trong Redis. PostgreSQL `query_db` vẫn là source of truth; Redis miss, timeout hoặc unavailable chỉ làm request chậm hơn một chút, không làm detail API lỗi.

Cấu hình mặc định:

- Redis: `localhost:18112` qua `REDIS_HOST` và `REDIS_PORT`.
- TTL: `QUERY_DETAIL_CACHE_TTL=10m`.
- Timeout: `QUERY_DETAIL_CACHE_CONNECT_TIMEOUT=500ms` và `QUERY_DETAIL_CACHE_COMMAND_TIMEOUT=500ms`.
- Tắt cache để rollback/chẩn đoán: `QUERY_DETAIL_CACHE_ENABLED=false` rồi restart `QueryApplication`.

Kiểm tra runtime bằng scenario dùng chung cho IntelliJ và CLI. Trong `tests/e2e` chạy:

```powershell
npm run query:cache:local
```

Hoặc mở `tests/e2e/query/001-detail-cache.http` trong IntelliJ, chọn environment `local` và chạy lần lượt từ trên xuống. Scenario tự chọn một Query subject, gọi detail hai lần và kiểm tra Actuator metrics; nếu `query_db` trống thì chạy `npm run catalog:local` để tạo subject mới rồi đợi Kafka → Query hội tụ.

Các metric còn lại là `query.detail.cache.put`, `query.detail.cache.eviction` và `query.detail.cache.lookup`. Cache key có dạng `query:subject-detail:v1:<subjectId>`; không sửa/xóa thủ công trừ khi đang chẩn đoán local.

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

## Gateway routing và correlation ID

Gateway nghe tại `http://localhost:18100` và route Catalog subjects, Scan API, Query subjects theo [gateway HTTP contract](../../docs/contracts/http/gateway-routing-v1.md). Gateway không public operation endpoint hay downstream Actuator; dùng direct service port cho công việc operation/admin.

Sau khi Gateway, Catalog và Query đã chạy, tại `tests/e2e` chạy:

```powershell
npm run gateway:local
```

Gateway giữ nguyên `X-Correlation-Id` hợp lệ, hoặc tạo UUID khi header thiếu/trùng/sai format. Có thể override downstream URL và timeout qua `CATALOG_SERVICE_URL`, `SCAN_SERVICE_URL`, `QUERY_SERVICE_URL`, `GATEWAY_HTTP_CLIENT_CONNECT_TIMEOUT`, `GATEWAY_HTTP_CLIENT_READ_TIMEOUT`; mặc định là 1 giây connect và 30 giây read.

## Media delivery V2

Media Worker chạy ở `18104` và chỉ nhận request media từ Gateway. Để Worker đọc file local, copy template rồi đổi path theo máy:

```powershell
Copy-Item apps/media-worker/src/main/resources/application-local.example.yml apps/media-worker/src/main/resources/application-local.yml
```

`media.roots` là registry `key → path`; `key` phải khớp `sourceRootKey` đã scan. Không thêm raw path vào frontend hay request URL. Máy development có thể dùng `fixture-joke-video` trong template; root thực tế cần trỏ đúng folder đã scan.

Media Worker gọi Catalog với timeout mặc định 1 giây connect, 5 giây read. Khi cần chẩn đoán local, có thể override `CATALOG_SERVICE_URL`, `MEDIA_CATALOG_CONNECT_TIMEOUT` và `MEDIA_CATALOG_READ_TIMEOUT`; không đổi port hay public Worker trên browser. Dùng `npm run media:local` trong `tests/e2e` sau khi Scan fixture để xác minh GET Range/HEAD đi qua Gateway.

## Observability local

Khi cần xem metrics/dashboard hoặc tìm structured log theo correlation ID, dùng
[Quan sát Backend V2 ở local](./observability-local.md). Stack này là profile Compose opt-in; không cần
bật để chạy business flow thông thường.
