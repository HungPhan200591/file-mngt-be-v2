# 5. FT013 Primer: Media Worker Processing Foundation

> 📌 **Tài liệu Nguồn chuẩn Feature 013 (SSOT)**:
> Đây là bản tóm tắt dành cho người mới đọc. Để xem chi tiết spec kỹ thuật chính thức của FT013, tham khảo:
> - 📄 **[FT013 Brief & Context](../../../docs/features/013-media-worker-processing-foundation/01-brief.md)**
> - 📄 **[FT013 Design & Contract](../../../docs/features/013-media-worker-processing-foundation/02-design.md)**
> - 📄 **[FT013 Implementation Plan](../../../docs/features/013-media-worker-processing-foundation/03-plan.md)**

## Trước FT013 hệ thống đang thiếu gì?

Catalog biết rằng asset tồn tại và biết locator của file, nhưng chưa biết metadata kỹ thuật của file đó.

Ví dụ hiện tại:

```json
{
  "assetId": "...",
  "role": "PRIMARY_VIDEO",
  "storageKey": "fixture-joke-video",
  "relativePath": "A - [JOKE-001].mp4"
}
```

Sau FT013, asset có thêm dữ liệu tương tự:

```json
{
  "contentLength": 123456789,
  "mediaType": "video/mp4",
  "sourceLastModifiedAt": "2026-08-01T10:00:00Z",
  "technicalMetadataVersion": 1
}
```

## FT013 sẽ code những phần nào?

### Catalog phát job

Khi Catalog thực sự thêm asset mới có `storageKey`, transaction cũng ghi processing request vào outbox. Publisher gửi event `media.processing.requested.v1` qua Kafka.

Asset trùng hoặc event Scan trùng không được tạo thêm job logic khác.

### Worker xử lý job

Worker nhận `assetId`, `storageKey`, `relativePath` và metadata version:

1. Resolve root từ local registry.
2. Kiểm tra path không thoát root và file là regular file.
3. Đọc file attributes bằng Java NIO.
4. Xác định MIME với resolver deterministic.
5. Publish `media.processing.completed.v1` sau khi Kafka ack.

Worker chỉ đọc file; không sửa, rename hay tạo artifact trong FT013.

### Catalog áp dụng kết quả

Catalog consumer:

1. Dedupe completion event.
2. Kiểm tra đúng Subject/Asset và metadata version.
3. Ghi metadata vào `media_asset`.
4. Tăng Subject version.
5. Ghi `media.subject.changed.v1` snapshot vào outbox cùng transaction.

### Query hội tụ

Query nhận snapshot mới, cập nhật PostgreSQL/search projection/cache DTO và trả metadata nullable qua API.

## Vì sao Worker không cần database?

FT013 chỉ đọc thuộc tính file, nên xử lý lại cùng job không gây tác dụng phụ. Completion ID được tạo ổn định và Catalog dedupe.

Database Worker chỉ đáng cân nhắc khi job có lifecycle/artifact phức tạp cần resume độc lập. Thumbnail/GIF/hash chưa nằm trong feature này nên chưa thêm state sớm.

## Vì sao metadata thuộc Catalog thay vì Query/Worker?

- Worker là nơi đo metadata, không phải owner business của Asset.
- Query là bản sao đọc, không phải canonical source.
- Catalog sở hữu Asset nên giữ kết quả chuẩn và phát snapshot cho consumer khác.

Đây là khác biệt giữa “ai tạo ra dữ liệu” và “ai sở hữu dữ liệu”.

## Điều FT013 không làm

- Không tách Actress/Studio/Tag.
- Không liên kết USE Album với Syncdroid.
- Không làm thumbnail/GIF/hash.
- Không dùng ffprobe để lấy duration, codec hoặc resolution.
- Không làm UI.
- Không backfill asset cũ thiếu `storageKey`.

## Ví dụ flow với fixture

```text
1. Scan thấy A - [JOKE-001].mp4
2. Approve → Catalog tạo Subject JOKE/VIDEO/JOKE-001 và Asset
3. Catalog outbox gửi processing request theo assetId
4. Worker đọc fixture, ví dụ size = 4 byte và MIME = video/mp4
5. Worker gửi completion
6. Catalog lưu metadata và phát Subject snapshot version mới
7. Query cập nhật projection
8. E2E gọi Query và thấy metadata
```

## Đường đọc code hiện tại

Đọc theo flow này, không đọc toàn bộ package:

1. [ScanService](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/application/ScanService.java): file thành Proposal/Issue như thế nào.
2. [ScanDecisionService](../../../apps/scan-service/src/main/java/com/filemngt/v2/scan/application/ScanDecisionService.java): approve thành outbox event.
3. [CatalogFileDiscoveryService](../../../apps/catalog-service/src/main/java/com/filemngt/v2/catalog/application/CatalogFileDiscoveryService.java): event thành Subject/Asset.
4. [CatalogSubjectOutboxService](../../../apps/catalog-service/src/main/java/com/filemngt/v2/catalog/application/CatalogSubjectOutboxService.java): canonical state thành snapshot.
5. [QueryProjectionService](../../../apps/query-service/src/main/java/com/filemngt/v2/query/application/QueryProjectionService.java): snapshot thành read model.
6. [MediaContentService](../../../apps/media-worker/src/main/java/com/filemngt/v2/mediaworker/application/MediaContentService.java) và [MediaRootResolver](../../../apps/media-worker/src/main/java/com/filemngt/v2/mediaworker/adapter/out/filesystem/MediaRootResolver.java): Worker đang resolve file an toàn ra sao.

Hai tài liệu feature cần đọc sau khi hiểu flow:

- [FT013 Brief](../../../docs/features/013-media-worker-processing-foundation/01-brief.md).
- [FT013 Design](../../../docs/features/013-media-worker-processing-foundation/02-design.md).

## Checklist hiểu trước khi cho AI code

Bạn đã sẵn sàng triển khai khi trả lời được các câu sau:

1. Subject khác Asset ở điểm nào?
2. Vì sao Scan không ghi thẳng Catalog?
3. Vì sao Catalog và Query lưu dữ liệu gần giống nhau?
4. Outbox tránh mất event như thế nào?
5. Vì sao event có thể bị giao lại và consumer phải idempotent?
6. Vì sao Worker đo metadata nhưng Catalog lại sở hữu kết quả?
7. FT013 làm gì và cố ý chưa làm gì?

Nếu một câu chưa rõ, dừng ở câu đó và đối chiếu chương tương ứng; chưa cần đọc implementation plan chi tiết.
