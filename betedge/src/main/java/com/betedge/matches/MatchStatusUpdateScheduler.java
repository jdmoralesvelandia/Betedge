package com.betedge.matches;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Separate from HotMatchRefreshScheduler (different risk profile - this spends no API budget at
 * all, just updates Match rows) but deliberately reuses its check-interval-ms property: "misma
 * frecuencia" by design, not by coincidence, so the two intervals can never drift apart. Its own
 * scheduling-enabled flag, same fail-closed pattern as the other schedulers (no matchIfMissing=true).
 */
@Component
@ConditionalOnProperty(value = "match-status-update.scheduling-enabled", havingValue = "true")
@RequiredArgsConstructor
public class MatchStatusUpdateScheduler {

    private final MatchStatusUpdateService matchStatusUpdateService;

    @Scheduled(fixedRateString = "${hot-refresh.check-interval-ms}")
    public void scheduledUpdate() {
        matchStatusUpdateService.updateStatuses();
    }
}
