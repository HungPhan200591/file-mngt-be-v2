# `media.file.removed.v1`

- Producer: Scan Service, chỉ sau khi người dùng approve proposal `DELETE_ASSET`.
- Consumer: Catalog Service.
- Partition key: `storageKey:relativePath`.
- Idempotency key: `eventId`; locator `(storageKey, relativePath)` là global asset locator.
- Delivery: at-least-once; locator không còn trong Catalog được xử lý như no-op thành công.

## Payload

`eventId`, `eventType`, `occurredAt`, `scanId`, `proposalId`, `storageKey`, `relativePath`.

Event không chứa `subjectId`: Scan không sở hữu canonical identity; Catalog tự resolve locator trong database của mình.
