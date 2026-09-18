# KeyPass

KeyPass is a backend for sharing digital car keys: owners issue time-limited, permissioned keys
to other people, holders can delegate a narrower version of their own key to someone else, and
every unlock decision — online or completely offline — comes back with a rule-by-rule
explanation instead of a bare yes/no.

## Guarantees this project proves with tests, not just claims

- **A stolen credential is useless without the phone's private key.** Every access request must
  be signed by the device that owns the key (`AccessCheckIT.stolenCredentialFailsWithoutTheRightDeviceKey`).
- **A replayed unlock request is rejected.** Single-use nonces, checked with
  `INSERT ... ON CONFLICT DO NOTHING` (`ReplayProtectionIT`).
- **Once a revocation commits, no later access is granted** — proven under 150 concurrent
  requests racing a revocation on virtual threads, not just asserted
  (`RevocationConcurrencyIT`, ADR 0004).
- **A user can never see or change another user's keys.** Ownership checks return 404, never
  403, so a caller can't even tell the resource exists (`OwnershipSecurityIT`, ADR 0007).

## Features

- Owners issue keys with fine-grained permissions (unlock, lock, open boot, start engine,
  share), a validity window, an optional speed cap, and embedded policy rules (curfew, geofence,
  allowed weekdays).
- Holders can re-share a key, but only with permissions and a time window that are equal to or
  narrower than their own, up to 3 levels deep — enforced server-side, not just by convention.
- Revoking a key cascades to every key ever shared from it, computed in one recursive-CTE SQL
  statement.
- A car with no signal verifies keys entirely offline: a signed credential, a device signature,
  and a Bloom filter of revoked keys downloaded up to 24 hours earlier.
- Every access decision — granted or denied — is written to an append-only audit log (the
  database itself rejects `UPDATE`/`DELETE` on it) with the full rule-by-rule trace.
- Brute-force and unusual-access-time anomaly detection raises alerts asynchronously.
- A plain-Java car/phone simulator drives the real HTTP API through 9 scenarios, including four
  distinct attack types (replay, tamper, stolen credential, brute force).

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for the module layout and sequence diagrams.

```
keypass-common   — crypto (Ed25519, Bloom filter, token bucket) + domain model, no Spring
keypass-server   — the Spring Boot API
keypass-car-sim  — a plain-Java client that plays the role of phones and cars
```

## Quick start

```bash
cp .env.example .env
java scripts/GenerateSigningKey.java   # paste the two output lines into .env

docker compose up
```

Then:

- Swagger UI: <http://localhost:8080/swagger-ui.html>
- Health check: <http://localhost:8080/actuator/health>

## Running the tests

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # or wherever your Java 21 lives
./mvnw -pl keypass-common,keypass-server -am verify
```

This runs unit tests, ArchUnit architectural checks, and Testcontainers integration tests
against a real, ephemeral PostgreSQL container (Docker must be running). Measured on this build:

| Module | Tests |
|---|---|
| `keypass-common` (crypto, policy rules, Bloom filter, token bucket) | 24 |
| `keypass-server` unit + ArchUnit | 17 |
| `keypass-server` Testcontainers integration | 15 |
| **Total** | **56**, all passing |

## Running the simulator

With a server running (via `docker compose up`, or `./mvnw -pl keypass-server spring-boot:run`):

```bash
./mvnw -pl keypass-car-sim compile exec:java -Dexec.args="http://localhost:8080"
```

Prints a pass/fail summary for all 9 scenarios: normal unlock, natural expiry, revocation,
replay, stolen credential, tampered credential, geofence breach, curfew, and rate-limited brute
force.

## Security

See [`docs/threat-model.md`](docs/threat-model.md) for a full STRIDE pass. In short: proof of
possession (device signatures) stops stolen credentials, Ed25519 signatures stop tampering,
single-use nonces plus a ±30s freshness window stop replay, and ownership checks return 404
rather than 403 so an attacker can't even map out what exists.

## Design decisions

Recorded as ADRs in [`docs/adr/`](docs/adr/):

- [0001 — Java 21, Spring Boot 4, PostgreSQL, Maven](docs/adr/0001-tech-stack.md)
- [0002 — Custom Ed25519 credential instead of JWT for car keys](docs/adr/0002-custom-credential-format.md)
- [0003 — Keys are immutable; editing means revoke-and-reissue](docs/adr/0003-immutable-keys.md)
- [0004 — Pessimistic locking for access checks vs revocation](docs/adr/0004-locking.md)
- [0005 — Offline bundle expires after 24h; Bloom filter false positives deny](docs/adr/0005-offline-bundle-expiry.md)
- [0006 — Geofence as a planar JSONB polygon, not PostGIS](docs/adr/0006-geofence-planar.md)
- [0007 — 404, not 403, for another user's resources](docs/adr/0007-404-not-403.md)

[`docs/postmortem-001.md`](docs/postmortem-001.md) is a genuine root-cause writeup from this
build: Spring Boot 4 silently split Flyway's auto-configuration into a separate dependency, so
migrations never ran and the app failed at startup with a misleading "missing table" error —
caught only by actually running the app against real infrastructure, not by the (fully green)
unit test suite.

## Limitations and future work

- **mTLS between cars and the server.** The current `X-Vehicle-Key` header is a reasonable
  stand-in for a simulated fleet; real cars would authenticate with per-vehicle client
  certificates.
- **HSM/KMS-backed signing key.** `SigningKeyRegistry` holds the server's private key in
  application memory; production would keep it in AWS KMS or an HSM.
- **Redis-backed rate limiting.** The current token bucket is per-instance in-memory Caffeine,
  fine for one server, not for a horizontally scaled deployment.
- **PostGIS for geofencing at scale.** See ADR 0006.
- **Load testing and AWS deployment** are deliberately not included here. Both need real,
  measured numbers rather than guessed ones — a Gatling simulation of the access-check endpoint
  and an optional single-instance EC2 + Docker Compose deployment are the natural next steps,
  documented but not run as part of this build.

## Interview questions this project is meant to prepare for

Walk through a full online unlock decision end to end. Why a device signature *and* a signed
credential? How is replay actually prevented, and why isn't a timestamp check alone enough? Why
pessimistic locking instead of optimistic here, and what would break without it? What's the
complexity of the Bloom filter, and what does a false positive mean in this system specifically?
Why does ray casting get geofences wrong sometimes, and when? How would this scale to millions of
cars? What happens if the signing key leaks? Why 404 instead of 403? What's one real bug you hit
building this, and how did you find it? (See the post-mortem.)
