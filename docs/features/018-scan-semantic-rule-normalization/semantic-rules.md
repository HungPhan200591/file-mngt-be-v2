# FT018 — Semantic Rulebook

Status: DRAFT — đây là source of truth duy nhất cho rule semantic của FT018. Chỉ tạo Design/Plan khi mọi mục `CẦN CHỐT` đã được quyết định.

## Cách đọc

- `CHỐT`: được phép đưa vào Design/Plan/code.
- `CẦN CHỐT`: là proposal, chưa được code.

## Rule chung

| Trạng thái | Rule |
| --- | --- |
| CHỐT | Semantic chỉ đến từ filename và registry; folder chỉ chọn `ScanProfile`. |
| CHỐT | Raw evidence giữ nguyên. Parser chỉ bỏ duplicate counter cuối như ` (1)` trên bản parse. |
| CHỐT | Khi không đủ bằng chứng: tạo proposal `PARTIAL`/`AMBIGUOUS` cùng warning, không đoán. |
| CHỐT | Tag filename được resolve thành `semantic.tagNames` qua registry có canonical spelling/alias, ví dụ `4K` → `4k`. |
| CẦN CHỐT | Token parenthesized không có trong registry: review hay tự tạo canonical tag. |

## Parsing registry

`registry` không phải JSON runtime của Scan. Studio/Tag canonical do `catalog-service` sở hữu trong `catalog_db`; Scan chỉ giữ projection read-only phục vụ parse, không đọc BE V1 khi runtime và không query `catalog_db`.

| Registry | Owner/source of truth | Seed ban đầu | Dùng để làm gì |
| --- | --- | --- | --- |
| Studio + studio code | Catalog tables `studio`, `studio_code` | `studios.json` V1, sau khi review code trùng | Map unique code/prefix → studio candidate; phát hiện ambiguity |
| Tag + tag alias | Catalog tables `tag`, `tag_alias` | Tag V1: `Best of`, `4k`, `Best`, `Uncensored`, `Sharpness`, `Collection`, `Cover` | Canonical spelling, alias và parser syntax |

Catalog phát versioned registry snapshot event; Scan consume idempotently vào local projection, ví dụ `scan_parser_studio_code` và `scan_parser_tag_alias`. Snapshot mang `registryVersion`; parser ghi version đã dùng vào evidence. Thay đổi registry cần review, outbox và fixture/test tương ứng.

Catalog vẫn là owner duy nhất của Studio/Tag canonical. Khi có canonical handoff sau approval, Catalog validate/upsert theo contract event riêng; Scan chỉ gửi candidate đã parse.

## JOKE

| Profile | Trạng thái | Rule |
| --- | --- | --- |
| `JOKE_VIDEO` | CHỐT | Nhận `<actress> - [<baseCode>] [<part?>]` và `Best of <actress> [<part?>]`; tách suffix tag đã đăng ký. |
| `JOKE_VIDEO` | CHỐT | Mỗi `(baseCode, part)` là một subject riêng. `[TEST-001] A` và `[TEST-001] B` không được gộp. |
| `JOKE_VIDEO` | CHỐT | `matchKey = JOKE:<baseCode>:<part-or-_>`; video/asset chỉ liên kết khi toàn bộ pair khớp. |
| `JOKE_ASSET` | CHỐT | Chỉ lấy code, part và tag khi chúng được mã hóa trong filename; không coi `Cover` hay tên asset mơ hồ là actress/title. |
| `JOKE_*` | CẦN CHỐT | `Best of` chỉ là tag, hay đồng thời là `studioName` để tương thích V1. |
| `JOKE_*` | CẦN CHỐT | Code studio trùng registry (`FSDSS`, `MIST`): giữ `AMBIGUOUS` hay có rule disambiguation được xác nhận. |

## USE

| Profile | Trạng thái | Rule |
| --- | --- | --- |
| `USE_VIDEO` | CẦN CHỐT | Parse format strict `<actress> - <title> - <studioCode>`; studio chỉ resolve khi code unique. |
| `USE_ASSET` | CẦN CHỐT | Chỉ semantic hóa khi filename tự khớp format strict; basename/link key đơn lẻ giữ `PARTIAL`. |
| `USE_ALBUM` | CẦN CHỐT | Identity là relative folder; chỉ parse semantic khi leaf folder khớp format USE strict. |
| `USE_*` | CẦN CHỐT | Khi nhiều studio code xuất hiện: chọn longest unique match hay trả `AMBIGUOUS`. |

## Canonical handoff

| Trạng thái | Rule |
| --- | --- |
| CHỐT | FT018 chỉ persist/trả semantic candidate ở `scan_proposal.evidence`; Scan không ghi Catalog. |
| CẦN CHỐT | Registry projection cần event snapshot riêng từ Catalog; không nhét registry vào `media.file.discovered.v1`. |
| CẦN CHỐT | Nếu `APPROVE` phải materialize tag/semantic canonical, tạo event version mới mang `baseCode`, `part`, tag đã review và metadata candidate. `media.file.discovered.v1` hiện không đủ payload. |

## Điều kiện sang Design

1. Chốt policy token tag lạ.
2. Chốt policy `Best of` và code studio trùng.
3. Chốt precedence USE khi nhiều studio code xuất hiện.
4. Chốt registry snapshot event và local Scan projection trong FT018 hay feature contract tiếp theo.
5. Chốt có hay không canonical handoff trong FT018 hay feature contract tiếp theo.
