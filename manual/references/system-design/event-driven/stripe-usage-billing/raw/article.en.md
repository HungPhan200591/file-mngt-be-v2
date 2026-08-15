# How we built it: Usage-based billing

> Source: [Stripe](https://stripe.com/blog/how-we-built-it-usage-based-billing)  
> Retrieved: 2026-08-15

![Image 1: Blog > How we built it: Usage-based billing > Header image](https://images.stripeassets.com/fzn2n1nzq965/7EExFvCgb0Dp7TTmKd2EC8/f31404de2517772dd0e162a24af02ef4/Usage_based_billing_Header_and_social__1_.png?w=1616&q=80)

Illustration by Álvaro Bernis

Usage-based billing (UBB) is becoming a preferred pricing model because it gives customers flexibility while aligning cost and value. Stripe saw increased demand for high-throughput UBB, released upgrades including credit burndown pricing and capacity for up to 100,000 events per second per business.

The product emphasizes three features: an accurate and highly available revenue ledger; real-time event processing with ultrahigh throughput billing; and support for complex pricing and accurate billing despite delayed events. The combination is difficult because high throughput puts pressure on real-time processing. The architecture offers lessons for highly scaled, highly reliable event streaming.

## Lesson 1: Asynchronous events processing increases speed and lowers costs—but it needs to be combined with additional developer observability

Stripe wanted to increase throughput 100x while maintaining 99.999% availability, zero data loss and low latency, and to process millions of events per second cost-effectively. Traditional Stripe APIs authenticate, validate, route, call RPCs and then run business logic synchronously across internal servers. That is too slow and expensive for an event stream.

The UBB API sends events to an edge router, which performs stateless authentication and API validation, then loads them directly onto an event bus. Async processing conveys events to the Dashboard or billing logic without slowing the stream. Its challenge is that failures are not immediately visible. Stripe addressed this with a real-time processing Dashboard and webhooks for validation failures. Async APIs enable fast, reliable and cost-effective streams only when paired with stronger developer observability.

![Image 2: Meter events](https://images.stripeassets.com/fzn2n1nzq965/58sKjWagOcVCeyO0W8crdW/9a479a4e24215f3f34c5b8c6edf9c5ec/Meter-events_2x.png?w=1616&q=80)

## Lesson 2: An active-active setup solves the problem of downtime, but it needs to be enriched with metadata that enables accurate reconciliation

For accurate processing without sacrificing reliability or latency, Stripe chose Apache Flink for distributed stream processing, low latency and exactly-once guarantees. Flink can have downtime, which is unacceptable for real-time financial usage. Stripe therefore processes the same event in two geographic regions simultaneously using active-active deployment.

Active-active solves downtime but creates a cross-region consistency problem. Stripe tags each event with standardized metadata, including a timestamp generated before the two Flink applications, event type and source. Matching metadata lets the systems compare streams and reconcile delayed events, preserving accurate aggregation and continuity during downtime.

## Lesson 3: Use a “fast” path, “slow” path approach to matching usage with pricing

Stripe represents pricing as a stream of changes and matches it against each customer event stream. When the streams fall out of sync, such as a retroactive discount, a lookback window is needed without pausing the event stream. Stripe created two aggregation pipelines with different speeds.

The fast path uses a 30-second tumbling window stored in memory. It triggers billing alerts, guarantees events are not lost and gives fast feedback. The slow path uses a five-minute window and writes events to disk as a transactional ledger. It handles delayed/out-of-order events and edge cases, and generates analytics, invoicing data and revenue-recognition records.

## A usage-based billing solution that can scale with your ambitions

Stripe reports capacity for 100,000 events per second per user, P95 latency below 30 seconds for time-sensitive operations and about five minutes end-to-end latency from ingestion to rated output for most use cases. See [usage-based billing](https://docs.stripe.com/billing/subscriptions/usage-based), [Stripe jobs](https://stripe.com/jobs/search) and the [usage-based billing guide](https://stripe.com/lp/usage-based-billing-guide).
