package com.betedge.odds;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmakerRepository extends JpaRepository<Bookmaker, Long> {

    Optional<Bookmaker> findByExternalKey(String externalKey);

    /**
     * For IngestionService/TheOddsApiIngestionService's per-run bookmaker cache - one query for
     * the whole (small, fixed) targetBookmakers list instead of one findByExternalKey call per
     * bookmaker seen per fixture/event. Same caching shape as ReferenceDataSyncService's own
     * participantNames cache, just built fresh per run instead of on a longer schedule, since
     * unlike participant names this must reflect targetBookmakers exactly as configured for THIS
     * run, not a previous one.
     */
    List<Bookmaker> findByExternalKeyIn(List<String> externalKeys);
}
