# Dev log

Short notes on real problems hit while building this and how they were resolved. The most
interesting one — Spring Boot 4's Flyway auto-configuration split — has its own full writeup in
[`postmortem-001.md`](postmortem-001.md); this log covers the smaller ones.

## Testcontainers container reuse vs. Spring's test context cache

Each `*IT` class extending `IntegrationTest` originally declared its own `@Container static
PostgreSQLContainer<?>` field via the `@Testcontainers` JUnit extension. Since every IT class
shares an identical `@SpringBootTest` configuration, Spring's test context cache reused the
*same* `ApplicationContext` (and therefore the same `DataSource`, pointing at whichever
container's port was current when that context was first built) across all of them — but a
fresh container was started per class. Later test classes got `Connection refused` against a
port their cached `DataSource` still remembered from an earlier, already-stopped container.

Fixed by switching to the documented "singleton container" pattern: one `PostgreSQLContainer`
started once in a static initializer, shared via `@DynamicPropertySource`, never explicitly
stopped (Testcontainers' Ryuk reaper cleans it up when the JVM exits). See `IntegrationTest.java`.

## `RestTestClient` replaced `TestRestTemplate`

`TestRestTemplate` no longer exists in `spring-boot-test` 4.1.1 — another casualty of the Boot 4
module split. The replacement is `org.springframework.test.web.servlet.client.RestTestClient`
(new in Spring Framework 7), and its request-body method is `.body(Object)`, not the
`WebTestClient`-style `.bodyValue(Object)` I first guessed.

## `@WebMvcTest` moved packages too

`org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` is gone; it now lives in
`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`, shipped by a new
`spring-boot-webmvc-test` artifact that has to be added as an explicit test dependency. Same
underlying cause as the Flyway and Jackson splits — by this point in the build, "check what
actually shipped in the jar" had become the default first move whenever a Boot-3-era import
stopped resolving.

## Testcontainers 2.x renamed its artifacts

`org.testcontainers:junit-jupiter` and `org.testcontainers:postgresql` (the 1.x names) became
`org.testcontainers:testcontainers-junit-jupiter` and `org.testcontainers:testcontainers-postgresql`
in Testcontainers 2.x, all now consistently prefixed. A one-line diff once the BOM's own contents
were checked, but a Maven "missing version" error over a `<dependencyManagement>` import that
*looked* correct was the initial red herring.

## Gatling's per-key rate limiter interaction

The load test's first draft seeded 200 keys and drove 50 requests/second at them uniformly at
random. `RateLimiter` allows 10 requests/minute *per key*; with 200 keys and 50 req/s, the
average per-key rate (50/200 = 0.25 req/s) sits above the 10/60 ≈ 0.167 req/s the bucket
actually sustains, so some keys would get correctly rate-limited under sustained load — which
would have shown up as load-test "failures" that were really the rate limiter doing exactly its
job. Fixed by seeding enough keys (400) that the average per-key rate stays comfortably under
the limit; see the comment in `AccessCheckSimulation.java`.

## First AWS deploy: what broke

The first run of the deploy pipeline against the real account hit several unrelated problems in
a row. In the order they appeared:

**Expired credentials hiding behind a fresh login.** `aws` kept failing with "credentials were
refreshed, but the refreshed credentials are still expired" even after logging in again. The shell
still had `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_SESSION_TOKEN` and
`AWS_CREDENTIAL_EXPIRATION` exported from an earlier session, and environment variables outrank the
profile. `env | grep ^AWS_` found it; `unset` fixed it. The login had also been done as the account
root user, which should be replaced by an IAM user.

**Trivy blocked the deploy on Jackson.** Ten HIGH findings, all in `app.jar`: denial-of-service
CVEs in both Jackson lines that Spring Boot 4.1.1 manages (2.21.5 and 3.1.5); the OS layer was
clean. Boot exposes the managed versions as `jackson-2-bom.version` and `jackson-bom.version`
(note the 3.x line owns the unsuffixed name), so overriding both in the parent `pom.xml`
(2.21.7 / 3.1.7) fixed it, confirmed with `dependency:tree` and the test suite. The scan's
`exit-code: 1` did its job; the answer was a version bump, not a looser threshold.

**A Maven Central 502 failed the image build.** `archunit-1.4.1.jar` returned `502 Bad Gateway`
mid-build; re-running the failed job passed. The Dockerfile's Maven step has no retry, so one
upstream blip fails a deploy.

**Caddy: wrong profile syntax, and a hostname instead of an IP.** `profile` is not valid directly
under `tls`; it belongs on the issuer, `tls { issuer acme { profile shortlived } }`. Separately,
the SSM parameter `/keypass/KEYPASS_DOMAIN` held `98-94-175-255.sslip.io`, not the bare IP the plan
assumed, so Caddy was requesting a hostname certificate. A TLS `internal error` alert on the bare
IP was the symptom, and the Caddy log (`certificate obtained successfully` for the sslip.io name)
and `cat -A` on the instance's `.env` explained it. Staging issuance with the shortlived profile
worked for the hostname.

**Reading "it works" carefully.** After removing the staging block, the production endpoint
returned 200 with a trusted certificate, which looked like success. The certificate's dates showed
it was the original 90-day one from 23 September, still in the `caddy-data` volume, so the new
issuance path had not actually been exercised in production, and the bare-IP case never was. Also,
`docker compose up -d` does not restart Caddy for a change to a bind-mounted file, so a
Caddyfile-only change needs an explicit restart.

Useful for next time: when the first symptom is a TLS alert, read the service's own log before
changing config, and diff what is actually running (`.env` value, certificate dates) against what
was assumed.
