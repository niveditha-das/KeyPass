# Load test results

Measured, not estimated — see [`load-tests/`](../load-tests) for the Gatling simulation itself.

## Setup

- **Endpoint under test**: `POST /vehicles/{vin}/access-checks` — the single hottest endpoint in
  the system, and the one doing the most work per request: server-signature verification,
  device-signature verification (proof of possession), a nonce insert (replay check), a
  `FOR SHARE` row lock on the key, full policy evaluation, and an audit-log write.
- **Seed data**: 400 distinct vehicles, each with its own owner/holder/device and one active
  `UNLOCK` key, created through the real HTTP API (not a SQL seed script) before the injection
  profile starts. 400 keys were chosen specifically so the average per-key request rate stays
  under the 10-requests/minute-per-key rate limit — see the note in
  [`AccessCheckSimulation.java`](../load-tests/src/test/java/com/keypass/loadtest/AccessCheckSimulation.java)
  and [`dev-log.md`](dev-log.md).
- **Injection profile**: ramp from 1 to 50 requests/second over 10 seconds, then hold 50
  requests/second for 30 seconds.
- **Machine**: Apple M4, 16 GB RAM, macOS 15.6. Server (Spring Boot, virtual threads enabled)
  and PostgreSQL 16 (Docker) both running locally on the same machine — no network latency
  between client, server and database. A deployed, separately-hosted setup would see higher
  latency from real network hops; these numbers isolate the application and database work itself.
- **Command**:
  ```bash
  cd load-tests
  mvn gatling:test -Dgatling.simulationClass=com.keypass.loadtest.AccessCheckSimulation \
      -Dkeypass.baseUrl=http://localhost:8080
  ```

## Results

| Metric | Value |
|---|---|
| Total requests | 1,755 |
| Failed requests | 0 (100% success) |
| Mean throughput | 42.8 req/s |
| Min response time | 71 ms |
| Mean response time | 77 ms |
| p50 | 74 ms |
| p95 | 89 ms |
| p99 | 103 ms |
| Max response time | 138 ms |

Every request in the run completed in under 800ms; the p95/p99 spread (89ms / 103ms) is tight,
suggesting no significant tail-latency outliers from lock contention or GC pauses at this load
level.

## Interpreting these numbers honestly

- This is a **local, single-machine measurement**, not a production capacity claim. It isolates
  the application and database logic from network and infrastructure variables a real deployment
  would add.
- 50 requests/second sustained is well within what the access-check flow handles cleanly at this
  scale (400 keys, one PostgreSQL instance, one server instance). It was not pushed to find a
  breaking point — the goal here was a real, honest baseline number rather than a stress-test
  ceiling, in keeping with the project's own rule against citing unmeasured numbers.
- The rate limiter (`RateLimiter`, 10 requests/minute per key) was deliberately kept *out* of the
  failure path by seeding enough keys that no single key's request rate approached its limit —
  see the reasoning in `dev-log.md`. A test specifically targeting the rate limiter (fewer keys,
  same throughput) would be a natural follow-up to demonstrate brute-force protection holding
  under sustained load, rather than just under the simulator's 15-attempt scenario.
