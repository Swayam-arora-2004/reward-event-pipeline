# rewards-event-pipeline

A purchase event is published to Kafka. A consumer credits reward points in PostgreSQL. The same event delivered twice credits points **once**. An event that keeps failing is moved to a dead-letter topic.

![CI](https://github.com/Swayam-arora-2004/reward-event-pipeline/actions/workflows/ci.yml/badge.svg)

## Architecture

`POST /purchases` validates the body and publishes a JSON `PurchaseEvent` to the Kafka topic `purchases` (key = `memberId`, so one member's events stay in order); it answers `202` only after Kafka has acknowledged the write. A `@KafkaListener` consumer reads each message, validates it, and calls `PointsService`, which in **one database transaction** inserts the `eventId` into `processed_event` (primary key) and adds `floor(amountRupees / 10)` points to `member_points`. Delivery is at-least-once; the unique `eventId` plus the single transaction makes the *effect* happen once. Stack: Java 21, Spring Boot 3.5.16, Spring Kafka, Spring Data JPA, PostgreSQL, Flyway, Testcontainers.

```
client --POST--> controller --> producer --> [purchases] --> consumer --> PointsService --> PostgreSQL
                                                  |  3 failed attempts                   (processed_event + member_points,
                                                  v                                       one transaction)
                                           [purchases.DLT]
```

## Run it

Needs Java 21, Maven, Docker.

```bash
cp .env.example .env            # then replace every CHANGE_ME
docker compose up -d            # Kafka + PostgreSQL on 127.0.0.1
mvn spring-boot:run             # app on http://127.0.0.1:8080
```

If port 5432 is already used on your machine, set `POSTGRES_HOST_PORT=5433` and `DB_PORT=5433` in `.env`.

```bash
curl -i -X POST http://127.0.0.1:8080/purchases \
  -H 'Content-Type: application/json' \
  -d '{"eventId":"e-1","memberId":"m-1","amountRupees":99}'
# HTTP/1.1 202   {"eventId":"e-1","status":"accepted"}   -> 9 points
```

## API

`POST /purchases` body `{eventId, memberId, amountRupees}`.
- ids: 1-64 characters from `A-Z a-z 0-9 . _ -`; `amountRupees`: whole number, 1 to 1,000,000,000.
- Unknown fields, decimals and numbers sent as strings are rejected with `400` and a short JSON error (no internals).
- `503` if Kafka did not acknowledge the write.

## Topics

| Topic | Purpose |
|---|---|
| `purchases` | purchase events, key = `memberId` |
| `purchases.DLT` | messages that failed 3 attempts |

## Duplicates and failures

- **Duplicate:** a repeated `eventId` is skipped and logged as `Duplicate event ignored`; the balance changes once and `processed_event` keeps one row.
- **Failure:** an unreadable or invalid message gets 3 attempts with a 1 second fixed backoff (configurable in `application.yml`), then is published to `purchases.DLT` by Spring Kafka's `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`.

## Tests

`mvn verify` starts real Kafka and PostgreSQL with Testcontainers (Docker must be running). It covers the points rule (99 -> 9, 100 -> 10, 5 -> 0), request validation, a normal credit, the same `eventId` twice, and an invalid event ending on `purchases.DLT` after 3 attempts.

## Load script

```bash
python3 scripts/load_test.py 1000     # app and docker compose must be running
```

Sends N events (default 1000) with about 10% repeated `eventId`s, then compares the database with a total computed independently from the unique events.

## Measured results

From a clean clone, `mvn verify`: **20 tests, 0 failures, BUILD SUCCESS**; JaCoCo line coverage **88.1 %** (104 of 118 lines).

Load run (1000 events, local Docker Kafka + PostgreSQL):

| Measure | Value |
|---|---|
| events sent | 1000 |
| unique events | 900 |
| duplicates ignored | 100 |
| expected_total | 225283 |
| credited_total | 225283 |
| double-credits | 0 |
| elapsed seconds (send + drain) | 3.68 |

Single run on one laptop; not a benchmark.

## Limits

Whole-rupee `long` amounts (a real system would use minor units or `BigDecimal`); one broker, one consumer instance; Kafka and PostgreSQL are unauthenticated/dev-only; invalid messages are retried even though retrying cannot fix them.
