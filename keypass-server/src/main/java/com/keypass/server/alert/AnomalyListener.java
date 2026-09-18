package com.keypass.server.alert;

import com.keypass.server.access.AccessDeniedEvent;
import com.keypass.server.access.AccessGrantedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Runs anomaly detection after the triggering transaction commits, off the request thread. */
@Component
public class AnomalyListener {

    private final BruteForceRule bruteForceRule;
    private final UnusualTimeRule unusualTimeRule;

    public AnomalyListener(BruteForceRule bruteForceRule, UnusualTimeRule unusualTimeRule) {
        this.bruteForceRule = bruteForceRule;
        this.unusualTimeRule = unusualTimeRule;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDenied(AccessDeniedEvent event) {
        bruteForceRule.check(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGranted(AccessGrantedEvent event) {
        unusualTimeRule.check(event);
    }
}
