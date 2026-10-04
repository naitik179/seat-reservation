# Seat Reservation at Scale

Backend implementation of a concurrent seat reservation system designed to remain correct under high contention.

The system supports show creation, authenticated seat reservation, idempotent retries, concurrent reservation protection, cancellation, health checks, metrics, Docker deployment, and load testing.

## Tech Stack

- Java 21
- Spring Boot 4.1.1
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway
- Spring Security
- Spring Boot Actuator
- Micrometer / Prometheus
- Maven
- Docker / Docker Compose
- JUnit 5

## Core Design

PostgreSQL is used as the concurrency and correctness boundary.

The reservation flow uses a database transaction and row-level locks:

1. Validate the request.
2. Persist/find the idempotency record.
3. Lock the user's show-level row.
4. Lock requested seats using `SELECT ... FOR UPDATE`.
5. Verify all requested seats are available.
6. Create the reservation.
7. Mark seats as `CONFIRMED`.
8. Update the user's reserved-seat count.
9. Store the reservation against the idempotency key.
10. Commit the transaction.

The application does not rely on Java `synchronized`, JVM-local locks, Redis locks, or in-memory state for correctness.

## Why Database Row Locking?

The service may eventually run on multiple application instances.

A Java-level lock only protects threads inside one JVM. PostgreSQL row-level locking provides a shared concurrency boundary across application instances.

Requested seats are locked in deterministic seat-number order to reduce deadlock risk when multiple requests contain overlapping seat sets.

## Idempotency

Reservation requests contain an idempotency key.

A unique constraint is maintained on:

`(show_id, user_id, idempotency_key)`

The request payload is hashed.

Therefore:

- Same key + same request → returns the original reservation.
- Same key + different request → `409 Conflict`.
- Concurrent requests with the same key are serialized by the database.
- A committed reservation remains recoverable even if the client does not receive the original HTTP response.

## Concurrency Test

A Python burst-test script is included under:

`scripts/burst_test.py`

The hot-seat test sends many concurrent requests for the same seat.

Results obtained during local testing:

| Requests | Successful | Conflicts | 5xx |
|---:|---:|---:|---:|
| 100 | 1 | 99 | 0 |
| 500 | 1 | 499 | 0 |
| 1,000 | 1 | 999 | 0 |
| 5,000 | 1 | 4,999 | 0 |
| 20,000 | 1 | 19,999 | 0 |

The 20,000-request test confirmed that a single hot seat was reserved exactly once while all other concurrent attempts were rejected with `409 Conflict`.

The final seat-state invariant was also maintained:

`available + held + confirmed = total`

## API

### Create Show

`POST /shows`

Requires admin authentication.

Example:

```json
{
  "name": "Paytm Test Show",
  "seats": ["A1", "A2", "A3"],
  "pricePaise": 50000
}
```

Returns 201 Created.

Get Show

`GET /shows/{showId}`

Returns show details and the state of every seat.

Reserve Seats

`POST /shows/{showId}/reserve`

Requires authentication.

Example:

```json
{
"seats": ["A1", "A2"],
"idempotencyKey": "reservation-001"
}
```

The authenticated user's identity is derived from the bearer token and is not accepted from the request body.

Returns 201 Created for a new reservation.

Domain conflicts return 409 Conflict rather than 5xx.

Cancel Reservation

`DELETE /shows/reservations/{reservationId}`

Requires authentication.

Only the reservation owner can cancel the reservation.

Successful cancellation returns: 204 No Content
Cancelled seats become available again.

## Authentication

For this take-home assignment, bearer tokens are intentionally simplified.

Example:

Authorization: Bearer user-123

The token value represents the user identity.
The configured admin token is used for show creation.
This is a simplified authentication mechanism for the assessment. In production, this would be replaced by JWT/OAuth2 validation against an identity provider.

## Error Handling

Domain errors are returned as HTTP 409, 404, 401, or 403 responses as appropriate.

Examples:

`SEAT_TAKEN
PER_USER_LIMIT
IDEMPOTENCY_CONFLICT
SEAT_NOT_FOUND`

Unexpected application failures are not used to represent normal reservation declines

## Observability

Spring Boot Actuator exposes:
* Health
* Liveness
* Readiness
* Prometheus metrics

Custom metrics include:

`reservations_confirmed_total
reservations_declined_total{reason="seat_taken"}
reservations_declined_total{reason="per_user_limit"}
reservations_declined_total{reason="idempotent_replay"}
`
Readiness includes database health.

## Running Locally

Start the application

docker compose up --build

The application is available at:http://localhost:8080

PostgreSQL runs on: localhost:5432

Health
`curl http://localhost:8080/actuator/health`

## Test Data

A deterministic test show is available through:

scripts/test-data.sql

It creates show: 550e8400-e29b-41d4-a716-446655440000

Reset Test Show
Get-Content .\scripts\reset_show.sql |
docker compose exec -T postgres psql -U reservation -d seat_reservation
Burst Test
``python .\scripts\burst_test.py `
--show-id 550e8400-e29b-41d4-a716-446655440000 `
--seat A1 `
--requests 500 `
--workers 100``

For the full stress test:

``python .\scripts\burst_test.py `
--show-id 550e8400-e29b-41d4-a716-446655440000 `
--seat A1 `
--requests 20000 `
--workers 500
Database Migrations``

Flyway manages the database schema.

Migrations are located under:

src/main/resources/db/migration

Hibernate schema generation is disabled using:

`spring.jpa.hibernate.ddl-auto=validate
`
This ensures application startup validates the schema rather than modifying it.

Money Representation

All monetary values are represented as integer paise using BIGINT.
For example: ₹500 = 50000 paise .This avoids floating-point precision issues.

Transaction Boundaries

Reservation and cancellation operations execute inside database transactions.

A reservation is only considered successful once all related database changes are committed:

Reservation
Reservation seats
Seat state
User/show reservation count
Idempotency result
Production Improvements

The assignment intentionally keeps the architecture simple.

For a production system, the following could be added:

* JWT/OAuth2 authentication
* Rate limiting
* API gateway
* Distributed tracing
* Centralized log aggregation
* Managed PostgreSQL with replicas/backups
* Connection-pool and database capacity tuning
* Alerting
* Automated deployment
* More extensive integration and chaos testing

These are deliberately outside the minimum correctness boundary of this assignment.