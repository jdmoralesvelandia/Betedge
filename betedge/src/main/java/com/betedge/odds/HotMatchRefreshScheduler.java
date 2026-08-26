package com.betedge.odds;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Independent from both IngestionScheduler and TheOddsApiIngestionScheduler - its own interval
 * and on/off switch, see hot-refresh in application.yml. Like TheOddsApiIngestionScheduler, no
 * matchIfMissing=true: unproven in production, must fail closed if the property can't resolve.
 */
@Component
@ConditionalOnProperty(value = "hot-refresh.scheduling-enabled", havingValue = "true")
@RequiredArgsConstructor
public class HotMatchRefreshScheduler {

    private final HotMatchRefreshService hotMatchRefreshService;

    @Scheduled(fixedRateString = "${hot-refresh.check-interval-ms}")
    public void scheduledCheck() {
        hotMatchRefreshService.refreshHotMatches();
    }
}
