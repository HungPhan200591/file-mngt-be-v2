# 03-plan: 023 File inventory seed (BT-02)

## Execution capsule

- **Owner**: `scan-service`
- **Scope**:
  - `apps/scan-service/src/main/resources/db/migration/V8__add_scan_file_inventory.sql`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/persistence/inventory/*`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/*`
  - `apps/scan-service/src/test/java/com/filemngt/v2/scan/*`
- **Must preserve**:
  - Tương thích và hành vi scan hiện tại của `ScanExecutor` và `ScanChunkCommitter` (BT-01).
  - Đảm bảo transaction `REQUIRES_NEW` cho mỗi chunk commit.
- **Read on demand**:
  - `docs/features/022-durable-scan-run-lease/02-design.md`
  - `manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md`

## Status

`DONE`

## Proposed Changes

### Database Migration

#### [NEW] [V8__add_scan_file_inventory.sql](file:///d:/Study/Project/file_mngt_microservice/apps/scan-service/src/main/resources/db/migration/V8__add_scan_file_inventory.sql)
- Bổ sung Flyway migration `V8__add_scan_file_inventory.sql` tạo bảng `scan_file_inventory` và Unique Index `ux_scan_file_inventory_root_path` trên `(root_key, source_relative_path)`.

---

### Persistence Layer (`scan-service`)

#### [NEW] [ScanFileInventoryEntity.java](file:///d:/Study/Project/file_mngt_microservice/apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/persistence/inventory/ScanFileInventoryEntity.java)
- JPA Entity đại diện cho `scan_file_inventory`, triển khai `Persistable<UUID>` hỗ trợ batch upsert/save hiệu năng cao.

#### [NEW] [ScanFileInventoryRepository.java](file:///d:/Study/Project/file_mngt_microservice/apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/persistence/inventory/ScanFileInventoryRepository.java)
- Spring Data JPA Repository cho `ScanFileInventoryEntity`, bổ sung phương thức upsert hoặc custom query theo `(rootKey, sourceRelativePath)`.

---

### Application Layer (`scan-service`)

#### [MODIFY] [ScanChunkCommitter.java](file:///d:/Study/Project/file_mngt_microservice/apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/ScanChunkCommitter.java)
- Bổ sung `ScanFileInventoryRepository` và xử lý upsert danh sách `ScanFileInventoryEntity` trong cùng transaction `@Transactional(propagation = Propagation.REQUIRES_NEW)` của mỗi chunk.

#### [MODIFY] [ScanExecutor.java](file:///d:/Study/Project/file_mngt_microservice/apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/ScanExecutor.java)
- Thu thập metadata file vật lý (`fileSize`, `modifiedAt`) khi thực hiện `Files.walk()`, bổ sung `ScanFileInventoryEntity` vào buffer của từng chunk để chuyển sang `ScanChunkCommitter`.

---

### Verification Plan

#### Automated Tests
- Chạy `mvn test -pl apps/scan-service` từ root repo.
- Thêm integration test trong `apps/scan-service/src/test/java/com/filemngt/v2/scan/ScanIntegrationTest.java`:
  - Đợt 1: Quét root fixture nhỏ, kiểm tra bảng `scan_file_inventory` có đủ các bản ghi với `state = PRESENT` và `last_seen_run_id` của đợt 1.
  - Đợt 2: Quét lại cùng root fixture đó, kiểm tra không bị lỗi duplicate key, số lượng bản ghi trong `scan_file_inventory` giữ nguyên và `last_seen_run_id` được cập nhật sang `runId` của đợt 2.
