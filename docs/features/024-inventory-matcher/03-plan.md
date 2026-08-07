# FT-024 — Inventory Matcher: Plan

## Status: DONE

## Completed: 2026-08-07
- Toàn bộ test của module scan-service xanh (BUILD SUCCESS)
- `spotless:apply` clean

## Execution Capsule

- **Owner**: `scan-service`
- **Scope files**: `domain/inventory`, `application/scan`, `adapter/out/persistence/inventory`, `test`
- **Must preserve**: Tất cả test cũ phải xanh. Ràng buộc Lease validation (`RUNNING`, `leaseUntil > now`, `workerId`) từ BT-01 phải được duy trì cho cả chunk commit và finalization `markMissing`. Không thay đổi REST API, không migration schema mới.
- **Read on demand**: `docs/architecture/03-CODING_RULES.md`.

## Bước triển khai

### Bước 1: Domain
- Tạo `ScanInventorySnapshot.java` record trong `domain/inventory`.

### Bước 2: Adapter — lookup + markMissing
- Thêm `findSnapshotsByRootKeyAndPaths` vào `ScanFileInventoryRepository` (JPQL projection).
- Thêm `markMissing(rootKey, currentRunId)` vào `ScanFileInventoryBatchWriter`.

### Bước 3: Application — matcher, committer & executor
- Tạo `ScanInventoryMatcher.java` với sealed `MatchResult`.
- Sửa `ScanChunkCommitter.java`: Thêm method `finalizeRun(runId, workerId, rootKey, finalProgress)` với `@Transactional(propagation = Propagation.REQUIRES_NEW)` và `validateLease(run, lease)` đóng gói cả `markMissing` và `complete`.
- Sửa `ScanExecutor.scanFiles`: lookup batch → classify → áp dụng tầng lọc 2 lớp (chỉ analyze file `NEW_OR_CHANGED` **và** được `ScanCandidateParser.supports(...)` chấp nhận).
- Sửa `ScanExecutor.execute`: ủy quyền hoàn tất đợt scan qua `chunkCommitter.finalizeRun(...)` để bảo vệ Lease an toàn.

### Bước 4: Test Matrix đầy đủ
- `inventoryMatcherSkipsUnchangedFileOnRescan()`: file không đổi → skip parse, proposal count giữ nguyên.
- `inventoryMatcherParsesModifiedFile()`: file sửa `fileSize`/`modifiedAt` → parse lại → tạo proposal mới.
- `inventoryMatcherMarksMissingFile()`: file xóa khỏi đĩa → đánh dấu `MISSING`.
- `inventoryMatcherUpsertsUnsupportedFileWithoutProposal()`: file không thuộc profile (như `.jpg`) → seed inventory `PRESENT` nhưng không tạo proposal.
- `ScanChunkCommitterTest.rejectsExpiredLeaseBeforeFinalization()`: lease hết hạn → không `markMissing`, không `complete` run.

### Bước 5: Verify
- `.\mvnw spotless:apply test -pl apps/scan-service`
- Toàn bộ test của module scan-service xanh (BUILD SUCCESS).

## Rollback
Nếu test đỏ: revert các file Java đã sửa. Schema DB không thay đổi.

## Verify
- Toàn bộ test của module scan-service xanh.
- `BUILD SUCCESS`, `spotless:check` pass.
