package com.keypass.server.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Every service depends on this bean rather than calling Instant.now() directly, so tests can
 * substitute Clock.fixed(...) to exercise curfews, expiry and skew deterministically. */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
