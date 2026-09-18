# Threat model

A STRIDE pass over KeyPass, focused on the access-check flow since that's where a mistake has
the most direct real-world consequence (someone unlocking or driving a car they shouldn't).

| Threat | Example | Mitigation |
|---|---|---|
| **Spoofing** | A stolen credential is used from a different phone | Proof-of-possession: the request must be signed by the device's own Ed25519 private key, checked in `AccessCheckService`/`CredentialVerifier`. Possessing the credential text alone grants nothing. |
| **Tampering** | An attacker edits a credential to add `START_ENGINE` | Ed25519 signature over the exact payload bytes (`CredentialCodec`); any change to the payload invalidates the signature. |
| **Repudiation** | "I never unlocked the car" | Every decision — granted or denied — writes an append-only audit row (`AuditEvent`), including the full rule-by-rule trace, with a database trigger that rejects `UPDATE`/`DELETE` on that table. |
| **Information disclosure** | User B lists or views User A's keys | Ownership checks on every endpoint, returning 404 rather than 403 for a resource that isn't the caller's (ADR 0007), verified by `OwnershipSecurityIT`. |
| **Denial of service** | Repeated unlock attempts against one key | A per-key token bucket (`RateLimiter`) throttles to 10 requests/minute; sustained denials also raise a `BRUTE_FORCE` alert. |
| **Elevation of privilege** | A holder shares `START_ENGINE` on a key that only has `UNLOCK` | `KeyService.share` enforces that a child key's permissions, time window and depth can only ever be equal to or narrower than its parent's — never wider (`KeyShareServiceTest`). |
| **Replay** | A captured request is resent later | Single-use nonces (`NonceStore`, `INSERT ... ON CONFLICT DO NOTHING`) plus a ±30 second timestamp window; the signed message also binds in the VIN and command so a captured request can't be replayed against a different car or action. |
| **Offline revocation gap** | A key is revoked while a car is in an underground car park with no signal | The car fails safe: its offline bundle expires after 24 hours (ADR 0005), and a Bloom filter false positive denies rather than grants. An `OFFLINE_USE_AFTER_REVOKE` alert fires once the car reconnects and uploads what it did while offline. |

## Deliberately out of scope for this build

- **mTLS between cars and the server**: real cars would authenticate with a per-vehicle client
  certificate rather than the shared-secret `X-Vehicle-Key` header used here. The header is a
  reasonable stand-in for a simulated fleet, but wouldn't be the production design.
- **HSM/KMS-backed signing key**: `SigningKeyRegistry` holds the server's Ed25519 private key in
  application memory (generated fresh on startup if `KEYPASS_SIGNING_KEY` isn't set). Production
  would keep this in AWS KMS or an HSM and never let it touch application memory in plaintext.
- **Rate limiting across multiple instances**: `RateLimiter` is an in-memory Caffeine cache, so
  it only throttles per server instance. A real multi-instance deployment needs a shared store
  (Redis) for this to hold under horizontal scaling.
