# 022 Durable scan run lease (BT-01) — Plan

Status: DONE
Design: [02-design.md](./02-design.md)
Manual Test Guide: [04-manual-testing-guide.md](./04-manual-testing-guide.md)

## Execution capsule

- Owner: `scan-service`
- Scope/files:
  - `apps/scan-service/src/main/resources/db/migration/V7__add_scan_run_durable_lease.sql`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/persistence/run/ScanRunEntity.java`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/adapter/out/persistence/run/ScanRunRepository.java`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/config/ScanProperties.java`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/exception/ScanLeaseExpiredException.java`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/ScanChunkCommitter.java`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/ScanExecutor.java`
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/scan/ScanService.java`
  - `apps/scan-service/src/test/java/com/filemngt/v2/scan/ScanIntegrationTest.java`
- Must preserve: REST API response contract, error handling cho missing root / Catalog unavailable, proposal/issue format.
- Read on demand: [BT-01](../../manual/learning/use-cases/scale-capacity/sc-01-scan-one-million-filesystem-entry/04-break-task.md#bt-01--durable-scan-run)

## Bước triển khai

1. **DB Migration**: Tạo `V7__add_scan_run_durable_lease.sql` bổ sung các cột `worker_id`, `lease_until`, `checkpoint_chunk`, `checkpoint_at`. (Đã hoàn tất)
2. **Entity & Repository**: Cập nhật `ScanRunEntity.java` ánh xạ các cột mới và thêm helper check lease / update progress checkpoint. (Đã hoàn tất)
3. **Properties & Exception**:
   - Thêm cấu hình lease duration trong `ScanProperties.java`. (Đã hoàn tất)
   - Tạo `ScanLeaseExpiredException.java`. (Đã hoàn tất)
4. **Chunk Committer**: Tạo `ScanChunkCommitter.java` với phương thức `@Transactional(propagation = Propagation.REQUIRES_NEW)` thực hiện commit chunk proposals/issues + update run checkpoint & lease trong 1 transaction riêng. (Đã hoàn tất)
5. **Executor & Service**:
   - Refactor `ScanExecutor.java` gọi `ScanChunkCommitter` theo batch chunk. (Đã hoàn tất)
   - Refactor `ScanService.java` gán `workerId` và `leaseUntil` khi tạo `ScanRunEntity`, đồng thời kiểm tra/thu hồi lease hết hạn. (Đã hoàn tất)
6. **Integration Verification**: Cập nhật và bổ sung test cases trong `ScanIntegrationTest.java` xác nhận lease lock & durable chunk checkpointing. (Đã hoàn tất)

## Kiểm tra

- Đã chạy `mvn test -am -pl apps/scan-service` sử dụng `corretto-25` SDK (21/21 integration tests thành công, 0 thất bại).

## Rollout và rollback

- Rollout: Chạy Flyway migration V7 và khởi động service mới.
- Rollback: Revert code về V6; các cột mới trong DB không ảnh hưởng đến tính tương thích ngược.

## Tài liệu cần cập nhật

- Cập nhật `docs/STATUS.md` ghi nhận feature 022 đã hoàn thành.
