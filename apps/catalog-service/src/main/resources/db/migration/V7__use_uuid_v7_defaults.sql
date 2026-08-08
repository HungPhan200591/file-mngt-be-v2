-- PostgreSQL 18 cung cấp uuidv7() native. Các default này chỉ áp dụng cho
-- insert không truyền ID; event/identifier do application truyền vào giữ nguyên.

alter table media_subject
    alter column id set default uuidv7();

alter table media_asset
    alter column id set default uuidv7();

alter table catalog_outbox_event
    alter column id set default uuidv7();

alter table catalog_dead_letter_event
    alter column id set default uuidv7();

alter table studio
    alter column id set default uuidv7();

alter table studio_code
    alter column id set default uuidv7();

alter table tag
    alter column id set default uuidv7();

alter table actress
    alter column id set default uuidv7();

alter table master_data_import
    alter column id set default uuidv7();
