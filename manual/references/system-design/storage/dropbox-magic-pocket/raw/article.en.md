# Inside the Magic Pocket: Dropbox's multi-exabyte storage system

> Source: [Dropbox Tech Blog](https://dropbox.tech/infrastructure/inside-the-magic-pocket)  
> Author: James Cowling (Dropbox Infrastructure Team)  
> Published: May 25, 2016  
> Retrieved: August 17, 2026  

Dropbox stores two fundamental kinds of data:
1. **File Content**: The raw bytes of user files (photos, videos, documents).
2. **Metadata**: Information about files, permissions, revision history, and user relationships.

**Magic Pocket (MP)** is the custom-built, in-house distributed storage system that Dropbox engineered to store multi-exabytes of file content. These files are split up into immutable blocks, replicated for durability, and distributed across our infrastructure in multiple geographic regions.

---

## Core Requirements and Architectural Principles

### 1. Immutable Block Storage
Magic Pocket is an **immutable block storage system**. It stores encrypted chunks of files up to 4 megabytes in size. Once a block is written to the system, it never changes. 

Immutability dramatically simplifies distributed systems design:
- No distributed locking for concurrent writes.
- No complex multi-version concurrency control (MVCC) in the raw storage engine.
- When a user edits a file, Dropbox records the sequence of file changes in a separate metadata system called **FileJournal**. Mutability is handled high up in the stack, while the underlying storage engine remains purely append-only and immutable.

### 2. Workload and Media Choice
Dropbox has extreme temporal locality: files are read frequently within the first hour of upload and much less frequently over time. However, cold files still require low-latency reads.
- **Spinning Disks (HDDs)** are used for bulk data blocks because they offer high storage density, durability, and low cost.
- **SSDs and RAM** are reserved for databases, caching layers, and index lookups.

### 3. Infinite Durability
Durability is non-negotiable. Data is protected against disaster using:
- **Erasure Coding**: Splitting data into $N$ data blocks and $M$ parity blocks to survive multiple concurrent drive failures with minimal storage overhead.
- **Multi-Zone Geographic Replication**: Storing independent copies across multiple geographic regions (Western, Central, and Eastern US).

### 4. Radical Simplicity over Complex Protocols
Complex distributed consensus (like raw Paxos/Raft) across millions of nodes is notoriously error-prone. Magic Pocket eschews distributed quorums where possible and leverages centralized, scalable coordination (sharded MySQL clusters for index tracking).

---

## Data Model: Blocks, Buckets, and Volumes

```
[File Content] ─── Split ───► [4MB Immutable Blocks (Key = SHA-256 Hash)]
                                       │
                                   Aggregated
                                       │
                                       ▼
                       [1GB Logical Buckets]
                                       │
                                 Erasure Coded
                                       │
                                       ▼
             [Replicated / Encoded Volumes on Storage Nodes]
```

1. **Block**: An opaque chunk of file data up to 4MB, compressed and encrypted. The identifier/key for a block is its cryptographic **SHA-256 Hash** (content-addressable storage).
2. **Bucket**: Managing billions of 4MB blocks individually creates unacceptable metadata overhead. MP aggregates blocks into **1GB logical containers called Buckets**.
3. **Volume**: One or more buckets replicated or erasure-coded across a specific set of physical storage nodes.

---

## Zone Architecture: Inside a Storage Cell

Within each geographic region/zone, Magic Pocket is structured into five decoupled components:

```
                          Incoming Request
                                 │
                                 ▼
                         ┌───────────────┐
                         │   Frontends   │
                         └───────────────┘
                           │           │
                  Check/Update         Stream Data
                           │           │
                           ▼           ▼
                   ┌─────────────┐   ┌─────────────────┐
                   │ Block Index │   │  Storage Nodes  │
                   │ (MySQL Grid)│   │  (OSD Daemons)  │
                   └─────────────┘   └─────────────────┘
                                       ▲
                                       │ Manage / GC / Repair
                                     ┌─────────────────┐
                                     │     Master      │
                                     │  (Coordinator)  │
                                     └─────────────────┘
```

1. **Frontends (Gateways)**:
   - Stateless servers that receive client read/write requests.
   - For `PUT`: Computes SHA-256 hash, checks if block already exists in the Block Index (instant deduplication), streams payload to storage nodes, and registers the block upon successful fsync.
   - For `GET`: Looks up bucket location from the Block Index and streams raw bytes directly from the nearest healthy storage node.
2. **Block Index (Sharded MySQL)**:
   - A highly scalable, sharded relational database grid that maps `Block SHA-256 Hash -> Bucket ID + Offset`.
3. **Storage Nodes (OSDs - Object Storage Daemons)**:
   - "Dumb" storage workers attached to high-density disk arrays.
   - They execute simple disk I/O commands (`put_block`, `get_block`, `fsync`) without needing to understand global cluster topology.
4. **Master (Coordinator & Janitor)**:
   - Operates entirely **out of the hot data path**.
   - Monitors storage node health, orchestrates background data repair when a drive fails, conducts garbage collection, and merges small buckets for erasure coding.

---

## Key Lessons Learned

1. **Separate Blob Storage from Metadata**: Storing large, immutable payloads in dedicated block stores while keeping fast-changing relationships in relational/sharded databases produces the highest performance and durability.
2. **Content-Addressable Storage (CAS)**: Using SHA-256 hashes as block keys gives automatic cross-user and intra-file deduplication out of the box.
3. **Keep Storage Nodes Simple**: Storage nodes should be dumb I/O daemons. Moving coordination to stateless frontends and off-path background masters isolates failures and prevents cluster-wide lockups.
