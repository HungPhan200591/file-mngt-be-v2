-- PostgreSQL 18 cung cấp uuidv7() native. Default bảo đảm mọi insert không truyền
-- ID vẫn dùng khóa time-ordered; application cũng chuyển các ID tự sinh sang UUIDv7.

alter table scan_run
    alter column id set default uuidv7();

alter table scan_proposal
    alter column id set default uuidv7();

alter table scan_issue
    alter column id set default uuidv7();

alter table scan_file_inventory
    alter column id set default uuidv7();

alter table scan_outbox_event
    alter column id set default uuidv7();
