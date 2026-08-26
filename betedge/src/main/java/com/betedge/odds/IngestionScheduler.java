package com.betedge.odds;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "odds-ingestion.scheduling-enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class IngestionScheduler {

    private final IngestionService ingestionService;

    /**
     * Twice a day, fixed local times rather than a fixed interval - explicit zone since the
     * server (Oracle Cloud) runs in UTC, not America/Bogota; never rely on the JVM/server default
     * zone for a schedule the business defines in local time.
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "America/Bogota")
    @Scheduled(cron = "0 0 13 * * *", zone = "America/Bogota")
    public void scheduledIngestion() {
        ingestionService.runIngestion(TriggeredBy.SCHEDULED);
    }
}
