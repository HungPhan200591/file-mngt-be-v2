# Implementation Plan — FT018: Scan Semantic Rule Normalization

Status: DONE

---

## Execution Capsule

- **Owner**: `scan-service` & `catalog-service` & `platform/event-contracts`.
- **Scope Files**:
  - `platform/event-contracts/src/main/java/com/filemngt/v2/contracts/events/MediaFileDiscoveredV2.java` [NEW]
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/domain/ScanSemanticParser.java` [NEW]
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/ScanService.java` [MODIFY]
  - `apps/scan-service/src/main/java/com/filemngt/v2/scan/application/ScanDecisionService.java` [MODIFY]
  - `apps/catalog-service/src/main/java/com/filemngt/v2/catalog/adapter/in/event/MediaFileDiscoveredConsumer.java` [MODIFY]
  - `apps/catalog-service/src/main/java/com/filemngt/v2/catalog/application/CatalogFileDiscoveryService.java` [MODIFY]
- **Must Preserve**: Idempotent processing trong `MediaFileDiscoveredConsumer`, status status checks trong `ScanDecisionService`.

---

## Proposed Changes

### Phase 1: Event Contract V2 (`platform/event-contracts`)
1. **Tạo `MediaFileDiscoveredV2.java`**:
   - Thêm các thuộc tính candidate semantic: `baseCode`, `part`, `studioCode`, `actressNames`, `tagNames`.

### Phase 2: Semantic Parser Engine (`apps/scan-service`)
1. **Tạo `ScanSemanticParser.java`**:
   - Nhận filename/foldername và `RegistrySnapshot`.
   - Implement JOKE Parser: Tách `(...)` thành Tags/UnrecognizedTags, `[...]` thành BaseCode và Part (normalized `A`, `B`, `PART 1`...). Xử lý `Best of` -> Code `BESTOF`.
   - Implement USE Parser: Validate Strict Format `<actress> - <title> - <studioCode>`. Đánh dấu `PARTIAL` nếu vi phạm format.
   - Disambiguation check: Trả `isAmbiguous = true` nếu Studio Code map với nhiều hơn 1 studio.
2. **Cập nhật `ScanService.java`**:
   - Truyền `RegistrySnapshot` vào `ScanSemanticParser` trong quá trình scan file walk.
   - Lưu trữ đầy đủ `semantic` DTO và `unrecognizedTags` vào proposal `evidence`.
3. **Cập nhật `ScanDecisionService.java`**:
   - Phát event `media.file.discovered.v2` khi proposal được `APPROVE`.

### Phase 3: Catalog Consumer Materialization (`apps/catalog-service`)
1. **Cập nhật `MediaFileDiscoveredConsumer.java` & `CatalogFileDiscoveryService.java`**:
   - Lắng nghe `media.file.discovered.v2`.
   - Materialize canonical metadata từ semantic candidate payload.

---

## Verification Plan

### Automated Tests
- `ScanSemanticParserTest.java` (Unit tests cho tất cả các case JOKE/USE, Tag `(...)`, Part `[...]`, `Best of`, Code trùng, USE strict format).
- `ScanIntegrationTest.java` (Integration test kiểm tra end-to-end từ Preview -> Approval v2 Outbox).
- `CatalogIntegrationTest.java` (Integration test kiểm tra consumer v2 event materialize metadata vào catalog_db).
