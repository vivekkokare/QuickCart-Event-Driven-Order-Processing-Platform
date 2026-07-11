# QuickCart — System Design Narrative

*Use this document to rehearse the system design interview. Each section maps to a stage of a typical 45-minute system design round.*

---

## 1. Problem Statement (2 min)

"Design an order processing system for a quick-commerce platform like Blinkit. A customer places an order, the system validates it, and fulfilment begins — specifically stock is reserved in the inventory."

**Clarifying questions you should ask in an interview:**
- What is the expected order volume? (Blinkit: ~500k orders/day, peaks at ~50 RPS)
- Is stock reservation synchronous (block the order response) or async?
- What consistency level is acceptable — can we oversell temporarily?
- Are we designing just the backend, or the full system?

---

## 2. High-Level Architecture (5 min)

```
Customer
   │ HTTP POST /api/v1/orders
   ▼
[ALB]
   │
   ▼
[order-service]  ──── (sync pre-check) ────► [inventory-service]
   │                                               │
   │ (saves order + outbox entry in one TX)        │ (consumes OrderCreated,
   │                                               │  deducts stock in DB,
   │ OrderCreated event ──► [RabbitMQ] ──────────► │  writes to Redis cache)
   │
[RDS Postgres: order_system]            [RDS Postgres: inventory_system]
                                        [ElastiCache Redis]
```

**Key talking points:**
- Two microservices, each owning its own database (database-per-service pattern)
- Communication is **synchronous** (HTTP) for the pre-flight stock check and **asynchronous** (RabbitMQ) for the actual stock deduction
- Both services are deployed on ECS Fargate behind an Application Load Balancer

---

## 3. Order Placement Flow — Deep Dive (10 min)

### Happy path

1. **Client** sends `POST /api/v1/orders` to order-service via ALB
2. **order-service** validates the request (bean validation on the DTO)
3. **Pre-flight stock check** — order-service calls `GET /api/v1/stock/{productId}` on inventory-service for each item. This is a synchronous REST call protected by a Resilience4j circuit breaker.
4. If stock is insufficient → `HTTP 422 Unprocessable Entity` returned immediately
5. If stock is available → order-service creates an `Order` domain aggregate and persists it
6. **Outbox write** — in the *same database transaction*, an `OutboxEvent` row is inserted
7. HTTP `201 Created` with order ID returned to client
8. **Outbox poller** (runs every 5s) reads unpublished events, publishes `OrderCreated` to RabbitMQ exchange `order.events`
9. **inventory-service** consumes from `inventory.order-created.queue` (bound to `order.events` exchange)
10. Idempotency check — if event already processed, skip
11. Stock deduction in RDS, cache eviction in Redis

### Why two-phase (sync check + async deduction)?

The sync check provides **fast feedback** to the customer — they know immediately if a product is out of stock. But it's advisory, not definitive, because:
- The cache may be slightly stale (5-minute TTL)
- Another order could race between the check and the deduction

The async deduction is the **definitive gate**. It reads actual DB stock levels inside a transaction. If stock is genuinely unavailable (race condition), the reservation fails and a compensating event (order cancellation) would be published — a natural next step.

This is the same pattern used by most e-commerce systems: fast optimistic accept, slower authoritative confirmation.

---

## 4. Reliability Patterns (10 min)

### Transactional Outbox — "at-least-once delivery"

**Problem:** `saveOrder()` and `publishEvent()` are two separate I/O operations. A crash between them leaves the system inconsistent.

**Solution:** Write the event to an `outbox_events` table in the same DB transaction as the order. The outbox poller delivers it later. The database's ACID guarantee ensures both writes succeed or neither does.

**Interview follow-up — what about the poller crashing after publish but before marking as published?**
The event gets redelivered. That's why we need an idempotent consumer.

### Idempotent Consumer — "exactly-once processing"

**Problem:** RabbitMQ can redeliver messages (consumer restart, nack). inventory-service must not deduct stock twice.

**Solution:** `processed_events` table. Before processing, check if `event_id` already exists. Check + deduction + mark-processed are all in one `@Transactional` method — atomic.

**The combination gives us:** at-least-once delivery + idempotent consumer = effectively-once processing.

### Circuit Breaker — "fail fast, degrade gracefully"

**Problem:** If inventory-service is down, without a circuit breaker, every order request makes an HTTP call that blocks for the full timeout (e.g. 30s). This exhausts thread pools and cascades the failure to order-service.

**Solution:** Resilience4j circuit breaker. After 50% failure rate over 5 calls, the circuit opens. Subsequent calls short-circuit and return the fallback (`true`) immediately. The circuit half-opens after 10 seconds to probe recovery.

**Why optimistic fallback (`return true`)?**
Because the definitive stock check happens in inventory-service's event handler anyway. Rejecting orders during an inventory outage would be unnecessarily restrictive — users would see errors even though their orders could be fulfilled when the service recovers.

