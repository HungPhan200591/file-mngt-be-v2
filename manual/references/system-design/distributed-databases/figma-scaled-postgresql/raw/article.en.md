# How Figma scaled to multiple databases

> Source: [Figma Engineering Blog](https://www.figma.com/blog/how-figma-scaled-to-multiple-databases/)  
> Author: Figma Infrastructure Team  
> Published: April 4, 2023  
> Retrieved: August 17, 2026  

In 2020, Figma’s infrastructure hit some growing pains due to a combination of new features, preparing to launch a second product, and more users (database traffic grows approximately 3x annually). We knew that the infrastructure that supported Figma in the early years wouldn’t be able to scale to meet our demands. We were still using a single, large Amazon RDS database to persist most of our metadata—like permissions, file information, and comments—and while it seamlessly handled many of our core collaborative features, one machine has its limits. Most visibly, we observed upwards of 65% CPU utilization during peak traffic due to the volume of queries serviced by one database. Database latencies become increasingly unpredictable as usage edges closer to the limit, affecting core user experiences.

If our database became completely saturated, Figma would stop working.

We were far from that, but as an infrastructure team, our goal is to identify and fix scalability issues proactively before they come close to being imminent threats. We needed to devise a solution that would reduce potential instability and pave the way for future scale. Plus, performance and reliability would continue to be top of mind as we implemented that solution; our team aims to build a sustainable platform that allows engineers to rapidly iterate on Figma’s products without impacting the user experience. If Figma’s infrastructure is a series of roads, we can’t just shut down the highways while we work on them.

---

## Tactical fixes: Gaining runway

We started with a few tactical fixes to secure an additional year of runway, while we set the foundation for a more comprehensive approach:

1. **Vertical scaling**: Upgrade our database to the largest instance available (from `r5.12xlarge` to `r5.24xlarge`) to maximize CPU utilization runway.
2. **Read replicas**: Create multiple read replicas to scale read traffic away from the primary writer.
3. **Dedicated databases for new use cases**: Establish separate databases for net-new products and features to prevent adding load to the original cluster.
4. **Connection pooling with PgBouncer**: Introduce PgBouncer as a connection pooler between application backend servers and RDS instances to manage thousands of concurrent connections efficiently.

While these tactical fixes bought us valuable time, they had clear boundaries:
- Writes (INSERT, UPDATE, DELETE) still contributed to the vast majority of CPU and I/O utilization on the single primary instance.
- Not all read queries could be offloaded to replicas because of application sensitivity to replication lag.

We needed a sustainable, long-term horizontal scaling architecture.

---

## Exploring our options: Why avoid NewSQL or NoSQL?

We evaluated several options for scaling out:

1. **Migrating to NoSQL (e.g., DynamoDB/Cassandra) or Vitess (MySQL)**: Would require a complex, high-risk dual-write/dual-read migration and fundamentally rewrite application domain models and relational guarantees.
2. **Postgres-compatible NewSQL (Distributed SQL)**: At our scale, Figma would have been one of the largest single-cluster footprints in the world for cloud-managed distributed Postgres. We did not want the operational burden and risk of discovering edge-case scaling bugs as the guinea pig customer for a proprietary managed platform.
3. **Self-hosting complex distributed databases**: High operational overhead that would distract the team from product velocity and platform reliability.

Instead of switching to a foreign technology, we chose to **double down on PostgreSQL** and scale it systematically through **Vertical Partitioning (Database Federation)** followed by **Horizontal Sharding via DBProxy**.

---

## Vertical Partitioning: Splitting by Domain and Table Isolation

Vertical partitioning moves independent groups of tables onto their own dedicated database instances.

To identify candidate tables for partitioning, we analyzed:
- **Workload Impact**: High Average Active Sessions (AAS) determined by sampling `pg_stat_activity` at 10ms intervals.
- **Table Isolation**: Tables with few or no cross-table foreign keys, joins, and cross-entity ACID transactions with the rest of the monolith.

### The Trade-off of Vertical Partitioning
When moving tables into separate databases, you lose:
- Native SQL JOINs across databases.
- Database-enforced Foreign Keys.
- Cross-database atomic transactions.

Application code had to be refactored to resolve relationships at the service/application layer.

---

## Horizontal Sharding and DBProxy

For tables that could not be solved by vertical partitioning alone (tables with hundreds of millions of rows, such as file metadata and permissions), Figma implemented horizontal sharding.

```
Application Backend (Ruby/Go)
        │
        ▼
   ┌─────────┐
   │ DBProxy │ (Query Parsing, AST inspection, Shard Routing)
   └─────────┘
        │
        ▼
   ┌───────────┐
   │ PgBouncer │ (Connection Pooling)
   └───────────┘
        │
  ┌─────┴─────┬───────────┐
  ▼           ▼           ▼
Shard 1    Shard 2     Shard N (PostgreSQL RDS)
```

### DBProxy: The Intelligent Routing Engine
Figma built a custom, lightweight query proxy in Go called **DBProxy**:
1. **SQL Parsing & AST Analysis**: Parses incoming SQL queries into an Abstract Syntax Tree (AST).
2. **Shard Key Extraction**: Inspects `WHERE` clauses to identify the partition key (e.g., `file_id` or `org_id`).
3. **Query Routing**:
   - **Targeted Route**: If the shard key is present, DBProxy forwards the query directly to the specific shard.
   - **Scatter-Gather**: If the shard key is missing, DBProxy fans out the query across all shards in parallel and merges the results in memory before returning them to the client.

---

## Two-Phase Safe Rollout: Logical Sharding before Physical Sharding

To eliminate downtime and prevent catastrophic migration failures, Figma used a disciplined two-phase rollout:

1. **Phase 1 — Logical Sharding**:
   - Application code was updated to route queries through DBProxy as if tables were already physically distributed across dozens of databases.
   - However, all logical shards initially resided as separate tables/views on the **same single physical RDS instance**.
   - This allowed Figma to validate query routing, transaction boundaries, and application behavior for months in production without moving any actual data across servers.
2. **Phase 2 — Physical Sharding via Logical Replication**:
   - Once logical sharding was proven stable, data was replicated to independent physical RDS instances using PostgreSQL Logical Replication.
   - The switchover for each table was executed safely in minutes with zero disruption to users.

Through this phased architectural strategy, Figma scaled its database capacity by **over 100x** while maintaining rock-solid uptime and developer velocity.
