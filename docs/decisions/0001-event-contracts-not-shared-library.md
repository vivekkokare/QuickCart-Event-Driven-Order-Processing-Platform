# ADR 0001: Event contracts are duplicated per service, not shared via a library

## Status
Accepted

## Context
`order-service` needs to notify `inventory-service` that an order was placed,
so inventory can attempt to reserve stock. This requires both services to
agree on the shape of an `OrderCreated` event.

The obvious-looking option is a third Maven module (e.g. `event-contracts`)
containing shared Java classes, which both services add as a dependency.
This gives compile-time type safety on both sides.

## Decision
We do **not** create a shared library module for event contracts. Each
service defines its own copy of the event record in its own
`infrastructure.messaging.event` package (e.g.
`order-service`'s `OrderCreatedEvent` and `inventory-service`'s own
`OrderCreatedEvent`, which may even diverge slightly in field selection).

The contract between the two services is the **documented wire schema**
(JSON shape + exchange name + routing key, documented in the event class's
Javadoc), not shared compiled code.

## Rationale
A shared library module reintroduces the exact coupling microservices are
meant to remove:

- Changing the event shape now requires a coordinated release: bump the
  shared module's version, then release every consuming service against the
  new version, in the right order. One of the main reasons teams choose
  microservices — independent deployability — is gone.
- A shared library tends to accumulate more than just event shapes over
  time (helper methods, validation logic, base classes) and becomes a
  disguised second monolith that everyone has to agree on before they can
  ship.
- Real inter-service contracts in industry are usually schema-based (JSON
  Schema, Avro + a schema registry, protobuf) specifically so that producer
  and consumer evolve independently as long as compatibility rules are
  followed. Duplicating a plain Java record per service is the same idea at
  much lower ceremony, appropriate for this stage of the project.

## Consequences
- We give up compile-time guarantees that both services agree on the event
  shape. A mismatch is caught at runtime/integration-test time, not by the
  Java compiler.
- We accept minor duplication (the event record is ~10 lines, copied once).
- Any breaking change to the event shape must use a new routing key (e.g.
  `order.created.v2`) so existing consumers are not silently broken by a
  producer deploy. This is called out directly in `OrderCreatedEvent`'s
  Javadoc in both services.
- If/when this duplication becomes a real maintenance burden, the
  documented escalation path is a schema registry (e.g. Confluent Schema
  Registry with Avro, or a JSON Schema repo validated in CI) — not a shared
  Java library.
