## What

<!-- What does this change do, in a sentence or two? -->

## Why

<!-- Why is this needed? Link an issue if there is one. -->

## How was this tested?

- [ ] `./mvnw -pl keypass-common,keypass-server -am verify` passes locally
- [ ] Ran the car simulator against a local server, if this touches the access-check flow
- [ ] Manually exercised the change via Swagger UI / `docs/api-examples.http`

## Checklist

- [ ] No secrets, credentials or signing keys committed
- [ ] New behavior has test coverage
- [ ] Docs (README / ADR / threat model) updated if this changes a design decision
