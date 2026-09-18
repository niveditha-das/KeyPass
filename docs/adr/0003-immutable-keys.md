# 0003: Keys are immutable — editing a key means revoking it and issuing a new one

Status: Accepted

## Context

Once a key is issued, could its permissions, validity window or policy ever be edited in place?
That would be convenient for owners ("just extend Alice's key by a week"), but it complicates
offline mode: a car that's out of signal has no way to learn about an edit, only a revocation
(via the Bloom filter it already downloaded).

## Decision

`DigitalKey` has no setters for `permissions`, `notBefore`, `notAfter` or `policy` — only
business methods for status transitions (`suspend()`, `resume()`), plus revocation. "Editing" a
key in the API means calling `POST /keys/{id}/revoke` and then issuing a brand new key.

## Consequences

- An offline car only ever needs one kind of update to stay correct: a growing revocation list.
  It never needs to reconcile "the permissions on key X changed while I was offline."
- Every issued key's terms are permanently visible in its row (and in the audit trail via
  `KEY_ISSUED`/`KEY_REVOKED` events), which is good for the append-only audit story.
- The cost: revoking and reissuing a key also revokes anything shared from it (cascading
  revocation, ADR 0004), which is the correct behavior for a genuine permission change but means
  "extend Alice's key by a week" also asks Alice to re-share to anyone she'd delegated to.

## Alternatives considered

- **Versioned keys** (an `edited_from_key_id` pointer, old version still technically valid until
  synced): adds real complexity for a benefit — convenience — that doesn't outweigh the offline
  correctness story immutability gives for free.
