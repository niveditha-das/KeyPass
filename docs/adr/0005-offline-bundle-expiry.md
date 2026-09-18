# 0005: Offline bundle expires after 24 hours; Bloom filter false positives deny access

Status: Accepted

## Context

A car with no signal (an underground car park, a rural area) can't ask the server "is this key
still valid?" in real time. It needs to make that call locally, using whatever it downloaded the
last time it had signal — the offline bundle (`OfflineBundleService`): the server's public keys
and a Bloom filter of currently-revoked key IDs for that vehicle.

Two questions this raises: how stale can that bundle be before the car should stop trusting it,
and what should happen when the Bloom filter gives an ambiguous answer?

## Decision

- The bundle carries `issuedAt` and `validUntil` (`issuedAt + 24 hours`); a car past
  `validUntil` refuses offline access entirely rather than trust a list that old.
- A Bloom filter has no false negatives but can have false positives (`BloomFilterTest` proves
  the first property over 10,000 items; the second is inherent to the structure). A false
  positive here means "the filter says this key *might* be revoked, but it might genuinely be
  fine" — and the car treats that as a denial.

## Consequences

A rare, wrongly-denied key (the false-positive case, tuned to about 1% via the filter's target
false-positive rate) is a real but small inconvenience. A wrongly-accepted key — letting someone
in on a key that's actually been revoked — is the failure this system exists to prevent, so it's
the one direction the design refuses to be wrong in. `OFFLINE_USE_AFTER_REVOKE` alerts (raised
when an uploaded offline event shows a key used after its recorded revocation time) are the
signal that the 24-hour window mattered in practice.

## Alternatives considered

- **A plain revoked-ID list instead of a Bloom filter**: for a single car with a handful of
  keys, a plain set would be simpler and just as correct — the Bloom filter's compactness only
  earns its complexity at fleet scale, where one filter can cover thousands of revoked keys in a
  few kilobytes. It's implemented here because the guide calls for it and because sizing and
  false-positive-rate tuning are themselves useful interview material, but a smaller single-car
  deployment would reasonably choose the simpler structure instead.
- **No expiry (trust the bundle forever)**: unacceptable — a car that syncs once and is then
  taken permanently offline would keep honoring keys revoked the next day.
