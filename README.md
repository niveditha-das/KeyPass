# KeyPass

[![ci](https://github.com/niveditha-das/KeyPass/actions/workflows/ci.yml/badge.svg)](https://github.com/niveditha-das/KeyPass/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-blue)
![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4-brightgreen)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16-336791)

A backend for sharing digital car keys. Owners issue time-limited, permissioned keys to other
people. Holders can delegate a narrower version of their own key onward. Every unlock decision,
online or fully offline, comes back with a rule-by-rule explanation instead of a bare yes/no.

```jsonc
// POST /api/v1/vehicles/{vin}/access-checks  ->  200
{
  "decision": "DENIED",
  "reason": "CURFEW",
  "maxSpeedKmh": null,
  "trace": [
    { "rule": "VALIDITY",   "passed": true,  "reason": "ok" },
    { "rule": "PERMISSION", "passed": true,  "reason": "ok" },
    { "rule": "CURFEW",     "passed": false, "reason": "Engine start blocked during curfew 22:00–06:00" }
  ]
}
```

## Contents

- [Guarantees](#guarantees)
- [Features](#features)
- [How it works](#how-it-works)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Testing](#testing)
- [Attack simulator](#attack-simulator)
- [Load testing](#load-testing)
- [Observability](#observability)
- [Security](#security)
- [Deployment](#deployment)
- [Design decisions](#design-decisions)
- [Limitations and future work](#limitations-and-future-work)

## Guarantees

These are enforced by tests that fail if the behaviour breaks, not just described in prose.

| Guarantee | Mechanism | Proven by |
|---|---|---|
| A stolen credential is useless without the phone's private key | Every request must carry a signature from the device the key was issued to | `AccessCheckIT` |
| A replayed unlock request is rejected | Single-use nonces via `INSERT ... ON CONFLICT DO NOTHING`, plus a ±30 s freshness window | `ReplayProtectionIT` |
| Once a revocation commits, no later access is granted | Pessimistic row locking (ADR 0004) | `RevocationConcurrencyIT`: 150 concurrent requests racing a revocation on virtual threads |
| A user can never see or change another user's keys | Ownership checks that return 404, never 403 (ADR 0007) | `OwnershipSecurityIT` |
| The audit log cannot be rewritten | A database trigger rejects `UPDATE` and `DELETE` | `AuditAppendOnlyIT` |
| Secrets never reach the logs | Log output is asserted against known secret values | `NoSecretsInLogsIT` |

## Features

- **Fine-grained keys.** Permissions (unlock, lock, open boot, start engine, share), a validity
  window, an optional speed cap, and embedded policy rules: curfew, geofence and allowed weekdays.
- **Delegation that can only narrow.** A holder can re-share a key with permissions and a time
  window equal to or narrower than their own, up to 3 levels deep. This is enforced server-side.
- **Cascading revocation.** Revoking a key revokes everything shared from it, computed in a single
  recursive-CTE statement.
- **Offline verification.** A car with no signal checks a signed credential, a device signature and
  a Bloom filter of revoked keys downloaded up to 24 hours earlier. It fails safe: a Bloom filter
  false positive denies rather than grants.
- **Explainable decisions.** Every check returns the full rule-by-rule trace, and the same trace is
  written to the audit log.
- **Anomaly detection.** Brute-force and unusual-access-time alerts are raised asynchronously.
- **Per-key rate limiting.** A token bucket allows 10 requests per minute per key.

## How it works

```
keypass-common   crypto (Ed25519, Bloom filter, token bucket) and the domain model; no Spring
keypass-server   the Spring Boot API
keypass-car-sim  a plain-Java client that plays the part of phones and cars
load-tests       Gatling simulations (a separate Maven project, outside the main reactor)
```

`keypass-common` has no Spring dependency (enforced by an ArchUnit test) so the simulator runs
the exact crypto and credential code the server uses.

An online unlock, briefly:

1. The phone fetches a **signed credential** for its key (Ed25519, issued by the server).
2. The car generates a random challenge. The phone signs `vin | command | challenge | timestamp`
   with its **device key**.
3. The car sends the credential and the device signature to the server.
4. The server verifies both signatures, consumes the nonce, takes a row lock on the key, evaluates
   the policy rules in order, and writes an audit row.

Sequence diagrams for the online and offline flows are in
[`docs/architecture.md`](docs/architecture.md).

## Quick start

Requirements: Docker, and Java 21 (only to generate a signing key).

```bash
cp .env.example .env
java scripts/GenerateSigningKey.java   # paste the two output lines into .env
docker compose up
```

Then open:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health: <http://localhost:8080/actuator/health>
- Runnable request examples: [`docs/api-examples.http`](docs/api-examples.http)

Port 5432 must be free, because the compose file publishes PostgreSQL there.

To run the server from source instead, start Postgres (`docker compose up db`) and run
`./mvnw -pl keypass-server spring-boot:run`.

## Configuration

| Variable | Purpose | Default |
|---|---|---|
| `DB_PASSWORD` | PostgreSQL password | `keypass` for local runs |
| `KEYPASS_SIGNING_KEY` | Base64 Ed25519 private key that signs credentials | ephemeral key generated at startup |
| `KEYPASS_SIGNING_PUBLIC_KEY` | Matching public key (required whenever the private key is set) | none |
| `KEYPASS_SIGNING_KID` | Key id stamped into issued credentials | `kid-dev-1` |
| `SPRING_PROFILES_ACTIVE` | Set to `prod` to require a signing key and disable Swagger | none |

Without `KEYPASS_SIGNING_KEY` the server generates a throwaway key, so credentials stop verifying
after a restart. That is fine for a first run and wrong for anything else, which is why the `prod`
profile refuses to start without one.

## Testing

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # or wherever Java 21 lives
./mvnw verify
```

Docker must be running: the integration tests use Testcontainers against a real, ephemeral
PostgreSQL. The build also enforces an 80% line-coverage gate on the policy, access, key and
revocation packages, and a Spotless formatting check.

| Suite | Tests |
|---|---|
| `keypass-common`: crypto, policy rules, Bloom filter, token bucket | 28 |
| `keypass-server`: unit, web-layer slices, ArchUnit | 42 |
| `keypass-server`: Testcontainers integration | 17 |
| `keypass-car-sim`: API client and request signing | 9 |
| **Total** | **96** |

CI (`.github/workflows/ci.yml`) runs the build, CodeQL, a container image build with a Trivy scan,
and publishes an SBOM. A separate workflow runs OWASP dependency checks.

## Attack simulator

With a server running:

```bash
./mvnw -DskipTests -pl keypass-common install      # once
./mvnw -pl keypass-car-sim exec:java -Dexec.args="http://localhost:8080"
```

It drives the real HTTP API through nine scenarios and prints a pass/fail summary:

| Scenario | Expected result |
|---|---|
| Normal unlock | `GRANTED` |
| Key past its expiry | `DENIED` (`VALIDITY`) |
| Revoked key | `DENIED` (`STATUS`) |
| Replayed request | `DENIED` (`REPLAY_DETECTED`) |
| Stolen credential, wrong device | `DENIED` (`BAD_DEVICE_SIGNATURE`) |
| Tampered credential | `DENIED` (`INVALID_CREDENTIAL`) |
| Outside the geofence | `DENIED` (`GEOFENCE`) |
| Engine start during curfew | `DENIED` (`CURFEW`) |
| Brute force on one key | `DENIED` (`RATE_LIMITED`) |

## Load testing

Numbers below are measured, not estimated. Full setup and method are in
[`docs/load-test-results.md`](docs/load-test-results.md). Environment: Apple M4, with the server and
PostgreSQL both running locally.

| Simulation | Profile | Result |
|---|---|---|
| `AccessCheckSimulation` | 400 keys, 50 req/s sustained | 1,755 requests, **0 failures, p95 89 ms**, p99 103 ms |
| `RateLimiterStressSimulation` | 5 keys, 50 req/s (each key far over its limit) | 1,755 requests: **80 granted, 1,675 `RATE_LIMITED`**, 0 server errors, p95 66 ms |

The stress simulation fails the run if grants exceed what the token bucket can legitimately allow
(85 in that configuration), so it doubles as a regression test for the limiter.

```bash
./mvnw -DskipTests -pl keypass-common install      # once
cd load-tests
mvn gatling:test -Dgatling.simulationClass=com.keypass.loadtest.AccessCheckSimulation \
    -Dkeypass.baseUrl=http://localhost:8080
# or: -Dgatling.simulationClass=com.keypass.loadtest.RateLimiterStressSimulation
```

The load tests live outside the main reactor because they need a server you started yourself and
should not compete with a build for CPU while they measure timing.

## Observability

Start Prometheus alongside the app with `docker compose --profile monitoring up`. It scrapes
`/actuator/prometheus`, which exposes:

- `keypass_access_decisions_total{decision,reason}`: every access decision, by outcome
- `keypass_access_check_seconds`: access-check latency
- counters for revocations and alerts

Every log line carries a request id, and log output is tested to contain no secrets.

## Security

[`docs/threat-model.md`](docs/threat-model.md) is a full STRIDE analysis. In short:

- Device signatures (proof of possession) stop stolen credentials.
- Ed25519 signatures stop tampering; there is no `alg` header to confuse (ADR 0002).
- Single-use nonces and a ±30 s window stop replay. The signed message also binds the VIN and the
  command, so a captured request cannot be replayed against another car or action.
- Ownership checks return 404 rather than 403, so an attacker cannot map what exists.
- The audit log is append-only at the database level.

## Deployment

[`docs/deployment.md`](docs/deployment.md) is a runbook for a single EC2 instance:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --build
```

The production overlay enables the `prod` profile, puts Caddy in front for automatic HTTPS, and
publishes no database or application port. `deploy/fetch-secrets.sh` pulls secrets from AWS SSM
Parameter Store. The runbook is prepared and checked with `docker compose config`, but has not
been executed against a live AWS account.

## Design decisions

Architecture decision records in [`docs/adr/`](docs/adr/):

| ADR | Decision |
|---|---|
| [0001](docs/adr/0001-tech-stack.md) | Java 21, Spring Boot 4, PostgreSQL, Maven |
| [0002](docs/adr/0002-custom-credential-format.md) | A custom Ed25519 credential instead of JWT for car keys |
| [0003](docs/adr/0003-immutable-keys.md) | Keys are immutable; editing means revoke and reissue |
| [0004](docs/adr/0004-locking.md) | Pessimistic locking between access checks and revocation |
| [0005](docs/adr/0005-offline-bundle-expiry.md) | Offline bundles expire after 24 h; Bloom filter false positives deny |
| [0006](docs/adr/0006-geofence-planar.md) | Geofences as planar JSONB polygons, not PostGIS |
| [0007](docs/adr/0007-404-not-403.md) | 404, not 403, for another user's resources |

[`docs/postmortem-001.md`](docs/postmortem-001.md) is a root-cause write-up of a real failure:
Spring Boot 4 split Flyway's auto-configuration into a separate dependency, so migrations silently
never ran and the app failed at startup with a misleading "missing table" error, while the whole
unit test suite stayed green. [`docs/dev-log.md`](docs/dev-log.md) covers smaller issues.

## Limitations and future work

- **mTLS between cars and the server.** Cars authenticate with an `X-Vehicle-Key` header, which is
  a reasonable stand-in for a simulated fleet. Real cars would use per-vehicle client certificates.
- **HSM or KMS-backed signing key.** `SigningKeyRegistry` holds the private key in application
  memory. Production should keep it in AWS KMS or an HSM.
- **Shared rate limiting.** The token bucket is in-memory per instance (Caffeine), so it holds for
  one server but not across a horizontally scaled deployment. That needs Redis.
- **PostGIS for geofencing.** The planar polygon check is approximate near the poles and the
  antimeridian (ADR 0006).
- **Executed AWS deployment.** The runbook exists; standing it up needs a real account.
