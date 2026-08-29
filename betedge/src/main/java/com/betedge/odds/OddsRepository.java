package com.betedge.odds;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OddsRepository extends JpaRepository<Odds, Long> {

    @Query("SELECT MAX(o.timestamp) FROM Odds o")
    Instant findMostRecentTimestamp();

    /**
     * For OddsDeduplicationService - the last known snapshot for one exact (match, bookmaker,
     * selection, dataSource) quartet, if any. Scoped to a single source deliberately: pinnacle is
     * fed by both OddsPapi and The Odds API under the same bookmaker_id, and comparing against
     * "the last row regardless of source" made each source's own repeat of its own unchanged price
     * look like a change the moment the OTHER source's row landed in between - see
     * OddsDeduplicationService's own Javadoc for the full incident writeup.
     */
    Optional<Odds> findTopByMatchIdAndBookmakerIdAndSelectionAndDataSourceOrderByTimestampDesc(
            Long matchId, Long bookmakerId, String selection, DataSource dataSource);

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
