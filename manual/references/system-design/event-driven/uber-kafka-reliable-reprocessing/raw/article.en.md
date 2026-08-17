# Building Reliable Reprocessing and Dead Letter Queues with Apache Kafka

> Source: [Uber Engineering Blog](https://www.uber.com/blog/reliable-reprocessing/)  
> Author: Uber Insurance Engineering Team  
> Published: February 22, 2018  
> Retrieved: August 17, 2026  

In distributed systems, retries are inevitable. From network errors to replication issues and even outages in downstream dependencies, services operating at a massive scale must be prepared to encounter, identify, and handle failure as gracefully as possible.

Given the scope and pace at which Uber operates, our systems must be fault-tolerant and uncompromising when it comes to failing intelligently. To accomplish this, we leverage Apache Kafka, an open source distributed messaging platform, which has been industry-tested for delivering high performance at scale.

Utilizing these properties, the Uber Insurance Engineering team extended Kafka’s role in our existing event-driven architecture by using non-blocking request reprocessing and dead letter queues (DLQ) to achieve decoupled, observable error-handling without disrupting real-time traffic. This strategy helps our opt-in Driver Injury Protection program run reliably in more than 200 cities, deducting per-mile premiums per trip for enrolled drivers.

In this article, we highlight our approach for reprocessing requests in large systems with real-time SLAs and share lessons learned.

---

## Working in an event-driven architecture

The backend of Driver Injury Protection sits in a Kafka messaging architecture that runs through a Java service hooked into multiple dependencies within Uber’s larger microservices ecosystem. For the purpose of this article, however, we focus more specifically on our strategy for retrying and dead-lettering, following it through a theoretical application that manages the pre-order of different products for a booming online business.

In this model, we want to both:
1. Make a payment, and
2. Create a separate record capturing data for each product pre-order per user to generate real-time product analytics.

This is analogous to how a single Driver Injury Protection trip premium processed by our program’s back-end architecture has both an actual charge component and a separate record created for reporting purposes.

In our example, each function is made available via the API of its respective service. When a pre-order request is received, Shop Service publishes a `PreOrder` message containing relevant data about the request. From there, each of the two sets of listeners reads the produced event to execute its own business logic and call its corresponding service (Payment Service and Analytics Service).

A quick and simple solution for implementing retries is to use a feedback cycle at the point of the client call. For example, if the Payment Service is experiencing prolonged latency and starts throwing timeout exceptions, the Shop Service would continue to call `makePayment` under some prescribed retry limit—perhaps with some backoff strategy—until it succeeds or another stop condition is reached.

---

## The problem with simple retries

While retrying at the client level with a feedback cycle can be useful, retries in large-scale systems may still be subject to:

* **Clogged batch processing (Head-of-Line Blocking)**: When we are required to process a large number of messages in real time, repeatedly failed messages can clog batch processing. The worst offenders consistently exceed the retry limit, which also means that they take the longest and use the most resources. Without a success response, the Kafka consumer will not commit a new offset and the batches with these bad messages would be blocked, as they are re-consumed again and again.
* **Difficulty retrieving metadata**: It can be cumbersome to obtain metadata on the retries, such as timestamps and *n-th* retry attempt.
* **Resource exhaustion & cascading failures**: Rapid-fire synchronous retries can overwhelm an already struggling downstream dependency, triggering cascading failures across the ecosystem.

If requests continue to fail retry after retry, we want to collect these failures in a DLQ for visibility and diagnosis. A DLQ should allow:
- **Listing** for viewing the contents of the queue.
- **Purging** for clearing bad messages.
- **Merging / Reprocessing** for safely rerunning dead-lettered messages once the root cause is resolved.

---

## Processing in separate queues (Non-blocking retry architecture)

To address the problem of clogged batch processing, we decouple error handling and retries from the main event stream into dedicated, tiered retry topics and dead letter queues.

### 1. Tiered Retry Topics with Delayed Processing
Instead of holding up the consumer on the main topic with blocking sleep or tight loops:
1. When a message fails processing, the consumer publishes the failed message to a designated **Retry Topic** (e.g., `pre-order-retry-1`), along with failure metadata in the message headers (retry count, error message, original timestamp).
2. The consumer then **commits the offset on the primary topic immediately**, freeing the partition to process subsequent fresh messages without lag.
3. Dedicated consumers subscribe to the Retry Topic. These retry consumers apply a configured delay (e.g., waiting 5 minutes) before re-attempting execution, giving downstream dependencies breathing room to recover.
4. If processing fails again, the message is forwarded to the next retry tier (e.g., `pre-order-retry-2` with a 15-minute delay).

### 2. Dead Letter Queue (DLQ) as Terminal State
If a message exceeds the maximum allowed retry attempts across all retry tiers:
1. It is routed to the **Dead Letter Queue** topic (`pre-order-dlq`).
2. The message stops being retried automatically.
3. The DLQ topic provides full observability for alerts, engineering inspection, root-cause diagnosis, and selective replay via administrative tools once the underlying issue is fixed.

---

## Key Lessons Learned

1. **Decoupling is Essential**: Isolating error handling from real-time stream ingestion prevents poison pills and slow downstream services from stalling real-time SLAs.
2. **Never Block Kafka Consumer Threads**: Performing `Thread.sleep()` or blocking loops inside consumer listeners degrades throughput across all partitions assigned to that consumer and risks triggering consumer group rebalances due to missed heartbeats.
3. **Preserve Message Context in Headers**: Propagate trace contexts, correlation IDs, original creation timestamps, and error stacks through Kafka headers across retry hops.
4. **Actionable DLQs**: A Dead Letter Queue is only valuable if there is an active operational process to monitor, inspect, alert, and reprocess messages.
