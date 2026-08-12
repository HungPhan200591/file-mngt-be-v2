# Runbook Vận Hành Docker & Chuyển Đổi Chế Độ Phát Triển (BE & FE V2)

Tài liệu này hướng dẫn chi tiết cách vận hành, chuyển đổi linh hoạt giữa 3 chế độ phát triển (**Pure Local Dev**, **Hybrid Debug**, **Full Docker**) cho cả người lập trình lẫn **AI Agent**.

---

## 1. Nguyên Tắc Thiết Kế Mạng & Port (ADR-004)

Tất cả các dịch vụ tuân thủ nghiêm ngặt dải port host tại [ADR-004](../../docs/adr/ADR-004-local-port-allocation.md):

| Dịch Vụ / Thành Phần | Container Port | Host Port | Chức Năng |
| :--- | :---: | :---: | :--- |
| **Gateway Service** | 18100 | **18100** | REST API Gateway |
| **Catalog Service** | 18101 | **18101** | Quản lý danh mục & metadata |
| **Scan Service** | 18102 | **18102** | Quét file & phát hiện thay đổi |
| **Query Service** | 18103 | **18103** | Truy vấn, tìm kiếm & cache |
| **Media Worker** | 18104 | **18104** | Xử lý & preview media |
| **Frontend V2 (React)** | 18120 | **18120** | Giao diện quản trị Admin FE V2 |
| **PostgreSQL** | 5432 | **18110** | Cơ sở dữ liệu chính |
| **Kafka KRaft** | 29092 | **18111** | Message Broker |
| **Redis** | 6379 | **18112** | Cache cho Query Service |
| **Elasticsearch** | 9200 | **18113** | Search projection |

Mọi file `application.yml` ở Backend và `vite.config.ts` ở Frontend mặc định kết nối về `localhost:<host_port>`, cho phép bật/tắt linh hoạt ở Local mà không phải thay đổi mã nguồn hay biến môi trường.

---

## 2. Hướng Dẫn Vận Hành 3 Chế Độ

### 🟢 Chế độ 1: Pure Local Dev (Khuyên dùng khi Code & Debug hàng ngày)
- **Hạ tầng Docker**:
  ```powershell
  npm run docker:up
  ```
  *(Lệnh này chỉ khởi chạy PostgreSQL, Kafka, Redis, Nginx, Swagger)*

- **Backend Services**:
  Mở project trong IntelliJ (JDK 25) và chạy bất kỳ main class nào (`GatewayApplication`, `CatalogApplication`, `ScanApplication`, `QueryApplication`, `MediaWorkerApplication`).

- **Frontend FE V2**:
  Tại thư mục `file-mngt-fe-v2`, chạy:
  ```powershell
  npm run dev
  ```
  Truy cập FE tại: `http://localhost:18120`.

---

### 🟡 Chế độ 2: Hybrid Debug Mode (Debug 1 Microservice cụ thể)
Khi toàn bộ hệ thống đang chạy trong Docker nhưng bạn cần debug sâu 1 service (ví dụ: `scan-service`):

1. **Khởi động toàn bộ hạ tầng & ứng dụng trong Docker**:
   ```powershell
   npm run docker:apps:up
   ```

2. **Dừng duy nhất service muốn debug để nhả port**:
   ```powershell
   npm run docker:stop scan-service
   ```

3. **Mở và chạy `ScanApplication` trong IDE với Debugger**:
   Service ở IDE sẽ chiếm port `18102`. Các container Docker khác vẫn gọi sang `localhost:18102` bình thường.

---

### 🔵 Chế độ 3: Full Docker Mode (Vận Hành Staging / Test E2E)
Khi muốn kiểm tra toàn bộ đóng gói đóng vai trò như môi trường Production:

- **Build và khởi chạy toàn bộ**:
  ```powershell
  npm run docker:apps:build
  ```

- **Hạ toàn bộ container ứng dụng**:
  ```powershell
  npm run docker:apps:down
  ```

- **Hạ toàn bộ container hạ tầng**:
  ```powershell
  npm run docker:down
  ```

---

## 3. Chỉ Dẫn Dành Cho AI Agent

Khi AI Agent nhận task từ người dùng có liên quan đến Docker hoặc Debugging:

1. **Không bao giờ tự ý sửa port chuẩn trong `ADR-004`**.
2. **Phân định rõ phạm vi tệp Compose**:
   - `infra/compose/compose.yaml`: Chỉ sửa/thêm các dịch vụ hạ tầng (Postgres, Kafka, Redis, Observability).
   - `infra/compose/compose.apps.yaml`: Chỉ chứa các dịch vụ ứng dụng (5 BE microservices + FE V2).
3. **Khi hỗ trợ người dùng debug lỗi runtime**:
   - Nếu user đang ở Local Dev Mode: Khuyên user dùng `npm run docker:up` rồi khởi chạy app qua IntelliJ.
   - Nếu user muốn gỡ 1 container ứng dụng để mở IDE: Gợi ý dùng `npm run docker:stop <service_name>`.
4. **Kiểm tra cú pháp trước khi bàn giao**:
   ```powershell
   docker compose -f infra/compose/compose.yaml -f infra/compose/compose.apps.yaml --env-file .env.example config
   ```
