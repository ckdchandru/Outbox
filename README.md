# Outbox

Spring Boot service that publishes events reliably using the transactional outbox pattern.

## The Problem

Say a transfer is saved to the database and then an event is sent to another service. If the app crashes
right after the save, the event is never sent. If the event is sent but the save rolls back, the other
service now knows about a transfer that doesn't exist. You can't wrap a database write and an HTTP call
in one transaction, so there is always a moment where things can go wrong.

## My Solution

I don't send the event directly. Instead I write it into an `outbox_events` table in the same transaction
as the transfer. Then separate worker threads read that table and deliver the events to a webhook.

```
POST /api/v1/transfers
      |
      v
+----------------- one transaction -----------------+
|  transfer row                                     |
|  outbox event: TRANSFER_INITIATED                 |
|  outbox event: TRANSFER_COMPLETED                 |
+---------------------------------------------------+
      |
      v
worker threads --(SKIP LOCKED)--> webhook
```

If the transaction fails, nothing is saved, not even the events. If it commits, the events are in the
database and the workers will pick them up, even if the app restarts in between.

## Guarantees

- **No lost events** - events are stored together with the data they belong to
- **Order per transfer** - every event has a `sequence_no`, and a worker won't pick an event while an older one of the same transfer is still pending or being sent. So `TRANSFER_COMPLETED` waits for `TRANSFER_INITIATED`. The only exception is when the older event goes `DEAD`, then it stops blocking so one bad event can't hold up the rest
- **No double pick** - workers claim rows with `SELECT ... FOR UPDATE SKIP LOCKED`, so many workers can run in parallel safely
- **At-least-once delivery** - an event can be sent twice in rare cases, so every request has an `X-Event-Id` header the receiver can use to ignore duplicates

When things fail:

- Failed sends are retried with exponential backoff and jitter (1s, 2s, 4s ...)
- After 5 attempts the event is marked `DEAD` and can be requeued through an admin API
- If a worker dies while sending, the event stays stuck in `PROCESSING`. A reclaimer job runs every 30 seconds and puts events stuck for more than 5 minutes back in the queue (or marks them `DEAD` if they have no attempts left). A claim token stops the old worker from updating it later

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

## Run It

```
docker compose up --build
```

Starts PostgreSQL, the app (port 8080) and an echo server used as the webhook (port 9000).
Use `docker compose logs webhook` to see the events it received.

## API

| Method | Endpoint | What it does |
|--------|----------|--------------|
| POST | `/api/v1/transfers` | Create a transfer, body: `{"amount": 300}` |
| GET | `/api/v1/transfers/{id}` | Get a transfer |
| GET | `/api/v1/admin/outbox/dead` | List dead events |
| GET | `/api/v1/admin/outbox/{eventId}` | See one event |
| POST | `/api/v1/admin/outbox/{eventId}/requeue` | Put a dead event back in the queue |

Admin endpoints need the header `X-Admin-Token: change-me`.

## Tests

```
docker compose up -d postgres
mvn test
```

50 tests in total. Some of them check that a failed transfer leaves no events behind, that order is kept
with 10 workers running at once, and that a slow worker can't overwrite an event after it was reclaimed.

There is also a benchmark that processes 1000 events with 1 worker and then with 20. It uses a fake
in-memory publisher instead of the real webhook, so it measures the database and worker side only:

```
mvn test -Dtest=ThroughputBenchmarkTest
```
