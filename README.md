# Paytm Money — Seat Reservation at Scale

Backend take-home assessment: concurrent seat reservation with PostgreSQL as the consistency boundary.

## Stack

- Java 21
- Spring Boot 4.1.1
- Spring Web
- Spring Data JPA / Hibernate
- PostgreSQL 17
- Flyway
- Spring Security
- Docker

## Design direction

PostgreSQL is the source of truth for reservation correctness. The reservation transaction will lock the user/show coordination row and requested seat rows using `FOR UPDATE`, with seats acquired in deterministic order. Idempotency is persisted with a database unique constraint.

Multi-seat reservations are all-or-nothing.

## Local prerequisites

- JDK 21
- Maven 3.6.3+
- Docker / Docker Compose

## Run locally

```bash
mvn clean test
docker compose up --build
```
