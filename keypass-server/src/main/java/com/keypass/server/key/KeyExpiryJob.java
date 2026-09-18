package com.keypass.server.key;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class KeyExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(KeyExpiryJob.class);

    private final JdbcClient jdbc;

    public KeyExpiryJob(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "PT1M")
    @Transactional
    public void expireKeys() {
        int n = jdbc.sql("""
                        UPDATE digital_key SET status = 'EXPIRED', version = version + 1
                        WHERE status = 'ACTIVE' AND not_after < now()
                        """)
                .update();
        if (n > 0) {
            log.info("Expired {} keys", n);
        }
    }
}
