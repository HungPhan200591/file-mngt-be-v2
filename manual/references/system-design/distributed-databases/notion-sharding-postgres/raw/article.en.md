# Herding elephants: lessons learned from sharding Postgres at Notion

> Source: [Notion Engineering Blog](https://www.notion.so/blog/sharding-postgres-at-notion)  
> Author: Notion Infrastructure Team  
> Published: October 6, 2021  
> Retrieved: August 17, 2026  

Earlier this year, we took Notion down for five minutes of scheduled maintenance. While our announcement gestured at “increased stability and performance,” behind the scenes was the culmination of months of focused, urgent teamwork: sharding Notion’s PostgreSQL monolith into a horizontally-partitioned database fleet.

While the switchover succeeded to much jubilation, our team spent months architecting this migration to make Notion faster and more reliable for years to come.

---

## Deciding when to shard

Sharding represented a major milestone in our ongoing bid to improve application performance. By mid-2020, product usage surpassed the abilities of our trusty Postgres monolith, which had served us dutifully through five years and four orders of magnitude of growth. Billions of new blocks, files, and spaces created constant CPU spikes, and simple catalog-only migrations became unsafe.

For fast-growing startups, sharding prematurely carries heavy operational burdens and application constraints. For Notion, the existential inflection point arrived when:
1. **Postgres `VACUUM` process began to stall consistently**, unable to clean up dead tuples fast enough on multi-terabyte tables.
2. **Transaction ID (TXID) Wraparound Risk**: In PostgreSQL, if autovacuum cannot freeze old transaction IDs before reaching 2 billion transactions, the database halts all writes to avoid data corruption. This existential threat forced immediate action.

---

## Designing an Application-Level Sharding Scheme

Rather than relying on opaque clustering middlewares (e.g., Citus or Vitess), Notion chose **Application-Level Sharding** to maintain strict control over query routing, transaction boundaries, and data placement.

### 1. What Data to Shard?
Notion’s data model revolves around the **Block** entity (trees of user-created content). We decided to shard:
- The core `block` table.
- All related tables reachable via foreign key relationships (e.g., `space`, `discussion`, `comment`).
- Preserving all related records on the same physical shard to avoid cross-shard distributed transactions.

### 2. Partition Key: `workspace_id`
We chose `workspace_id` (a UUID) as the partition key:
- Ensures high **Data Locality**: All blocks, pages, and discussions belonging to a single workspace live on the same shard.
- Intra-workspace operations retain full ACID transaction guarantees.
- Cross-workspace queries are rare in Notion’s collaboration model.

### 3. The Power of Highly-Divisible Logical Shards: Why 480?
Notion introduced a layer of indirection: **Logical Shards mapped to Physical Databases**.
- We created **480 logical shards** (schemas).
- Initially, these 480 logical shards were mapped across **32 physical AWS RDS PostgreSQL instances** (15 logical shards per host).
- **Why 480?** 480 has an enormous number of divisors (2, 3, 4, 5, 6, 8, 10, 12, 15, 16, 20, 24, 30, 32, 40, 48, 60, 80, 96, 120, 160, 240).
- When traffic grew in 2023 ("The Great Re-shard"), Notion easily scaled from 32 physical hosts to **96 physical hosts** (5 logical shards per host) by simply moving schema buckets without modifying a single line of application routing code!

```
                    UUID workspace_id
                           │
                           ▼
                 hash(workspace_id) % 480
                           │
             ┌─────────────┴─────────────┐
             ▼                           ▼
      Logical Shard 0             Logical Shard 479
             │                           │
             ▼                           ▼
     Physical Host RDS 1        Physical Host RDS 32 / 96
```

---

## Migration and Zero-Downtime Cutover Strategy

Migrating hundreds of terabytes of live production data required a rigorous four-phase pipeline:

```
[Monolith Database] ──────── Dual-Write (Application) ────────► [32 Sharded Hosts]
        │                                                               ▲
        └─── PostgreSQL Logical Replication (Catch-up Historical) ──────┘
                                        │
                               [Audit / Dark Reads]
                               (Verify 100% parity)
                                        │
                         [5-Minute Cutover Maintenance]
```

1. **Dual-Write / Shadow Writes**: Application wrote new and updated records to both the monolith and the new sharded instances simultaneously.
2. **Logical Replication for Historical Data**: PostgreSQL Logical Replication streamed historical tuples from the monolith to target logical shards.
3. **Audit & Dark Reads**: Background verification jobs read records from both sources to ensure 100% byte-for-byte parity before cutover.
4. **5-Minute Cutover**: During a brief maintenance window, writes were paused, replication caught up within seconds, application configuration was switched to the sharded topology, and Notion came back online.

---

## Key Lessons Learned

1. **Shard by Business Boundary (Workspace ID)**: Choosing a partition key that groups related entities preserves ACID transactions and prevents expensive distributed 2PC (Two-Phase Commit).
2. **Use Logical Shards as a Decoupling Layer**: Never map shard keys directly to physical server IP addresses. Logical shards (e.g., 480 schemas) allow re-sharding hardware in hours rather than months.
3. **Beware Soft Limits Before Hard Limits**: Autovacuum stall and TXID wraparound will cripple PostgreSQL long before disk capacity runs out.
