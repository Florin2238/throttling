# API Throttling

A Spring Boot service that demonstrates API rate limiting. It exposes two endpoints, `/foo` and `/bar`, each
protected by a different rate limiting algorithm that I implemented from scratch. Every client has its own
configurable limit, and the counters can be kept in memory or in PostgreSQL.

| Endpoint   | Algorithm            |
|------------|----------------------|
| `GET /foo` | Token Bucket         |
| `GET /bar` | Sliding Window (log) |

## Technologies

- Java 25+ (developed on JDK 26), Spring Boot 4.1, Maven (wrapper included, no installation needed)
- PostgreSQL 17 through Docker Compose, accessed with Spring JDBC
- JUnit 5 and MockMvc for the tests
- No third-party rate limiting library: the algorithms and the storage layer are my own code

## What it does

- Every request needs `Authorization: Bearer <client-id>`. A missing or unknown client gets `401`.
- Within the limit: `200 {"success":true}`. Over the limit: `429 {"error":"rate limit exceeded"}` with a `Retry-After` header.
- Limits are configured per client in `src/main/resources/application.yaml`
  (`client-1`: 5 requests per 10 s, `client-2`: 3 requests per 10 s). Each endpoint counts separately.
- Storage is chosen with `throttling.store.type`: `memory` (a `ConcurrentHashMap`, lost on restart) or `postgres`
  (persistent, atomic updates through a row lock).

## Run

```bash
./mvnw test                      # run the tests
./mvnw spring-boot:run           # start with in-memory storage, on http://localhost:8080
```

For PostgreSQL storage (needs Docker):

```bash
docker compose up -d
THROTTLING_STORE_TYPE=postgres ./mvnw spring-boot:run
```

On Windows use `mvnw.cmd`, and set the variable first (`$env:THROTTLING_STORE_TYPE="postgres"` in PowerShell).
The startup log shows which store is active, and the table is created automatically.

## How to test it

**1. Two clients, both endpoints.** Start the service from a clean state and run `./demo.sh`
(needs `bash` and `curl`). Each line sends the limit plus two requests:

```text
client-1  /foo  -> 200 200 200 200 200 429 429
client-1  /bar  -> 200 200 200 200 200 429 429
client-2  /foo  -> 200 200 200 429 429
client-2  /bar  -> 200 200 200 429 429
```

Clean state means: restart the service in memory mode, or run
`docker compose exec postgres psql -U throttling -d throttling -c "TRUNCATE rate_limit_state;"` in PostgreSQL mode.
Run the demo once with each storage mode.

**2. The two algorithms behave differently.** From a clean state:

```bash
H="Authorization: Bearer client-1"
for i in 1 2 3 4 5; do
  curl -s -o /dev/null http://localhost:8080/foo -H "$H"
  curl -s -o /dev/null http://localhost:8080/bar -H "$H"
done
sleep 2
echo -n "/foo after 2s: "; curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/foo -H "$H"
echo -n "/bar after 2s: "; curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/bar -H "$H"
```

Expected: `/foo` answers `200` (the token bucket refilled one token), `/bar` answers `429` (all five requests are
still inside the 10 s sliding window).

**3. In-memory versus persistent.** Set `window-seconds: 60` for `client-1`, start the service, send 5 requests to
`/bar` as `client-1`, restart the service, then send one more:

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/bar -H "Authorization: Bearer client-1"
```

With `memory` the answer is `200` (the counters were lost). With `postgres` it is `429` (they survived). You can see
the saved state with
`docker compose exec postgres psql -U throttling -d throttling -c "SELECT * FROM rate_limit_state;"`.
Set `window-seconds` back to `10` afterwards.

## Design notes

- `Store` is a small interface with one atomic operation, `update(key, function)`. The limiters keep their state as a
  string, so any algorithm works with any store. `InMemoryStore` uses `ConcurrentHashMap.compute`; `PostgresStore`
  uses a transaction with `SELECT ... FOR UPDATE`.
- Limiters and stores are plain Java. Spring is only used for the HTTP layer (interceptors for authentication and
  rate limiting) and for wiring in `RateLimitConfig`.
- I chose PostgreSQL as the persistent store because it is durable by default, atomic, and easy to demonstrate.
  In production I would use Redis for the counters (faster, built-in expiry) and keep PostgreSQL for long-lived data.
- The sliding window log is exact but keeps one timestamp per accepted request; for large limits a sliding window
  counter would use constant memory.
- Not included: cloud deployment and an automated test of `PostgresStore` against a real database (Testcontainers
  would be the natural addition; the PostgreSQL mode is covered by the manual scenarios above).