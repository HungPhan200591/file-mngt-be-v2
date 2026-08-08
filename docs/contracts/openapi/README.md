# REST API contracts

Mỗi public API hoặc internal service boundary sở hữu một OpenAPI file versioned. Ghi request, response,
error, pagination và compatibility trước khi consumer được sửa.

Không dùng controller source code làm contract duy nhất.

Contract hiện hành: [Catalog API v1](./catalog-v1.yaml),
[Catalog Scan Existence Internal API v1](./catalog-scan-existence-v1.yaml),
[Catalog Master Data API v1](./catalog-master-data-v1.yaml) và [Scan API v1](./scan-v1.yaml).
Khi API đổi, sửa file owner trước hoặc cùng source code. Contract internal phải ghi rõ direct service
boundary và không mặc nhiên được route qua Gateway.
