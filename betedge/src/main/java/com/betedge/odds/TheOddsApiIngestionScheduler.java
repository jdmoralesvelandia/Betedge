package com.betedge.odds;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Independent from IngestionScheduler - its own interval and on/off switch, see
 * theoddsapi-ingestion in application.yml. Unlike IngestionScheduler, no matchIfMissing=true:
 * this pipeline is unproven in production, so if the property somehow can't be resolved at all,
 * it must fail closed (disabled), not open.
 */
@Component
@ConditionalOnProperty(value = "theoddsapi-ingestion.scheduling-enabled", havingValue = "true")
@RequiredArgsConstructor
public class TheOddsApiIngestionScheduler {

    private final TheOddsApiIngestionService theOddsApiIngestionService;

    /**
     * Three times a day, fixed local times rather than a fixed interval - same explicit-zone
     * reasoning as IngestionScheduler: the server (Oracle Cloud) runs in UTC, never rely on its
     * default zone for a schedule the business defines in local (Bogota) time.
     */
    @Scheduled(cron = "0 0 7 * * *", zone = "America/Bogota")
    @Scheduled(cron = "0 0 10 * * *", zone = "America/Bogota")
    @Scheduled(cron = "0 0 14 * * *", zone = "America/Bogota")
    public void scheduledIngestion() {
        theOddsApiIngestionService.runIngestion(TriggeredBy.SCHEDULED);
    }
}
