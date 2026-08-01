# E2E HTTP harness

## Mục đích

- OpenAPI trong `docs/contracts/openapi/` là contract source of truth.
- File `.http` trong thư mục này là source of truth cho kịch bản E2E: chạy được bằng IntelliJ và bằng Agent/CLI.
- Postman chỉ dùng để khám phá thủ công hoặc import OpenAPI; không duy trì Postman collection song song.

## Chuẩn bị một lần

```powershell
Copy-Item http-client.env.example.json http-client.env.json
npm install
```

`http-client.env.json` là local-only và bị gitignore. Biến của môi trường `local` phải nằm trong block `local`; không đặt secret vào file `*.example.json` hay request đã commit.

## Chạy Catalog E2E

1. Khởi động PostgreSQL Compose và `catalog-service` bằng IntelliJ; kiểm tra `http://localhost:18101/actuator/health/readiness` trả `UP`.
2. Tại `tests/e2e/`, chạy:

```powershell
npm run catalog:local
```

Hoặc mở file `.http` trong IntelliJ và chạy từng request. Agent dùng cùng lệnh trên; có thể lọc một request theo tên:

```powershell
npx httpyac send catalog/001-subject-lifecycle.http --name CreateSubject --env local
```

Trong IntelliJ, chọn environment `local` ở HTTP Client trước khi chạy request để nạp `catalogBaseUrl`.

`CreateSubject` lưu `catalogSubjectId` và `catalogSubjectIdentity` vào HTTP Client global storage. Vì vậy sau khi chạy Create một lần, bạn có thể chạy riêng `GetCreatedSubject` hoặc `RejectDuplicateIdentity`. Nếu mở IntelliJ/clear HTTP Client storage mới, chạy Create lại trước.

## Chạy Scan E2E

Trước khi chạy, cấu hình Scan local trỏ đúng fixture repository:

```powershell
$env:SCAN_ROOTS_0_KEY = 'fixture-joke-video'
$env:SCAN_ROOTS_0_PATH = 'D:\Study\Project\file_mngt_microservice\tests\fixtures\scan\joke-video'
$env:SCAN_ROOTS_0_PROFILE = 'JOKE_VIDEO'
```

Chạy lại `scan-service` từ IntelliJ trong terminal đã có ba biến trên, sau đó tại `tests/e2e/` chạy `npm run scan:local`. Với IntelliJ, chạy `StartScanPreview` trước rồi chạy riêng các request còn lại; đợi run thành `COMPLETED` trước khi review proposals/issues.

## Quy ước viết kịch bản

- Mỗi API owner có thư mục riêng; đánh số theo scenario, ví dụ `catalog/001-subject-lifecycle.http`.
- Dùng dữ liệu có tiền tố `E2E-` và built-in variable như `{{$timestamp}}` để sinh identity; không dùng JavaScript block đầu file.
- Dùng response handler JetBrains `> {% client.test(...); %}` cho status và dữ liệu quan trọng; không dùng assertion `??` riêng của httpYac. Create/đổi API phải có success, validation và conflict/not-found nếu contract có.
- Chỉ chạy khi người dùng đã chủ động khởi động runtime. Catalog hiện không có delete API, nên scenario create để lại dữ liệu E2E trong `catalog_db` local.
- Khi REST contract đổi: cập nhật OpenAPI trước, sau đó cập nhật đúng file `.http` bị ảnh hưởng.
