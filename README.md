# Outbox

A project where I built the transactional outbox pattern using Java, Spring Boot and PostgreSQL.

## About

When an app saves data and also sends an event to another service, these are two separate steps.
If one works and the other fails, the data and the event don't match. This is called the dual-write problem.

To fix this, I save the event in an `outbox_events` table in the same transaction as the data.
Then worker threads read the events from the table and send them to a webhook.
For the demo I used a simple money transfer that creates two events, `TRANSFER_INITIATED` and `TRANSFER_COMPLETED`.

## Features

- Transfer and its events are saved in one transaction
- Multiple workers send events at the same time without taking the same event
- Events of the same transfer are always sent in order
- Failed sends are retried with exponential backoff and jitter
- After 5 failed attempts the event goes to a dead letter queue
- Admin APIs to see dead events and requeue them
- A reclaimer that recovers events stuck on a crashed worker

## How It Works

```
PENDING -> PROCESSING -> PUBLISHED
               |
               +--> PENDING (retry later)
               |
               +--> DEAD (too many failures)
```

1. A worker picks an event using `SELECT ... FOR UPDATE SKIP LOCKED`, so two workers never get the same event.
2. The same query skips an event if an older event of the same transfer is not finished yet. This keeps the order.
3. If sending fails, the event goes back to `PENDING` and the wait doubles every time (1s, 2s, 4s ...).
4. After 5 attempts it becomes `DEAD`. An admin can requeue it later.
5. If a worker crashes, the reclaimer puts the stuck event back in the queue. A claim token makes sure the old worker can't update it anymore.

## Tech Stack

Java 17, Spring Boot 3, Spring Data JPA, Hibernate, HikariCP, PostgreSQL, Flyway, JUnit 5, Docker

## Project Structure

All the code is inside `src/main/java/com/ob`:

- `controller` - REST endpoints
- `service` - main logic, claiming events, retry backoff, reclaimer
- `repository` - database access (JPA and native SQL queries)
- `worker` - worker threads, event processor and reclaim job
- `publisher` - sends events to the webhook
- `model` - entity classes and enums
- `dto` - request and response classes
- `exception` - custom exceptions and error handling
- `security` - admin token check
- `config` - web config

## Getting Started

You need Docker installed. Then run:

```
docker compose up --build
```

This starts PostgreSQL, the app on `http://localhost:8080` and an echo server that acts as the webhook.

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/transfers` | Create a transfer |
| GET | `/api/v1/transfers/{id}` | Get a transfer |
| GET | `/api/v1/admin/outbox/dead` | List dead events (admin) |
| GET | `/api/v1/admin/outbox/{eventId}` | Get one event (admin) |
| POST | `/api/v1/admin/outbox/{eventId}/requeue` | Requeue a dead event (admin) |

Admin APIs need the `X-Admin-Token` header (default `change-me`).

## Running Tests

```
docker compose up -d postgres
mvn test
```

There are 50 tests covering the transaction rollback, event order, concurrent workers, retries,
the dead letter queue, the reclaimer and the APIs.

Benchmark with 1 and 20 workers:

```
mvn test -Dtest=ThroughputBenchmarkTest
```
