# Local infrastructure

`compose.yaml` là owner của local infrastructure. Host port phải theo
[ADR-004](../../docs/adr/ADR-004-local-port-allocation.md); version image phải được pin, không dùng
`latest`. Sao chép `.env.example` thành `.env` ở root để override local và không commit secret thật.

## Core profile

Lệnh mặc định chạy PostgreSQL, Kafka KRaft và Redis:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml up -d
```

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

Tắt stack bằng `docker compose ... down` không làm mất named volume. Không thêm `-v` nếu chưa chủ động
muốn xóa dữ liệu local.
