# Local infrastructure

`compose.yaml` là owner của local infrastructure. Host port phải theo
[ADR-004](../../docs/adr/ADR-004-local-port-allocation.md); version image phải được pin, không dùng
`latest`. Sao chép `.env.example` thành `.env` ở root để override local và không commit secret thật.

Tệp ứng dụng BE & FE V2 nằm tại `compose.apps.yaml`. Xem chi tiết hướng dẫn vận hành 3 chế độ (Local Dev, Hybrid Debug, Full Docker) tại [Runbook Vận Hành Docker](../../manual/operations/docker-and-dev-modes.md).

## Application containers (BE Microservices & FE V2)

```powershell
# Bật toàn bộ 5 BE microservices + FE V2 trong Docker
docker compose --env-file .env -f infra/compose/compose.yaml -f infra/compose/compose.apps.yaml up -d --build

# Dừng 1 service để giải phóng port cho Local IDE Debug (ví dụ: scan-service)
npm run docker:stop scan-service
```

## All profiles (Toàn bộ container)

Lệnh chạy toàn bộ container của dự án (Core, Search và Observability):

```powershell
docker compose --profile search --profile observability --env-file .env -f infra/compose/compose.yaml up -d
```

## Core profile

Lệnh mặc định chạy Nginx media V2, PostgreSQL, Kafka KRaft và Redis:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml up -d
```

Nginx media V2 là service `nginx-media`, dùng config riêng
`infra/nginx/nginx.conf` và host port `18119`. Nó chỉ mount `D:`, `E:`, `G:` read-only
cho static media; không dùng container, config hay port `8888` của V1. Public URL local
có dạng `http://localhost:18119/files/<drive>:/<path-encoded>`.

PostgreSQL tạo ba database/user độc lập. `kafka-volume-init` là init job cấp quyền cho
`kafka-data`, kết thúc với exit code `0` là đúng; không xóa nó và không chạy Kafka broker bằng root.

## Search profile

```powershell
docker compose --profile search --env-file .env -f infra/compose/compose.yaml up -d
```

Profile này thêm Elasticsearch tại `18113` cho media search của Query. Elasticsearch chỉ là search
projection, không phải canonical database.

## Observability profile

```powershell
docker compose --profile observability --env-file .env -f infra/compose/compose.yaml up -d
```

Profile này thêm Elasticsearch, Logstash, Kibana, Prometheus và Grafana. Elasticsearch dùng chung
instance nhưng tách logical data:

- Query sở hữu media search index;
- Logstash ghi ECS log vào data stream `logs-file_mngt_v2-local`.

Prometheus scrape trực tiếp năm service qua `/actuator/prometheus`; Actuator không được route qua
Gateway. Logstash đọc JSON log ở `logs/` tại project root hoặc trong từng app module, nên hoạt động với
cả hai kiểu IntelliJ working directory phổ biến.

Địa chỉ local:

- Kibana: `http://localhost:18114`;
- Logstash JSON-lines input dự phòng: `localhost:18115`;
- Prometheus: `http://localhost:18116`;
- Grafana: `http://localhost:18117`.

Grafana tự provision Prometheus datasource và dashboard `File Management V2 overview`. Kibana cần tạo
data view một lần bằng `infra/observability/kibana/data-view.http` sau khi đã có log được ingest.

## Quản lý & Vận hành (Operations)

### 1. Kiểm tra trạng thái container

```powershell
docker compose --profile search --profile observability -f infra/compose/compose.yaml ps
```

### 2. Xem log container (Real-time tail)

- Xem log toàn bộ container:
  ```powershell
  docker compose --profile search --profile observability -f infra/compose/compose.yaml logs -f
  ```
- Xem log riêng cho một service cụ thể (ví dụ: `nginx-media`, `postgres`, `kafka`):
  ```powershell
  docker compose -f infra/compose/compose.yaml logs -f nginx-media
  ```

### 3. Restart một service cụ thể

```powershell
docker compose -f infra/compose/compose.yaml restart nginx-media
```

### 4. Tạm dừng / Khởi động lại container (Giữ nguyên trạng thái)

- Tạm dừng (Stop):
  ```powershell
  docker compose --profile search --profile observability -f infra/compose/compose.yaml stop
  ```
- Bật lại (Start):
  ```powershell
  docker compose --profile search --profile observability -f infra/compose/compose.yaml start
  ```

### 5. Dừng và gỡ bỏ container / network

- Hạ stack (Vẫn giữ nguyên named volumes dữ liệu):
  ```powershell
  docker compose --profile search --profile observability -f infra/compose/compose.yaml down
  ```
- Hạ stack và XÓA TOÀN BỘ DỮ LIỆU (Cần cẩn trọng):
  ```powershell
  docker compose --profile search --profile observability -f infra/compose/compose.yaml down -v
  ```
