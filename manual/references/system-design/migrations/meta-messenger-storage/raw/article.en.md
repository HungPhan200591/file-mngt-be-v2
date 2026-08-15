# Migrating Messenger storage to optimize performance

> Source: [Engineering at Meta](https://engineering.fb.com/2018/06/26/core-infra/migrating-messenger-storage-to-optimize-performance/)  
> Published: June 26, 2018

More than a billion people use Facebook Messenger to share text, photos, video and more. Messenger changed from an email-like product, where messages waited in an inbox, into a mobile-first, real-time communications system. The original monolithic service was separated into a read-through caching service for queries; [Iris](https://code.facebook.com/posts/820258981365363/building-mobile-first-infrastructure-for-messenger/) to queue writes to subscribers such as storage and devices; and a storage service for message history.

The storage service was modernized through three changes:

* redesigning and simplifying the schema, creating a source-of-truth index from existing data and defining invariants;
* moving from HBase to [MyRocks](https://code.facebook.com/posts/190251048047090/myrocks-a-space-and-write-optimized-mysql-database/), which integrates RocksDB as a MySQL storage engine;
* moving from spinning disks to flash on the [Lightning Server SKU](https://code.facebook.com/posts/989638804458007/introducing-lightning-a-flexible-nvme-jbof/).

The result was improved resiliency and latency, 90 percent lower storage consumption and new capabilities such as mobile content search, without disruption or downtime. It required two migration flows to account for every Messenger user.

## Handling the challenge of migrating at scale

HBase served Messenger well, but MyRocks could use flash instead of spinning disks. MySQL replication topology was also more compatible with Facebook data centers, reducing physical replicas while improving availability and disaster recovery.

Migration had to keep Messenger running for more than one billion accounts. Reading historical HBase data was I/O-bound; going too aggressively would degrade HBase and cause user-visible errors. Business users could keep many chat windows active around the clock, so code had to support product changes on both old and new systems during account moves.

Every account's petabytes of data had to be migrated. The schema changed, so the process had to parse messy legacy data, handle corner cases and resolve conflicts while preserving the same messages, videos and photos. At the same time, Meta was building the new database and Lightning hardware and fixing software, kernel, firmware and power-path bugs.

The normal flow covered 99.9 percent of accounts. A buffered migration flow covered hard-to-migrate accounts. The team performed thorough data validation, prepared a revert plan and ran an accounting job to verify that nobody was missed before taking the old system offline.

![Chart 1: Migration workflow](https://engineering.fb.com/wp-content/uploads/2018/06/statemachine-new-code.png)

## Normal migration

The single-user migrator assumes that no data is written to an account during migration. A state machine and monitoring tools enforce this. An account is in one of three static states—`not-migrated`, `double-writing` or `done`—or a dynamic state while migration is active.

At migration start, the system records the last data position in the old storage service and Iris, then copies data to the new system. When copying finishes, it checks whether the source position moved. If it did not, writes can switch to MyRocks and the account enters `double-writing`. If it moved, migration fails, MyRocks data is cleaned up and a future job retries the account.

During `double-writing`, the migrator performs data and API validation. Data validation compares HBase and MyRocks. API validation reads from both systems and compares responses so clients can read seamlessly. Before `done`, the workflow verifies success. A complete revert plan can return the account to `not-migrated`, switch read serving back and wipe new-system data.

![Chart 2: Normal migration flow](https://engineering.fb.com/wp-content/uploads/2018/06/NormalMigration_New_site2.jpg)

## Buffered migration flow

Some accounts have no quiet period, such as large businesses running Messenger bots, or are unusually large. The buffered flow sets a migration start cutoff, snapshots the account, copies it to a buffer tier, and migrates the buffer to MyRocks. Meanwhile, writes to MyRocks queue in Iris, which can queue messages for weeks. Once the snapshot is migrated, the account enters `double-writing`; MyRocks resumes writes, drains the queue and catches up.

The buffer may be a dedicated HBase tier with the old schema. For extremely large accounts, dedicated SSD servers with embedded RocksDB make migration faster.

![Chart 3: Buffered migration flow](https://engineering.fb.com/wp-content/uploads/2018/06/BufferedMigration_new-site.png)

## Migrate at scale

Meta used the [Bistro framework](https://facebook.github.io/bistro/) to parallelize migration jobs, schedule work, track progress, log/analyze progress and throttle jobs when service issues appeared. After accounts became `done`, new accounts were created only on the new system, writes to HBase stopped cluster by cluster, and an accounting job verified every HBase account had migrated. The process reached 100 percent migration.

## Benefits of the new system

The normal flow migrated 99.9 percent of accounts in two weeks; the remaining accounts finished through the buffered flow two weeks later. A simpler schema reduced disk usage. [Zstandard](https://github.com/facebook/zstd) compression and a replication-factor reduction from six to three reduced storage by 90 percent without data loss.

MyRocks provided more automated disaster recovery; a data-center switch no longer required manual supervisors. With Lightning flash, read latency became 50 times lower than HBase. The migration also enabled mobile message search using Facebook's MySQL-based search infrastructure. The completed migration left a platform for further Messenger improvements.
