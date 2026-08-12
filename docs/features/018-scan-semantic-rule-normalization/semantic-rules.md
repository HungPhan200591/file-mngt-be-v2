# FT018 — Semantic Rulebook

Status: CHỐT — Source of truth duy nhất cho rule semantic và parser normalization của FT018.

---

## 1. Rule Chung & Boundary

| Trạng thái | Rule |
| --- | --- |
| CHỐT | Semantic chỉ được parse từ filename/foldername và Catalog REST Registry snapshot; folder chỉ dùng để chọn `ScanProfile`. |
| CHỐT | Raw evidence giữ nguyên. Parser chỉ loại bỏ duplicate counter ở cuối filename (ví dụ ` (1)`) trên bản parse semantic. |
| CHỐT | Khi không đủ bằng chứng hoặc vi phạm format: Tạo proposal `PARTIAL`/`AMBIGUOUS`/`UNPARSEABLE` kèm warning/issue chi tiết, không tự đoán. |
| CHỐT | Catalog là owner duy nhất của Studio, Studio Code, Tag, Actress. Scan chỉ dùng REST snapshot read-only từ Catalog cho mỗi run. |

---

## 2. Tag & Token Policy

| Trạng thái | Rule |
| --- | --- |
| CHỐT | Tag syntax: Tag chỉ nằm trong ngoặc tròn `(...)`, **KHÔNG** nằm trong ngoặc vuông `[...]`. Ngoặc vuông `[...]` dành riêng cho Studio Code / Part. |
| CHỐT | Nếu token trong `(...)` khớp với `Tag.normalized_name` trong Registry snapshot (case-insensitive) → Parse thành `semantic.tagNames`. |
| CHỐT | Nếu token trong `(...)` chưa có trong Catalog Tag Registry → Đưa vào `evidence.unrecognizedTags` để User review và khởi tạo Tag mới tại FE `metadata-admin`. **Không tự động biến token lạ thành Tag mới**. |
| CHỐT | Tag `Best`: Hệ thống hỗ trợ candidate asset link để gộp asset `Best` (cover/image/video phụ) vào `PRIMARY_VIDEO` tương ứng, mặc dù bản thân file `Best` vẫn có thể đứng độc lập như một primary video. |
| CHỐT | Khi Catalog materialize nhiều file cùng subject, tag cấp subject chỉ lấy từ event có role `PRIMARY_VIDEO`; asset phụ không có tag không được xóa tag đã phát hiện từ video chính. |

---

## 3. Disambiguation & Studio Policy

| Trạng thái | Rule |
| --- | --- |
| CHỐT | Rule `Best of`: Xử lý thành Studio Code = `BESTOF` và Studio Name = `Best Of`. |
| CHỐT | Studio Code bị trùng/mơ hồ: Nếu 1 Studio Code (ví dụ `FSDSS`) map với nhiều hơn 1 Studio trong Registry → Trả proposal `AMBIGUOUS` kèm danh sách các Studio nghi vấn để User chọn owner khi review. |
| CHỐT | Longest Unique Match: Khi filename chứa nhiều token khớp studio code, ưu tiên match token có độ dài lớn nhất và duy nhất. |

---

## 4. JOKE Region Rules

| Profile | Trạng thái | Rule |
| --- | --- | --- |
| `JOKE_VIDEO` | CHỐT | Format chuẩn: `<actress> - [<baseCode>] [<part?>]` hoặc `Best of <actress> [<part?>]`. Tách tag trong `(...)`. |
| `JOKE_VIDEO` | CHỐT | Mỗi `(baseCode, part)` đại diện cho 1 subject riêng. `[TEST-001] A` và `[TEST-001] B` không gộp chung. |
| `JOKE_VIDEO` | CHỐT | `matchKey = JOKE:<baseCode>:<part-or-_>`; Video và Asset chỉ liên kết khi `matchKey` khớp hoàn toàn. |
| `JOKE_ASSET` | CHỐT | Chỉ lấy code, part và tag khi được mã hóa rõ ràng trong filename; không tự coi tên file asset mơ hồ là title hay actress. |

---

## 5. USE Region Rules

| Profile | Trạng thái | Rule |
| --- | --- | --- |
| `USE_*` | CHỐT | Format strict duy nhất: `<actress> - <title> - <studioCode>`. Mọi file/folder không đúng format này sẽ xếp vào nhóm phân loại xử lý sau (`PARTIAL`/`UNPARSEABLE`). |
| `USE_VIDEO` | CHỐT | Bắt buộc tách đúng 3 thành phần `<actress>`, `<title>`, `<studioCode>`. Studio Code phải resolve thành công và duy nhất. |
| `USE_ASSET` | CHỐT | Chỉ parse semantic khi filename tự thỏa mãn format strict; nếu không chỉ giữ basename/link key (`PARTIAL`). |
| `USE_ALBUM` | CHỐT | Leaf folder name **bắt buộc** phải tuân thủ format strict `<actress> - <title> - <studioCode>`. Tên của các file ảnh bên trong folder album không quan trọng (chỉ cần là định dạng ảnh hợp lệ) và sẽ được thu thập trọn bộ vào album đó. |

---

## 6. Canonical Handoff & Event Contract

| Trạng thái | Rule |
| --- | --- |
| CHỐT | Scan chỉ lưu trữ candidate kết quả parse tại `scan_proposal.evidence` và không trực tiếp ghi vào database Catalog. |
| CHỐT | Khi User bấm `APPROVE` một Proposal: `scan-service` phát event `media.file.discovered.v2` (hoặc nâng cấp payload) mang đầy đủ thông tin semantic candidate (`baseCode`, `part`, `studioCode`, `actressNames`, `tagNames`) để Catalog materialize và upsert canonical metadata. |
