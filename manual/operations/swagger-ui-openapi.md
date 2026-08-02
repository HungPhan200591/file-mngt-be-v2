# Hướng dẫn Vận hành Swagger UI & OpenAPI Contracts

Tài liệu hướng dẫn sử dụng và kiểm thử REST API Contracts của **Backend V2** thông qua **Swagger UI** ở môi trường local.

---

## 1. Kiến trúc Contract-First

Backend V2 áp dụng mô hình **Contract-First**:
- Mọi API contract được định nghĩa chuẩn hóa dưới dạng file **OpenAPI 3.1 YAML** trong thư mục [`docs/contracts/openapi/`](../../docs/contracts/openapi/).
- Code ứng dụng (Controllers/DTOs) tuân thủ strictly theo contract OpenAPI.
- Không dùng thư viện Java runtime-scanning (như `springdoc-openapi`) để giữ microservices gọn nhẹ và tách biệt giữa code và contract spec.

---

## 2. Thông số Swagger UI Local

- **Host Port**: `18118` (Theo [ADR-004](../../docs/adr/ADR-004-local-port-allocation.md)).
- **Container Service**: `swagger-ui` trong [infra/compose/compose.yaml](../../infra/compose/compose.yaml).
- **Trình duyệt**: `http://localhost:18118`

---

## 3. Khởi động Swagger UI

Khởi động cùng với toàn bộ hạ tầng Docker local:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml up -d
```

Nếu chỉ muốn tái tạo hoặc khởi động riêng service `swagger-ui`:

```powershell
docker compose --env-file .env -f infra/compose/compose.yaml up -d --force-recreate swagger-ui
```

---

## 4. Các tính năng chính trên giao diện Swagger UI

### 4.1 Chuyển đổi giữa các API Specs (Select Dropdown)
Ở góc trên bên phải giao diện Swagger UI (`http://localhost:18118`), chọn menu thả xuống để chuyển đổi giữa các bộ contract:
- **Catalog API**: Quản lý canonical subject, asset (`/api/v2/catalog/...`).
- **Scan API**: Khởi tạo scan folder, xem preview và approve proposals (`/api/v2/scans/...`).
- **Query API**: Read model search (Elasticsearch + PostgreSQL fallback + Redis cache) (`/api/v2/query/...`).
- **Media Delivery API**: contract legacy FT011; static media mới được Nginx phục vụ trực tiếp theo [ADR-005](../../docs/adr/ADR-005-nginx-direct-media-delivery.md).

### 4.2 Lựa chọn Target Server (Gateway vs Direct Microservice)
Mỗi OpenAPI spec cung cấp sẵn 2 tùy chọn Server trong mục **Servers**:
- **`http://localhost:18100` (Local V2 Gateway - Routed)**: *(Mặc định)* Gửi request qua API Gateway. Dùng để test đúng luồng thực tế frontend gọi vào và kiểm thử lan truyền `X-Correlation-Id`.
- **Direct Service URL (`http://localhost:18101`, `18102`, `18103`)**: Gửi request trực tiếp vào Microservice con (dùng khi debug độc lập từng service).

---

## 5. Quy trình Cập nhật Contract (Live Sync)

Do thư mục `docs/contracts/openapi/` được mount trực tiếp dạng `volume` vào container `swagger-ui`:
1. Chỉnh sửa hoặc thêm mới API endpoint trong các file `.yaml` tại `docs/contracts/openapi/`.
2. Lưu file trên IDE.
3. Nhấn **F5** (Refresh) trên trình duyệt `http://localhost:18118` để giao diện tự động cập nhật schema mới nhất mà **không cần restart Docker container**.
