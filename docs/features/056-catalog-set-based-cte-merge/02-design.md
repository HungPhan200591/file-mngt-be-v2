# FT-056 — BT-09D2 Catalog Set-Based CTE Merge — Design

Owner: `catalog-service`
Brief: [01-brief.md](./01-brief.md)

## High Level Design

Đây là tối ưu SQL merge bên trong function finalizer hiện có: giữ page, fence và canonical writes;
bỏ per-page DDL storm. Worker Java `CatalogOperationFinalizer` (claim một page rồi `release`) không đổi
— đó là BT-09D3.

### Kiến trúc hiện tại (As-Is)

```mermaid
flowchart LR
    subgraph APP["App engine"]
        direction TB
        W["Finalizer worker"] --> CLAIM["Claim one page"]
    end
    subgraph DB["catalog_db"]
        direction TB
        DDL["Temp DDL storm"] --> HASH["JSON before_hash"]
        HASH --> MERGE["Canonical writes"]
        MERGE --> OUT[/"Outbox checkpoint"/]
    end
    CLAIM --> DDL
    style APP fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style W fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style CLAIM fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style DB fill:#4A148C,stroke:#fff,stroke-width:2px,color:#fff
    style DDL fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style HASH fill:#E91E63,stroke:#fff,stroke-width:2px,color:#fff
    style MERGE fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style OUT fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
```

| Nút | Vai trò |
| --- | --- |
| Finalizer worker | 4 physical worker, claim 64 logical lane |
| Claim one page | `acquire` → `catalog_finalize_operation_page` → `release` |
| Temp DDL storm | V19: 7 temp table, 4 index phụ, 5 `ANALYZE` mỗi page |
| JSON before_hash | `md5(catalog_subject_state_json)` cho subject đã có |
| Canonical writes | Insert/update subject, asset, tag, actress, primary |
| Outbox checkpoint | Snapshot v2 + workset + lane cursor trong cùng transaction |

4–8 worker song song tạo DDL trên `pg_class`/`pg_type` → catalog lock, không phải thời gian merge thuần.

### Kiến trúc đích (To-Be)

```mermaid
flowchart LR
    subgraph APP["App engine"]
        direction TB
        W["Finalizer worker"] --> CLAIM["Claim one page"]
    end
    subgraph DB["catalog_db"]
        direction TB
        CTE[/"In-query CTE"/]
        CTE --> NEW["Skip hash if new"]
        CTE --> OLD["Hash if exists"]
        NEW --> WRITE["Set-based merge"]
        OLD --> WRITE
        WRITE --> OUT[("Outbox checkpoint")]
    end
    CLAIM --> CTE
    style APP fill:#263238,stroke:#fff,stroke-width:2px,color:#fff
    style W fill:#FF9800,stroke:#fff,stroke-width:2px,color:#fff
    style CLAIM fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style DB fill:#4A148C,stroke:#fff,stroke-width:2px,color:#fff
    style CTE fill:#4CAF50,stroke:#fff,stroke-width:2px,color:#fff
    style NEW fill:#00BCD4,stroke:#fff,stroke-width:2px,color:#fff
    style OLD fill:#2196F3,stroke:#fff,stroke-width:2px,color:#fff
    style WRITE fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
    style OUT fill:#9C27B0,stroke:#fff,stroke-width:2px,color:#fff
```

| Nút | Vai trò |
| --- | --- |
| In-query CTE | Workset page, latest event, asset, primary, metadata, snapshot — không DDL |
| Skip hash if new | Subject mới luôn đổi; snapshot dựng một lần; version `0` |
| Hash if exists | Chỉ subject đã có mới so `before_hash`/`after_hash` |
| Set-based merge | Cùng reducer FT-054 [D4]: locator, primary, tags, tombstone |
| Outbox checkpoint | Unique `(operationId, subjectId, eventType)` + fence trước commit |

Function signature, `statement_timeout` và `CatalogOperationPageStore.finalizePage` giữ nguyên.

## Quyết định và So sánh (Trade-offs)

