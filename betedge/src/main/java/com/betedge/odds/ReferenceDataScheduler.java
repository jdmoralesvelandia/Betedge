package com.betedge.odds;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "reference-data-sync.scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class ReferenceDataScheduler {

    private final ReferenceDataSyncService referenceDataSyncService;

    @Scheduled(fixedRateString = "${reference-data-sync.interval-ms}")
    public void scheduledSync() {
        referenceDataSyncService.syncNow();
    }
}
