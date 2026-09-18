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
