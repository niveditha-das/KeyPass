# 0004: Pessimistic locking for access checks vs revocation

Status: Accepted

## Context

An access check and a revocation can race: a car might be mid-check on a key at the exact
moment its owner revokes it. The system must never grant access after the revoking transaction
has committed, and this has to hold under real concurrency, not just "in practice it's fast
enough."

## Decision

An access check takes `PESSIMISTIC_READ` (`FOR SHARE`) on the `digital_key` row before
evaluating policy (`DigitalKeyRepository.findByIdForShare`). Revocation takes
`PESSIMISTIC_WRITE` (`FOR UPDATE`) on the root key before the recursive-CTE cascade
(`findByIdForUpdate` in `KeyRevocationService`).

PostgreSQL's lock semantics do the rest: `FOR SHARE` locks can coexist with each other, but
`FOR UPDATE` waits for every existing `FOR SHARE` holder to finish, and blocks new `FOR SHARE`
attempts until it commits. So:

- Every access check that's already holding the share lock when a revocation starts finishes
  (grants or denies, and writes its audit row) before the revocation can proceed.
- Every access check that starts after the revocation commits reads the row with
  `status = 'REVOKED'` and is denied.

## Consequences

Revocation now waits (milliseconds, not seconds) for in-flight checks on that specific key. The
guarantee is provable, not just probable: `RevocationConcurrencyIT` fires 150 concurrent
requests at a key on virtual threads while a revocation races them, then asserts (by comparing
audit-row IDs, since a grant's row is always written before the revocation's while the lock is
held) that zero grants have an ID after the revocation's. Removing the lock and rerunning that
test should make it fail intermittently — that's deliberate, as evidence the lock is doing real
work and not just decoration.

## Alternatives considered

- **Optimistic locking** (`@Version`, retry on conflict): the row does have a `version` column
  for other purposes, but optimistic locking here would mean an access check could still read a
  stale (not-yet-revoked) row and grant, then fail to *write* — by which point the phone has
  already unlocked the car. The guarantee has to be enforced before the decision, not after.
- **A Redis-backed distributed lock**: real overkill for a single-database system, and adds a
  second system that itself needs to be consistent with Postgres.
