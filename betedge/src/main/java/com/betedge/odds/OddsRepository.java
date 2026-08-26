package com.betedge.odds;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OddsRepository extends JpaRepository<Odds, Long> {

    @Query("SELECT MAX(o.timestamp) FROM Odds o")
    Instant findMostRecentTimestamp();

    /** The latest snapshot per (bookmaker, selection) for a match - not the full history. */
    @Query(value = """
            SELECT DISTINCT ON (bookmaker_id, selection) *
            FROM odds
            WHERE match_id = :matchId
            ORDER BY bookmaker_id, selection, timestamp DESC
            """, nativeQuery = true)
    List<Odds> findLatestOddsByMatch(@Param("matchId") Long matchId);

    /** Full history (every snapshot, every bookmaker) for a match, oldest first - for charting. */
    List<Odds> findByMatchIdOrderByTimestampAsc(Long matchId);

    boolean existsByMatchId(Long matchId);

    /** Every match id that has at least one odds row - for bulk hasOdds checks without N+1 queries. */
    @Query("SELECT DISTINCT o.match.id FROM Odds o")
    List<Long> findMatchIdsWithOdds();
}
