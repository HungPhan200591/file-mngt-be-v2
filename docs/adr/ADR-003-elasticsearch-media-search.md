# ADR-003: Elasticsearch cho media search

Status: ACCEPTED
Date: 2026-08-01

## Context

Gallery Web và Media Library cần full-text search, typo tolerance, autocomplete và filter nhanh trên metadata đã liên kết. PostgreSQL/Catalog vẫn phải là nguồn dữ liệu chuẩn, còn log data stream của ELK không phù hợp để phục vụ search nghiệp vụ.

## Decision

- `query-service` sở hữu Elasticsearch index `media-subject-*` như một read projection.
- Index được cập nhật từ Catalog/Worker event, tách logic và lifecycle khỏi logs data stream.
- Frontend chỉ gọi Query API; không gọi Elasticsearch trực tiếp.
- Search dùng full-text/fuzzy/autocomplete khi phù hợp; exact filter/order/pagination vẫn là contract của Query API.
- Index lỗi hoặc lag không làm mất dữ liệu chuẩn; Query trả tín hiệu degraded hoặc fallback đơn giản theo feature design.

## Alternatives

- Chỉ PostgreSQL search: dễ hơn nhưng ít cơ hội học search engine và giới hạn autocomplete/fuzzy search.
- Catalog query Elasticsearch trực tiếp: làm Catalog bị trộn write model với read/search concern.
- Tạo service Search riêng: chưa cần thiết; Query đã là read model owner.

## Consequences

- Cần index mapping, analyzer, rebuild procedure và đo index lag trong feature Query.
- Cùng Elasticsearch cluster local với ELK nhưng tách index/data stream, retention và quyền truy cập logic.
- Không đưa document index hoặc Elasticsearch client vào shared platform module.