| Thuộc tính | V19 hiện tại | FT-056 đích |
| --- | --- | --- |
| Working set page | 7 `CREATE TEMP ... ON COMMIT DROP` | UNLOGGED scratch `(operation_id, lane_id)`; xóa/ghi lại mỗi page |
| Index/ANALYZE | 4 index phụ + 5 `ANALYZE` mỗi page | Index tạo một lần lúc migrate; không DDL trong function |
| Subject mới | `before_hash` null; vẫn `after_hash` JSON | Bỏ cả hai hash so sánh; luôn emit snapshot |
| Subject đã có | Hash trước/sau; no-op không outbox | Giữ |
| Claim/lease | Một page / một claim | Giữ — D3 mới đổi |
| Event/outbox contract | `media.subject.changed.v2` | Không đổi |
| Gate | Không đạt 1M (FT-054 timeout) | `pageExec` median `< 5 ms`; 100K subject `<= 5 s` |

CTE `MATERIALIZED` trong function (lần 1) scan lặp `catalog_discovery_stage`: calibration 25K
`mergeMs=2.633 s` / pageExec avg `129 ms` (chậm hơn V19 `2.032 s` / `106 ms`); 1M gãy
`DataAccessResourceFailureException` (tmpfs/connection). Scratch UNLOGGED keyed theo lane tránh
catalog DDL và tránh spill CTE. Không `CREATE TEMP` trong page loop.

Không tách stored procedure thành nhiều function chỉ để giảm dòng: một page = một transaction fence.
Nếu body vượt 500 dòng, Plan ghi ngoại lệ stored-proc; không tách làm nhiều round-trip.

## Domain và data ownership

`catalog-service` sở hữu `catalog_db`: `catalog_discovery_stage`, `catalog_operation_subject`,
`catalog_operation_lane`, `catalog_approval_operation`, `media_subject` / `media_asset` / tags /
actresses, `catalog_removed_asset_locator`, `catalog_outbox_event`. Không đọc/ghi database service khác.

Reducer giữ [FT-054 Design D4](../054-catalog-operation-coalescing/02-design.md): thứ tự
`(sourcePartition, sourceOffset, eventId)`; locator `(storageKey, relativePath)`; primary election
(video không tag thắng, giữ primary hiện tại khi cùng priority); `tagNames` từ primary; tombstone
`removedAt >= event.timestamp`.

## REST/event contract

Không đổi REST, không đổi schema `media.subject.changed.v2` hay `media.approval.watermark.v1`.
Output vẫn transactional outbox; Query không thuộc D2.

## Luồng lỗi, idempotency và consistency

- Mất fence trước/trong checkpoint → exception, Java release, retry claim; không commit canonical dở.
- Snapshot vượt `maximum-snapshot-bytes` (900 KiB) → workset `FAILED`, operation `BLOCKED`, không insert outbox.
- Unique outbox `(operation_id, subject_id, event_type)` chặn duplicate snapshot khi retry cùng operation.
- Cardinality: `checkpoint_count = processed_count`; `inserted_snapshot_count = changed_count`.
- Subject mới retry: `ON CONFLICT` subject/asset + unique outbox hội tụ một canonical effect.
- Subject cũ không đổi: workset `COMPLETED`, `changed = false`, không tăng version, không outbox.

## Hiệu năng, quan sát và bảo mật tối thiểu

Đồng hồ D2: `FinalizerSnapshot.pageExec` (median/p95/max) và wall-clock từ `READY_TO_COALESCE` tới
workset hoàn tất. `acquireMillis` / Kafka ingest / relay / `QUERY_DB_READY` không được cộng vào gate.

Calibration: 2.500 subject (25K event đã stage). Qualification: 100.000 subject. Run manifest ghi
page size, worker count, pool, `work_mem`, `statement_timeout`, CPU/RAM/storage. Testcontainers là
correctness; throughput chỉ trên qualification environment đã khóa.

Không claim `SLI-03` hay Catalog phase `<= 10 s` (ngân sách D1+D2+D3; D4 mới đóng).
