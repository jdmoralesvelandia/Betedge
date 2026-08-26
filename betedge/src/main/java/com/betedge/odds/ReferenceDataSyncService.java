package com.betedge.odds;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Participant names change far less often than odds, so they're synced on their own (much
 * longer) schedule and cached in memory instead of being re-fetched on every odds ingestion
 * cycle. If the cache is still empty when it's first read (e.g. right after a restart, before
 * the scheduled sync has had a chance to run), a synchronous sync is triggered on demand so
 * ingestion never runs cold against an empty cache.
 *
 * Used to also sync the full ~230-bookmaker catalog daily (to build a "valid, non-cloned
 * bookmaker" set IngestionService checked against). Removed: odds-ingestion.bookmakers is now a
 * small fixed list, each entry already confirmed non-cloned by hand (see application.yml), so
 * that daily full-catalog call was paying real API budget for a check that no longer catches
 * anything a fixed, pre-verified list doesn't already guarantee. The Bookmaker rows the fixed
 * list needs are seeded once via V17__seed_fixed_bookmakers.sql instead of synced daily.
 */
@Service
@RequiredArgsConstructor
public class ReferenceDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSyncService.class);
    private static final int SOCCER_SPORT_ID = 10;

    private final OddsPapiClient oddsPapiClient;

    private final AtomicReference<Map<String, String>> participantNames = new AtomicReference<>(Map.of());
    private volatile boolean populated = false;

    public Map<String, String> getParticipantNames() {
        ensurePopulated();
        return participantNames.get();
    }

    private void ensurePopulated() {
        if (!populated) {
            log.info("Reference data cache is empty (cold start); running a synchronous sync before continuing");
            syncNow();
        }
    }

    public synchronized void syncNow() {
        participantNames.set(fetchParticipantNamesSafely());
        populated = true;
        log.info("Reference data sync complete: {} participant names cached", participantNames.get().size());
    }

    private Map<String, String> fetchParticipantNamesSafely() {
        try {
            return oddsPapiClient.fetchParticipantNames(SOCCER_SPORT_ID);
        } catch (RestClientException e) {
            log.error(
                    "Failed to fetch participant names from OddsPapi; keeping the previously cached map, if any", e);
            return participantNames.get();
        }
    }
}
