# Hướng dẫn chạy Test (Unit, Integration & E2E)

Tài liệu hướng dẫn ngắn gọn cách chạy kiểm thử cho Backend V2 từ root project.

---

## 🧪 1. Java Unit & Integration Tests (Maven + Testcontainers)

Các câu lệnh dưới đây tự động nạp JDK `corretto-25` và chạy từ gốc dự án:

### `scan-service`
- **Chạy riêng `ScanIntegrationTest`** *(yêu cầu Docker Desktop cho Testcontainers)*:
  ```powershell
  npm run test:scan:it
  ```
- **Chạy toàn bộ test của `scan-service`**:
  ```powershell
  npm run test:scan
  ```

### Các Service khác
- **`catalog-service`**: `npm run test:catalog`
- **`query-service`**: `npm run test:query`
- **`gateway-service`**: `npm run test:gateway`
- **`media-worker-service`**: `npm run test:media`

### Toàn bộ dự án
- **Chạy tất cả unit & integration tests**:
  ```powershell
  npm run test:all
  ```

---

## 🌐 2. E2E HTTP Scenario Tests (httpyac)

Yêu cầu: Đã bật Docker Compose (`postgres`, `kafka`) và các service Spring Boot tương ứng.

- **Khởi tạo lần đầu**: `npm run e2e:init`
- **Chạy 1 lệnh duy nhất toàn bộ E2E**:
  ```powershell
  npm run e2e:all
  ```
- **Chạy theo từng Service**:
  - Scan Service: `npm run e2e:scan` *(In chi tiết: `npm run e2e:scan:debug`)*
  - Catalog Service: `npm run e2e:catalog`
  - Gateway Routing: `npm run e2e:gateway`
  - Query Cache: `npm run e2e:query:cache`
  - Media Delivery: `npm run e2e:media`
  - Observability: `npm run e2e:observability`
