# 017 Scan semantic metadata extraction

Owner: `scan-service` / `scan_db`

## Mục tiêu

Nâng Scan từ việc chỉ tạo identity candidate sang lưu và trả evidence có cấu trúc, được trích xuất read-only từ relative path và filename. Evidence phục vụ review; Scan không ghi Catalog và không đọc database Catalog.

## Acceptance criteria

- Mỗi proposal trả `evidence` gồm parser version, filename/stem/extension, parent path, path segments và profile-specific identity evidence.
- `JOKE_*` giữ code trong bracket làm evidence; `USE_*` giữ normalized basename; `USE_ALBUM` giữ relative folder identity.
- Evidence được persist tại `scan_proposal`, không có absolute path, và reload proposal vẫn không mất dữ liệu.
- Không gán actress/studio/tag từ folder mơ hồ; những field đó chỉ xuất hiện khi extractor profile-specific có convention được chốt.

## Ngoài phạm vi

- Catalog lookup/write, event payload, metadata canonical, thumbnail/hash/duration/EXIF.
- Suy đoán actress/studio/tag hoặc đổi dữ liệu của các scan run cũ.
