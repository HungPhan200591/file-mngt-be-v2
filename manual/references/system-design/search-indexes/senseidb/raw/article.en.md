# Introducing SenseiDB 1.0: an open-source, distributed, realtime, semi-structured database

> Source: [LinkedIn Engineering](https://engineering.linkedin.com/open-source/introducing-senseidb-10-open-source-distributed-realtime-semi-structured-database)  
> Source capture: Jina Reader; retrieved 2026-08-15.  
> Note: the source page currently exposes a historical article with incomplete/incorrect publication metadata in its HTML; the original URL is authoritative.

I'm excited to announce that we have released version 1.0.0 of [SenseiDB](http://senseidb.com/) to the open-source community. Sensei is a distributed, elastic, realtime, and semi-structured database.

**[Check out SenseiDB on Github!](https://github.com/linkedin/sensei)**

Read on to learn more about what Sensei does, the architecture behind it, and the project's future direction.

## What is Sensei?

![Image 1: SenseiDB Logo](https://content.linkedin.com/content/dam/engineering/en-us/blog/migrated/sensei_black_0.jpg)

Sensei is a distributed data system that was built to support many product initiatives at LinkedIn, including the real-time faceted search in [Signal](http://www.linkedin.com/signal/) and the news feed and tabs on the [Homepage](http://www.linkedin.com/). It is the foundation of LinkedIn's search and data infrastructure.

Sensei is both a search engine and a database. It is designed to query and navigate through documents that consist of (a) unstructured text and (b) well-formed and structured metadata.

## Features

Some features and differentiators of Sensei:

* Ability to consume high insert/updates while maintaining [high query performance](http://senseidb.com/performance.html).
* Support for complex queries via a [query language](http://senseidb.com/bql.html) (BQL) and a [REST/JSON api](http://senseidb.com/client-rest.html).
* Streaming updates from different [Gateways](http://senseidb.github.com/sensei/indexing-gateway.html) such as JDBC, JMS, and [Kafka](http://incubator.apache.org/kafka/).
* Bootstrapping from [Hadoop](http://hadoop.apache.org/), e.g. Map-Reduce job to batch build index and push to Sensei clusters.
* Ability plug-in custom and complex faceting logic such as the social graph.

## Architecture

![Image 2: Sensei Architecture](https://content.linkedin.com/content/dam/engineering/en-us/blog/migrated/sensei-architect.png)

### Inserts

Unlike many other data-systems, Sensei consumes data from an ordered and versioned data stream that we call a [gateway](http://senseidb.github.com/sensei/indexing-gateway.html). Within LinkedIn, some of the data streams consumed by Sensei include [Kafka](http://incubator.apache.org/kafka/) and Databus (a technology we use to stream data from a database).

Sensei relies on the external data stream for atomicity and isolation guarantees; in a way, the commit log is externalized. This design allows us to optimize for update rate while providing eventual consistency across replications without needing a quorum.

For more details, see the [architecture overview page](http://senseidb.com/overview.html) and the [clustering page](http://senseidb.com/cluster.html).

### Queries

Sensei's execution engine is optimized for performance on very large datasets and supports a rich query feature set:

* get/getAll, e.g. a key-value retrieval
* full-text search
* structured, sql-like selects
* aggregation, e.g. facet counting and group-by

Along with a [REST/JSON API](http://senseidb.github.com/sensei/clients.html), Sensei supports a SQL-like query language called [BQL](http://senseidb.github.com/sensei/bql.html).

## What Sensei is NOT

Some features Sensei does not support in comparison to other data-systems:

* Sensei is not relational. Like many other NoSQL systems, data is de-normalized and JOIN operations are not supported.
* Sensei is not transactional. We provide durability and eventual consistency guarantees but we do not support a full transactional insert model (e.g. roll-back).

## Next play

Some future work we have in mind for Sensei:

* Relevance toolkit
* Support for aggregation and field collapsing
* Support for nested document structures
* Dynamic Schema
* Online data-rebalancing
* Data import/export
* Inter-cluster Map-Reduce support

## Get involved!

To learn more and help drive Sensei forward, check-out the [SenseiDB project page](http://senseidb.com/), [source code](https://github.com/linkedin/sensei), [LinkedIn group](http://www.linkedin.com/groups/SenseiDB-4264313), [mailing list](http://groups.google.com/group/sensei-search), and IRC at `irc.webchat.org`, channel `#sensei-search`.
