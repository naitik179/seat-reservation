# Seat Reservation at Scale — Design Write-up

## 1. Atomic Decision — Preventing Double-Sell

The atomic decision is made by **PostgreSQL row-level locking using `SELECT ... FOR UPDATE` inside a single database transaction**.

For every reservation:

1. Normalize and deterministically sort the requested seat numbers.
2. Start a database transaction.
3. Lock the requested seat rows using:

```sql
SELECT *
FROM seats
WHERE show_id = ?
  AND seat_number IN (...)
ORDER BY seat_number
FOR UPDATE;
```

4. Verify that **all** requested seats are `AVAILABLE`.
5. Create the reservation.
6. Change all requested seats to `CONFIRMED`.
7. Persist the reservation-seat mappings and update the user's seat count.
8. Persist the completed idempotency record.
9. Commit the transaction.

The critical property is that the availability check and the state transition happen while the database row lock is held.

For example, if two concurrent requests attempt to reserve `A1`:

```text
Request 1 ──┐
            ├── SELECT FOR UPDATE ──> A1 locked by Request 1
Request 2 ──┘

Request 1:
    A1 = AVAILABLE
    A1 -> CONFIRMED
    COMMIT

Request 2:
    waits for the row lock
    reads A1 = CONFIRMED
    returns 409
```

Therefore, two transactions cannot both observe `A1` as available and successfully confirm it.

The correctness does not depend on Java `synchronized`, an in-memory lock, or a single application instance. PostgreSQL is the shared correctness boundary.

### Why this is race-free

The race would be:

```text
T1: read A1 = AVAILABLE
T2: read A1 = AVAILABLE
T1: confirm A1
T2: confirm A1
```

`SELECT ... FOR UPDATE` prevents this sequence because T2 cannot proceed past the row lock until T1's transaction completes.

---

## 2. Multi-Seat Reservations and Deadlocks

Multi-seat requests are **all-or-nothing**.

Before acquiring locks, requested seats are:

1. Trimmed
2. Deduplicated
3. Sorted

For example:

```text
[A3, A1, A2]
```

becomes:

```text
[A1, A2, A3]
```

The SQL query also explicitly orders the rows:

```sql
ORDER BY seat_number
FOR UPDATE
```

This ensures concurrent transactions acquire seat locks in a consistent order.

Without deterministic ordering, a potential deadlock would be:

```text
Transaction 1: locks A1 → waits for A2
Transaction 2: locks A2 → waits for A1
```

With deterministic ordering:

```text
Transaction 1: A1 → A2
Transaction 2: A1 → A2
```

One transaction obtains the first lock while the other waits, rather than acquiring locks in opposite orders.

---

## 3. Per-User Limit

The assignment requires the per-user limit to remain correct under concurrency.

A `user_show` table stores:

```text
(show_id, user_id, seat_count)
```

The row is locked using:

```sql
SELECT *
FROM user_show
WHERE show_id = ?
  AND user_id = ?
FOR UPDATE;
```

The application then checks:

```text
existingSeatCount + requestedSeatCount <= perUserLimit
```

and updates the count within the same transaction.

This prevents two concurrent requests from the same user from both reading the same old seat count and bypassing the limit.

---

## 4. Idempotency

Idempotency state is persisted in the PostgreSQL `idempotency_keys` table.

The important fields are:

```text
show_id
user_id
idempotency_key
request_hash
reservation_id
response_status
```

There is a database unique constraint on:

```text
(show_id, user_id, idempotency_key)
```

### Same key + same request

The request hash matches the previously stored hash.

If the reservation already exists, the existing reservation is returned instead of creating another one.

### Same key + different request

For example:

First request:

```json
{
  "seats": ["A1"],
  "idempotencyKey": "payment-123"
}
```

Retry:

```json
{
  "seats": ["A2"],
  "idempotencyKey": "payment-123"
}
```

The request hashes differ, so the API returns:

```text
409 Conflict
IDEMPOTENCY_CONFLICT
```

### How exactly-once is enforced

The database unique constraint ensures that concurrent requests cannot create multiple idempotency records for the same:

```text
(show_id, user_id, idempotency_key)
```

The idempotency record and reservation are then completed in the same transaction.

Therefore, for a **committed idempotency key**, there is one durable reservation outcome.

This also handles the important failure case where the server commits the transaction but the HTTP response is lost:

```text
Client
  |
  | POST reservation
  v
Server
  |
  | COMMIT
  v
Database
  |
  X response lost
  |
Client retries same idempotency key
  |
  v
Existing reservation returned
```

The client does not need to guess whether the first request succeeded.

---

## 5. Holds and Expiry

The database model supports the seat states:

```text
AVAILABLE
HELD
CONFIRMED
```

However, the current assignment implementation does **not** implement automatic timed holds.

The implemented lifecycle is:

```text
AVAILABLE -> CONFIRMED
CONFIRMED -> AVAILABLE    (explicit cancellation)
```

`HELD` is retained in the model because the assignment defines the state and it provides a natural extension point for a payment/checkout flow.

If timed holds were required, I would implement:

```text
AVAILABLE
    |
    v
HELD + hold_expires_at
    |
    +---- payment succeeds ----> CONFIRMED
    |
    +---- expiry reached ------> AVAILABLE
```

The expiry operation would itself use a database transaction and row-level locking to ensure that an expired hold cannot race with a successful confirmation.

For example, a scheduled worker could periodically find expired holds and atomically release them.

I intentionally did not add a background expiry mechanism because the current assignment flow uses explicit cancellation rather than a payment/hold workflow.

