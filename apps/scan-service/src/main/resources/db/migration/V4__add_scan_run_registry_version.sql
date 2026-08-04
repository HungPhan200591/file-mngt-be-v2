-- FT019: Thêm registry_version vào scan_run
-- Nullable: run cũ (trước FT019) có registry_version = NULL

alter table scan_run add column registry_version bigint;
