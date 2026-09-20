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
  failure path here by seeding enough keys that no single key's request rate approached its
  limit — see the reasoning in `dev-log.md`. The opposite case is measured in the next section.

## Rate-limiter stress test

[`RateLimiterStressSimulation`](../load-tests/src/test/java/com/keypass/loadtest/RateLimiterStressSimulation.java)
inverts the setup above: **5 keys** instead of 400, at the same 50 requests/second profile, so
each key is hit at roughly 10 requests/second against an allowance of 10 per *minute* — what a
brute-force attacker replaying one stolen credential looks like. Same machine and injection
profile as above.

```bash
cd load-tests
mvn gatling:test -Dgatling.simulationClass=com.keypass.loadtest.RateLimiterStressSimulation \
    -Dkeypass.baseUrl=http://localhost:8080
```

| Metric | Value |
|---|---|
| Total requests | 1,755 |
| Granted | 80 |
| Denied `RATE_LIMITED` | 1,675 |
| Other denials | 0 |
| Upper bound the limiter should allow (5 keys x (10 burst + 40s / 6s refill + 1)) | 85 |
| HTTP failures (non-200) | 0 |
| p50 / p95 / p99 | 62 ms / 66 ms / 74 ms |
| Max response time | 104 ms |

The limiter held: 80 grants against a ceiling of 85, and 95.4% of the traffic was rejected
without a single server error. Rejections were also *cheaper* than grants (p95 66 ms vs 89 ms),
because a rate-limited request is denied before the device-signature check, the nonce insert and
the row lock. The simulation fails the run if grants exceed the bound or if nothing is rate
limited at all, so it is a regression test for the limiter, not just a benchmark. As before,
this is a single-instance, local measurement; a multi-instance deployment would need the shared
rate-limit store described in the README before this guarantee held across instances.
