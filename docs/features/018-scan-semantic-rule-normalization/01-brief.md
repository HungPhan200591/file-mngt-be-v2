# Feature Brief — FT018: Scan Semantic Rule Normalization

## Mục tiêu
Nâng cấp `scan-service` để tự động parse và chuẩn hóa semantic metadata (Studio Code, Actress, Title, Part, Tag) từ filename/foldername dựa trên Catalog REST Registry Snapshot thu thập từ FT019. Khi proposal được `APPROVE`, `scan-service` phát event `media.file.discovered.v2` mang đầy đủ payload semantic candidate để Catalog materialize canonical metadata.

## Phạm vi (Scope)
1. **Catalog Registry Integration**:
   - Sử dụng `RegistrySnapshot` (studioCodes, tags) lấy từ `catalog-service` qua REST API tại đầu mỗi `scan_run`.
2. **Semantic Parser Engine (`scan-service`)**:
   - **JOKE Profile**:
     - Format: `<actress> - [<baseCode>] [<part?>]` hoặc `Best of <actress> [<part?>]`.
     - Studio Code `Best of` → Code `BESTOF`, Name `Best Of`.
     - Ngoặc tròn `(...)` chứa Tag. Nếu Tag khớp Registry → map `semantic.tagNames`. Nếu Tag chưa có trong Registry → đưa vào `evidence.unrecognizedTags`.
     - Ngoặc vuông `[...]` chứa BaseCode và Part.
     - Part normalized: `A`, `B`, `PART 1`, `CD 1`... Mỗi `(baseCode, part)` đại diện cho một Subject độc lập `JOKE:<baseCode>:<part-or-_>`.
     - Asset Candidate Link: File `Best` (cover/image/video phụ) liên kết với `PRIMARY_VIDEO` tương ứng.
     - Studio Code Disambiguation: Nếu Studio Code map với nhiều Studio trong Registry → trả proposal `AMBIGUOUS`.
   - **USE Profile (`USE_VIDEO`, `USE_ASSET`, `USE_ALBUM`)**:
     - Format Strict: `<actress> - <title> - <studioCode>`. Mọi file/folder không khớp format strict này sẽ chuyển sang `PARTIAL`/`UNPARSEABLE`.
     - `USE_ALBUM`: Folder leaf name bắt buộc theo format strict. Các file ảnh trong folder album được gom trọn bộ vào album đó.
3. **Event Contract Upgrade**:
   - Khởi tạo Event contract `media.file.discovered.v2` mở rộng payload chứa candidate semantic: `baseCode`, `part`, `studioCode`, `actressNames`, `tagNames`.
   - Bổ sung `MediaFileDiscoveredConsumer` ở `catalog-service` hỗ trợ v2 event.

## Ngoài phạm vi (Out of Scope)
- Không tự động sinh Tag mới trong Catalog Registry khi gặp Tag lạ trong `(...)` (chỉ ghi nhận vào `unrecognizedTags` để User xem/tạo tại `metadata-admin`).
- Không scan hay query trực tiếp DB `catalog_db`.
