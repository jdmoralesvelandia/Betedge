package com.betedge.odds;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {

    Optional<IngestionRun> findTopByOrderByFinishedAtDesc();

    /** For HotMatchRefreshService's monthly budget check - startedAt is passed as the start of the current calendar month (UTC). */
    long countByProviderAndTriggeredByAndStartedAtGreaterThanEqual(
            DataSource provider, TriggeredBy triggeredBy, Instant startedAt);
}
