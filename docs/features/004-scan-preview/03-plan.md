# 004 Scan preview — Plan

Status: READY
Design: [02-design.md](./02-design.md)

## Execution capsule

- Owner: `scan-service`, `scan_db`, Scan OpenAPI v1.
- Scope/files: Scan POM/config/source/test, Flyway Scan, `docs/contracts/openapi/scan-v1.yaml`, `tests/e2e/scan/`, `docs/STATUS.md`.
- Must preserve: read-only filesystem; no Catalog write/call, Kafka, outbox, approval or file mutation; port theo ADR-004; source dưới 500 dòng/file.
- Read on demand: [Design](./02-design.md), `apps/scan-service/CONTEXT.md`, [Scan OpenAPI](../../contracts/openapi/scan-v1.yaml), `docs/architecture/03-CODING_RULES.md`.

## Bước triển khai

1. Bổ sung Web, Validation, JPA, Flyway, PostgreSQL và Testcontainers cho Scan; cấu hình root registry local qua `scan.roots`.
2. Tạo Flyway `scan_run`, `scan_proposal`, `scan_issue`, index theo run/path và guard một run `RUNNING` trên root.
3. Cài domain profile/run status/proposal/issue; parser Strategy + Registry theo root profile; parse ambiguity thành issue.
4. Cài application background run, API create/get/list theo OpenAPI và Problem Details.
5. Viết Testcontainers integration cho migration, root missing, duplicate running root, JOKE/USE proposal, issue và pagination.
6. Thêm fixture filesystem + E2E `.http`; chạy kiểm tra được cho phép, rồi chuyển Plan `DONE` và cập nhật status.

## Kiểm tra

- Static: OpenAPI parse, migration ownership/naming, parser không đi ra ngoài root, source line cap và whitespace.
- Khi được phép: `./mvnw test -pl apps/scan-service -am`; E2E chạy với fixture root local đã cấu hình, không dùng thư viện media thật.
- Kiểm tra `scan_db` chỉ chứa object Scan; scan không mở Kafka connection, không truy cập Catalog và không đổi filesystem.

## Rollout và rollback

- Rollout local chỉ sau khi user cấu hình explicit fixture root. Không tự scan source media thật.
- Rollback code bằng revert feature commit; không xóa `scan_db`/volume nếu chưa có yêu cầu rõ ràng.

## Source-of-truth audit

- Thêm Scan OpenAPI v1; ownership Scan hiện có không đổi, không cần ADR/event contract.