---

## 6. Consistency vs Availability During a Partition

For seat reservation, I prioritize **consistency over availability**.

The reason is that selling the same seat twice is materially worse than temporarily rejecting or delaying a reservation.

The reservation decision requires a healthy PostgreSQL connection because PostgreSQL is the correctness boundary.

During a database/network partition:

```text
Application ----X---- PostgreSQL
```

the service should **fail closed** for reservation writes rather than accepting reservations using stale or local state.

This means:

* No local/in-memory fallback for seat ownership
* No accepting reservations without database confirmation
* Readiness should fail when the database is unavailable
* Reservation writes should fail rather than risk double-selling

This sacrifices availability during a partition, but preserves the core invariant:

```text
A seat must never be confirmed twice.
```

For this use case, that is the correct trade-off.

---

## 7. Transactional Consistency

The reservation operation is transactional.

The following operations participate in the same transaction:

* Idempotency key creation/lookup
* User/show locking
* Seat locking
* Availability validation
* Reservation creation
* Seat state changes
* Reservation-seat mappings
* User seat-count update
* Idempotency completion

This prevents partially completed reservations.

For a multi-seat request such as:

```text
A1, A2, A3
```

if any requested seat is unavailable, the complete request fails.

No subset of the requested seats is reserved.

---

## 8. Consistency Invariant

The system maintains:

```text
available + held + confirmed = total
```

Seat state is persisted in PostgreSQL, so state can be reconstructed after an application restart.

---

## 9. Observability

The application exposes:

```text
/actuator/health
/actuator/health/readiness
/actuator/prometheus
```

Custom metrics include:

```text
reservations_confirmed_total

reservations_declined_total{reason="seat_taken"}

reservations_declined_total{reason="per_user_limit"}

reservations_declined_total{reason="idempotent_replay"}
```

Structured logging is enabled.

---

## 10. Concurrency Testing

A Python burst test was created to simulate concurrent users competing for the same seat.

Example:

```bash
python burst_test.py \
  --base-url http://localhost:8080 \
  --show-id <SHOW_ID> \
  --seat A1 \
  --requests 1000 \
  --workers 100
```

The test verifies:

* Exactly one `201 Created`
* Remaining requests receive `409 Conflict`
* Zero `5xx`
* Zero client errors

### Local Results

The implementation was tested with up to **20,000 concurrent requests against the same seat**.

|   Requests |   201 |        409 |   5xx |
| ---------: | ----: | ---------: | ----: |
|        100 |     1 |         99 |     0 |
|        500 |     1 |        499 |     0 |
|      1,000 |     1 |        999 |     0 |
|      5,000 |     1 |      4,999 |     0 |
| **20,000** | **1** | **19,999** | **0** |

The deployed Railway environment was also validated with 100 concurrent requests:

```text
201 Created     : 1
409 Conflict    : 99
401 Unauthorized: 0
5xx             : 0
Client errors   : 0
```

Additional live validation covered:

* Health
* Readiness
* Show creation
* Reservation
* Idempotent retry
* Same-key/different-body conflict
* Cancellation

---

## 11. AI Usage — Directed vs Decided

AI tools were used extensively during development, but **the AI was used as an implementation and reasoning assistant, not as the authority for the architecture**.

### AI-directed work

I used AI for:

* Generating validation and exception-handling code
* Writing the Python concurrency test
* Debugging compilation and configuration issues
* Generating test cases
* Documentation and README preparation
* Exploring implementation alternatives

### Engineering decisions I made

The following decisions were intentionally made based on the assignment's correctness requirements:

* PostgreSQL is the correctness boundary
* `SELECT ... FOR UPDATE` is used for seat locking
* Seat locks are acquired in deterministic order
* The reservation is a single database transaction
* Per-user limits use a locked `user_show` row
* Idempotency is persisted in PostgreSQL
* `(show_id, user_id, idempotency_key)` has a unique constraint
* Same-key/different-body requests are rejected
* Multi-seat reservations are all-or-nothing
* No Java `synchronized`/in-memory locking is used
* No Redis or Kafka is required for the core reservation path
* Consistency is preferred over availability during a database partition

The resulting design was validated through functional testing and a 20,000-request concurrency test rather than being accepted solely because AI suggested it.

---

## 12. What I Would Do Next

If this were moving from an assessment to a production system, I would prioritize the following:

### 1. Real authentication and authorization

Replace the assignment-level Bearer token mechanism with OAuth2/JWT-based authentication and proper user identity management.

### 2. Outbox/event-driven integration

For payment, notifications, analytics, and downstream systems, use an outbox/event-driven approach so database state changes and emitted events remain reliable.

### 3. Rate limiting and traffic protection

Add API gateway rate limiting and protection against retry storms.

### 4. Production load testing

Run controlled load tests against production-like infrastructure and tune:

* PostgreSQL capacity
* Connection pool size
* Application replicas
* Lock contention
* Request timeouts


---

## 13. Summary

The implementation deliberately uses a small number of strong primitives rather than adding unnecessary distributed infrastructure.

The key correctness mechanism is:

```text
Database transaction
       +
SELECT ... FOR UPDATE
       +
deterministic lock ordering
       +
database idempotency constraint
```

This provides a simple and durable correctness boundary for the reservation system.

The implementation was validated with **20,000 concurrent requests against the same seat**, producing exactly one successful reservation, 19,999 domain-level conflicts, and zero server errors.

AI tools were used throughout development, but the core architectural decisions were explicitly evaluated against the concurrency, consistency, and failure requirements of the assignment.
