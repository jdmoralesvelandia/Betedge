package com.betedge.matches;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    @Query(value = "SELECT * FROM competition WHERE external_keys ->> 'oddspapi' = :key", nativeQuery = true)
    Optional<Competition> findByExternalKeyOddsPapi(@Param("key") String key);

    @Query(value = "SELECT * FROM competition WHERE external_keys ->> 'theoddsapi' = :key", nativeQuery = true)
    Optional<Competition> findByExternalKeyTheOddsApi(@Param("key") String key);

    /** Competitions tracked through The Odds API with at least one Match kicking off in [from, to] - for HotMatchRefreshService. */
    @Query(value = """
            SELECT DISTINCT c.* FROM competition c
            JOIN match m ON m.competition_id = c.id
            WHERE c.external_keys ->> 'theoddsapi' IS NOT NULL
            AND m.start_time BETWEEN :from AND :to
            """, nativeQuery = true)
    List<Competition> findTheOddsApiCompetitionsWithMatchStartingBetween(
            @Param("from") Instant from, @Param("to") Instant to);
}
