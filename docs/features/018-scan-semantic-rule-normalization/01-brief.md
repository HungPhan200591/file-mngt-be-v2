# 018 Scan semantic rule normalization

Owner: `scan-service` / `scan_db`  
Predecessor: [FT017 — Scan semantic metadata extraction](../017-scan-semantic-metadata-extraction/01-brief.md)
Status: PENDING — chờ [FT019 — Catalog master data registry](../019-catalog-master-data-registry/03-plan.md) hoàn tất.
Rule source of truth: [semantic-rules.md](./semantic-rules.md)

## Vấn đề

FT017 đã persist evidence cấu trúc nhưng chủ động để semantic ở `PARTIAL`: `actressNames`, `studioName` và `tagNames` chưa được suy ra. BE V1 có các parser JOKE/USE và registry studio, nhưng có fallback mơ hồ, registry trùng code và không dùng folder để suy luận semantic. Cần chốt rule deterministic trước khi đưa vào V2.

## Mục tiêu

Chuẩn hóa rule semantic theo từng `ScanProfile`, chỉ trích xuất dữ liệu được mã hóa rõ trong filename hoặc registry versioned. Proposal trả evidence reviewable gồm giá trị semantic, parser/rule version và warning khi không đủ bằng chứng hoặc có ambiguity.

## Acceptance criteria

- Có rule matrix đã được duyệt cho `JOKE_VIDEO`, `JOKE_ASSET`, `USE_VIDEO`, `USE_ASSET` và `USE_ALBUM`: input, precedence, output semantic, warning và ví dụ.
- Studio registry có owner/version, phát hiện code trùng thay vì chọn theo thứ tự nạp.
- Normalization, tag và duplicate counter có semantics rõ; raw evidence không bị ghi đè. Tag filename được resolve qua `normalized_name` case-insensitive; token lạ không tự tạo canonical tag.
- Với JOKE, mỗi cặp `(baseCode, part)` là một subject riêng; code đơn lẻ không được dùng để ghép proposal/video/asset hoặc làm canonical identity.
- Không rule nào suy diễn actress/studio/tag chỉ từ folder cluster; folder chỉ chọn `ScanProfile`.
- Khi rule không khớp hoặc ambiguous, proposal vẫn được tạo để review với `PARTIAL`/`AMBIGUOUS`, không có canonical write, Catalog lookup hay event change.
- Design, Plan và code chỉ bắt đầu sau khi discovery được chốt.

## Ngoài phạm vi

- Catalog lookup/write, versioning canonical DTO/event, migration/backfill scan run cũ.
- Suy đoán bằng AI/fuzzy matching, metadata kỹ thuật, thumbnail/GIF/hash và đổi filesystem.
- Sao chép fallback thiếu an toàn hoặc hành vi map-override của BE V1.
