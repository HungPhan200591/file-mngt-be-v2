# 019 Catalog master data registry

Owner: `catalog-service` / `catalog_db`; consumer: `scan-service` qua REST snapshot.

## Vấn đề

Catalog được quy định là owner của Actress, Studio và Tag nhưng hiện chưa có schema, import hay CRUD. Scan vì vậy không có registry chuẩn để resolve semantic từ filename. FT018 chỉ có thể parse evidence, chưa được triển khai cho đến khi master data sẵn sàng.

## Mục tiêu

Xây đủ master data tối thiểu để bắt đầu full scan: import an toàn, CRUD cơ bản, studio-code registry versioned và một REST snapshot để Scan lấy một bản dữ liệu immutable tại lúc bắt đầu run.

## Acceptance criteria

- `catalog_db` sở hữu Studio/Studio Code, Tag, Actress và registry version.
- Studio và Studio Code thuộc đúng một `region` (`JOKE` hoặc `USE`); Tag là global, Actress thuộc `region`.
- Có import JSON với `dryRun` mặc định, validate toàn bộ trước apply và không overwrite/đoán conflict.
- Studio import tương thích schema `studios.json` theo [import contract](./04-studio-import-contract.md); Tag và Actress dùng CRUD hoặc fixture/import payload V2 do người dùng cung cấp.
- Có CRUD cơ bản, pagination/search và enable/disable cho Studio, Tag, Actress và Studio Code.
- `GET` registry theo region trả `registryVersion`, Studio Code của region đó và Tag global active; không trả filesystem path hay data không cần cho parser.
- Scan lấy snapshot qua REST trước khi tạo run; lỗi Catalog/registry khiến request scan fail rõ ràng, không tạo run parse thiếu registry.
- Một run ghi `registryVersion` đã dùng; snapshot thay đổi sau đó không làm proposal trong run đổi nghĩa.
- Có integration test Catalog và E2E HTTP Scan chứng minh import/CRUD → snapshot → start scan.

## Ngoài phạm vi

- Parse semantic, thay đổi `media.file.discovered.v1`, materialize metadata vào subject hoặc Query projection: thuộc FT018 sau FT019.
- Fuzzy matching/AI, import production tự chạy, backfill scan cũ và frontend quản trị hoàn chỉnh.
