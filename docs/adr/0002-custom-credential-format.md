# 0002: Custom Ed25519 credential instead of JWT for car keys

Status: Accepted

## Context

A car key needs to be verifiable by a car that has no network connection at all (offline mode),
carries no secrets a thief could extract and reuse elsewhere, and is small enough to live
comfortably on a phone. The system already uses JWTs for user login — the question is whether
the *car key* itself should also be a JWT.

## Decision

Car keys are a custom format: `base64url(payloadJson) + "." + base64url(Ed25519 signature)`,
implemented in `keypass-common`'s `CredentialCodec`. User login tokens remain JWTs, signed
RS256 by `JwtEncoder`.

## Consequences

- No algorithm-confusion attacks (a well-known JWT footgun: a token crafted with `alg: none`,
  or an RS256 token re-signed as HS256 using the public key as an HMAC secret). The verifier
  here only ever tries Ed25519.
- Smaller payload than a JWT with equivalent claims, and trivially verifiable offline — the car
  just needs the server's Ed25519 public key, published at `/.well-known/keypass-keys`.
- The trade-off: no ecosystem of JWT libraries or tooling to lean on. `CredentialCodec` is about
  80 lines and is fully covered by `CredentialCodecTest`, so this was judged worth it.

## Alternatives considered

- **JWT (RS256) for both**: simpler mentally (one token format everywhere), but pulls in a JWT
  library on the car/phone side purely to check one signature, and the algorithm-confusion
  attack surface exists (mitigated but not eliminated by pinning `alg`).
- **JWE (encrypted JWT)**: unnecessary — a car key isn't a secret that needs confidentiality,
  it's a claim that needs integrity and authenticity. Signing (not encrypting) is the correct
  primitive.
