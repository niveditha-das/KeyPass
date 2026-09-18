package com.keypass.server;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A single Postgres container, started once for the whole test JVM and never explicitly
 * stopped (Testcontainers' Ryuk reaper cleans it up when the JVM exits). Every IT subclass
 * shares the exact same @SpringBootTest configuration, so Spring's test context cache reuses
 * one ApplicationContext across all of them — using a per-class @Container here instead would
 * start a fresh container for each subclass while the cached context kept pointing at the
 * previous one's now-closed port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
