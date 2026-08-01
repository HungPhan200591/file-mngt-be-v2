# REST API contracts

Mỗi service public sở hữu một OpenAPI file versioned. Ghi request, response, error, pagination và compatibility trước khi consumer được sửa.

Không dùng controller source code làm contract duy nhất.

Contract hiện hành: [Catalog API v1](./catalog-v1.yaml), [Scan API v1](./scan-v1.yaml). Khi API đổi, sửa file owner trước hoặc cùng source code.
