alter table scan_inventory_diff_stage
    add column is_new boolean;

update scan_inventory_diff_stage diff
set is_new = inventory.id is null
from scan_file_inventory inventory
where inventory.root_key = diff.root_key
  and inventory.source_relative_path = diff.source_relative_path;

update scan_inventory_diff_stage
set is_new = true
where is_new is null;

alter table scan_inventory_diff_stage
    alter column is_new set default false,
    alter column is_new set not null;
