-- PostgreSQL 18 cung cấp uuidv7() native. Projection/processed-event nhận ID
-- từ Catalog/Kafka vẫn truyền ID tường minh; default chỉ là fallback cho insert
-- không truyền ID.

alter table query_media_subject
    alter column id set default uuidv7();

alter table query_media_asset
    alter column id set default uuidv7();

alter table query_search_outbox
    alter column id set default uuidv7();
