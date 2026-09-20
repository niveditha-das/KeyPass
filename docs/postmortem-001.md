# Post-mortem 001: Flyway migrations silently never ran on Spring Boot 4

## Detection

Running the app for the first time against a real PostgreSQL container (not just compiling it),
`spring-boot:run` failed on startup with:

```
org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: missing table [alert]
```

`alert` isn't a special table — it's simply the first one alphabetically among the entity class
names (`Alert`, `AppUser`, `AuditEvent`, `Device`, `DigitalKey`, `Vehicle`), which was the first
clue that no tables existed at all, not just one.

## Timeline

1. `keypass-server` compiled cleanly and all Mockito/ArchUnit unit tests passed — nothing in the
   normal build caught this.
2. Started the app against a real Postgres container to smoke-test the register/login flow.
   Startup failed with the schema validation error above.
3. Searched the full startup log for the string `Flyway`. It appeared zero times — not even the
   usual `Migrating schema "public" to version "1 - init"` line. Flyway hadn't run at all.
4. `mvn dependency:tree` confirmed `flyway-core` and `flyway-database-postgresql` were both on
   the classpath, which ruled out the obvious "forgot the dependency" explanation.
5. Diffed `spring-boot-autoconfigure-4.1.1.jar`'s contents against what the same JAR carried on
   Spring Boot 3.x: it no longer contains a `FlywayAutoConfiguration` class at all. Spring Boot 4
   split several auto-configuration modules that used to ship bundled inside
   `spring-boot-autoconfigure` into their own separate artifacts.

## Root cause

Spring Boot 4 moved Flyway's auto-configuration into a new `org.springframework.boot:spring-boot-flyway`
artifact. Having `flyway-core` on the classpath is no longer sufficient by itself to get Flyway
wired into the application context — the dedicated Boot auto-configuration module has to be an
explicit dependency too. Most tutorials and Stack Overflow answers written for Boot 3 (which
is most of what exists as of this writing) assumes the older bundling and simply doesn't mention
this.

The same pattern turned out to affect Jackson too, one layer deeper: Boot 4's auto-configured
`ObjectMapper` bean is now backed by Jackson 3 (`tools.jackson.databind.ObjectMapper`), a
different type from the Jackson 2 `com.fasterxml.jackson.databind.ObjectMapper` this project's
`CredentialCodec` is written against. Rather than chase Boot's Jackson-version auto-configuration
further, `CredentialCodecConfig` now builds its own dedicated `ObjectMapper` bean — which is
arguably the more correct design anyway (see the comment in that class: a credential's signature
covers exact JSON bytes, so it shouldn't be able to drift if someone later tweaks a global
Jackson feature for API responses).

A related but unrelated-in-cause bug surfaced in the same debugging session: once Flyway *did*
run, schema validation failed again, this time on `vehicle.vin` — the migration declared it
`CHAR(17)`, which Postgres reports as `bpchar`, while the `Vehicle` entity's plain `String vin`
field validates against `VARCHAR`. Fixed by changing the migration to `VARCHAR(17)`.

## Fix

- Added `org.springframework.boot:spring-boot-flyway` as an explicit dependency.
- Gave `CredentialCodec` its own `ObjectMapper` bean instead of relying on Boot's auto-configured
  one.
- Changed `vehicle.vin` from `CHAR(17)` to `VARCHAR(17)` in `V1__init.sql`.

## Regression test

No single new test was added for this specific class of bug, because the existing Testcontainers
integration test suite already is that regression test: every `*IT` class extends
`IntegrationTest`, which boots the full Spring context against a real PostgreSQL container. If
Flyway ever silently stops running again, or the `ObjectMapper` wiring breaks again, or a column
type drifts from its entity mapping again, the very first integration test to run fails at
context startup — which is exactly what happened here, just caught by hand instead of by CI.
This is also the concrete argument for why `AccessCheckIT`, `ReplayProtectionIT` and friends run
against a real database rather than mocks: a mocked repository layer would have let every one of
these three bugs through silently.

## Lessons learned

- **A green build is not the same as a working application.** Every layer up to and including
  unit tests passed while the app was completely broken at startup. Actually running it against
  real infrastructure — even just once, by hand — caught three real bugs that no amount of
  additional unit testing would have.
- **A major framework version bump changes more than the version number.** Spring Boot 4 isn't
  additive-only over Boot 3; it restructured what ships where. When something that "should just
  work" based on well-known conventional wisdom doesn't, checking what actually shipped in the
  jar (`jar tf your-dependency.jar | grep WhatYouExpect`) is faster than guessing from outdated
  tutorials.
- **Silence is a symptom.** The absence of any `Flyway` log line was the single most useful
  diagnostic signal here — a missing log line from a component that should always announce
  itself on startup is often more informative than an error message.
