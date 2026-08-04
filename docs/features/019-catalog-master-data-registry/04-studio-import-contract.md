# FT019 — Studio import contract

Source input có thể theo schema của [studios.json BE V1](D:/Study/Project/file_mngt/src/main/resources/json/studios.json). File này chỉ là input import do người dùng cung cấp; V2 không đọc BE V1 ở runtime và test dùng fixture V2 độc lập.

## Schema nguồn

```json
{
  "JOKE": [{ "studio": "<studio display name>", "code": ["<studio code>"] }],
  "USE": [{ "studio": "<studio display name>", "code": ["<studio code>"] }]
}
```

- Chỉ nhận hai key top-level `JOKE`, `USE`; mỗi row cần `studio` không rỗng và `code` là array string không rỗng.
- Mỗi row thuộc đúng region key đang chứa nó.
- `normalized_name`: trim, collapse whitespace, uppercase theo `Locale.ROOT`.
- `normalized_code`: trim, xóa whitespace, uppercase theo `Locale.ROOT`, giữ raw `code` để hiển thị.

## Endpoint và semantics

`POST /api/v2/master-data/imports/studios?dryRun=true|false` nhận nguyên JSON trên.

- `dryRun=true` là default: chỉ trả report, không ghi database và không tăng `registryVersion`.
- `dryRun=false`: chỉ apply khi toàn bộ payload valid và không có conflict; một transaction, không partial-write.
- Apply idempotent: Studio cùng `(region, normalized_name)` được reuse; Studio Code cùng `(region, normalized_code)` trỏ cùng Studio là no-op. `registryVersion` chỉ tăng khi có mutation thật.
- Source file phải được copy vào test fixture của V2; E2E/import test không đọc path BE V1.

## Rule merge và conflict

- Row Studio có cùng `(region, normalized_name)` được merge code vào cùng một Studio.
- Code lặp lại cho cùng Studio là no-op.
- Conflict là khi cùng `(region, normalized_code)` trỏ hai Studio khác nhau. Dry-run phải trả code và các Studio liên quan; apply trả `409` và không ghi gì.
- Người dùng sửa input hoặc CRUD Studio Code để chọn owner, rồi import lại. Không có rule chọn “row cuối”.

## Master data không có source JSON này

- Tag và Actress dùng CRUD hoặc fixture/import payload V2 do người dùng cung cấp; không hard-code dữ liệu V1 vào migration, Design hay test.
- `studios.json` chỉ là source schema cho Studio import, không phải source cho Tag hoặc Actress.