### Dead Letter Queue

Failed messages (malformed payload, processing exception after retries) are routed to `inventory.order-created.queue.dlq`. This prevents bad messages from blocking the main queue and allows manual inspection and replay.

---

## 5. Data Model (5 min)

### order-service (order_system DB)

```sql
orders          — id, customer_id, status, total_amount, created_at
order_items     — id, order_id, product_id, product_name, quantity, unit_price
outbox_events   — id, event_type, payload (JSONB), published, created_at
```

### inventory-service (inventory_system DB)

```sql
stock           — product_id (PK), product_name, available_quantity, version
processed_events — event_id (PK), processed_at
```

**`version` on stock** — used for optimistic locking. If two concurrent reservations try to update the same stock row, one will fail with an `OptimisticLockException` and retry. Avoids `SELECT FOR UPDATE` which holds a lock for the transaction duration.

---

## 6. Caching Strategy (5 min)

**What is cached:** `Stock` objects keyed by `productId` in Redis, 5-minute TTL.

**Read-through:** On `findByProductId`, check Redis first. On miss, load from Postgres, populate Redis.

**Write-around (evict on write):** On `save`, write to Postgres first, then evict the Redis key. Next read will be a cache miss that fetches fresh data from DB.

**Why not write-through?** Write-through would update the cache on every save. Since `save` is called after stock deductions (which happen asynchronously on event consumption), write-through and write-around give similar freshness. Write-around is simpler.

**Cache DTO vs Domain Object:** We cache `StockCacheEntry` (a Java record with Jackson) not `Stock` (the domain object). This keeps the domain model free of serialisation annotations — a Clean Architecture requirement.

---

## 7. Scaling Discussion (5 min)

**order-service** is stateless — scale horizontally by increasing ECS `desiredCount`. The ALB distributes traffic across tasks.

**inventory-service** consumes from a RabbitMQ queue — multiple instances compete for messages. RabbitMQ's round-robin dispatch means each `OrderCreated` message goes to exactly one consumer. Horizontal scaling works without coordination.

**Bottlenecks at scale:**

| Component | Bottleneck | Solution |
|---|---|---|
| Outbox poller | Single-threaded polling | Partitioned outbox, parallel pollers, or CDC (Debezium) |
| RDS single instance | Write throughput | Read replicas for reads, PgBouncer for connection pooling |
| Redis single node | Cache invalidation storms | Redis Cluster, local L1 cache in front of Redis |
| Amazon MQ | Message throughput | Amazon MQ Active/Standby pair, or migrate to MSK (Kafka) |

**At Blinkit scale (~50 RPS peak):**
- order-service: 2-4 Fargate tasks (512 CPU, 1GB RAM each)
- inventory-service: 2-4 Fargate tasks
- RDS: db.t3.medium with read replica
- Redis: cache.r6g.large with 1 replica

---

## 8. Observability (2 min)

**Logs:** CloudWatch Log Groups (`/quickcart/order-service`, `/quickcart/inventory-service`), 1-week retention.

**Health checks:** Spring Actuator `/actuator/health` exposed, checked by both ECS (container health) and ALB (target group health).

**What to add in production:**
- **Metrics:** Micrometer + CloudWatch metrics (order rate, event lag, circuit breaker state, cache hit ratio)
- **Tracing:** AWS X-Ray or OpenTelemetry — trace a request across order-service → RabbitMQ → inventory-service
- **Alerting:** CloudWatch Alarms on `runningCount < desiredCount`, DLQ depth > 0, circuit breaker open

---

## 9. Security (2 min)

- **Network isolation:** Data stores in isolated subnets with no internet route. ECS tasks in private subnets. Only ALB is internet-facing.
- **Secrets:** DB passwords and MQ passwords in AWS Secrets Manager, injected as environment variables at container startup. Never hardcoded or in Git.
- **Non-root containers:** Spring Boot runs as `spring` user inside the container (not root).
- **Security groups:** Least-privilege. RDS only accepts connections from `serviceSecurityGroup`. Redis only from `serviceSecurityGroup`. MQ only from `serviceSecurityGroup`.

---

## 10. What I Would Do Differently at Scale

1. **Replace outbox poller with CDC (Debezium)** — reads the Postgres WAL instead of polling a table. Lower latency, zero DB load for the poller.
2. **Replace Amazon MQ with MSK (Kafka)** — Kafka's partitioned log enables ordered processing per customer, long event retention for replay, and consumer groups for fan-out.
3. **Add a saga orchestrator** — for multi-step workflows (reserve stock → charge payment → dispatch). A choreography-based saga (as we have) works for two steps but becomes hard to reason about with five.
4. **API Gateway** — rate limiting, authentication (JWT validation), request throttling before traffic reaches ECS.
5. **Canary deployments** — ECS supports weighted traffic shifting. Deploy to 10% of traffic, monitor error rates, then shift 100%.
