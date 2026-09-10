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
     * Two independently-configurable cron slots, fixed local times rather than a fixed interval -
     * explicit zone since the server (Oracle Cloud) runs in UTC, not America/Bogota; never rely
     * on the JVM/server default zone for a schedule the business defines in local time.
     *
     * Reduced from 2x/day to 1x/day on 2026-09-09 (odds-ingestion.cron-secondary defaults to "-")
     * while OddsPapi's free-tier reset behavior (monthly vs lifetime) is still unconfirmed -
     * halves the scheduled monthly cost (180 -> 90 calls/month at today's 3-calls/run, see
     * odds-ingestion's own budget comment in application.yml). "-" is Spring's own documented
     * cron value meaning "disabled" for a property-resolved @Scheduled trigger (see
     * ScheduledAnnotationBeanPostProcessor) - not a magic string invented here. Revert to 2x/day
     * by setting ODDS_INGESTION_CRON_SECONDARY back to "0 0 13 * * *" - no code change needed.
     */
    @Scheduled(cron = "${odds-ingestion.cron-primary}", zone = "America/Bogota")
    @Scheduled(cron = "${odds-ingestion.cron-secondary}", zone = "America/Bogota")
    public void scheduledIngestion() {
        ingestionService.runIngestion(TriggeredBy.SCHEDULED);
    }
}
