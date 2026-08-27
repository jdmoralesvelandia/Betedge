package com.betedge.odds;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    /**
     * Scoped by provider - a plain findTopByOrderByFinishedAtDesc() (no provider filter) would
     * return whichever of ODDSPAPI/THEODDSAPI happened to finish most recently, so each pipeline's
     * own "last run" endpoint would risk showing the OTHER pipeline's data whenever that one ran
     * more recently. Each ingestion service's own findLastRun() passes its own provider here.
     */
    Optional<IngestionRun> findTopByProviderOrderByFinishedAtDesc(DataSource provider);

    /** For HotMatchRefreshService's monthly budget check - startedAt is passed as the start of the current calendar month (UTC). */
    long countByProviderAndTriggeredByAndStartedAtGreaterThanEqual(
            DataSource provider, TriggeredBy triggeredBy, Instant startedAt);
}
