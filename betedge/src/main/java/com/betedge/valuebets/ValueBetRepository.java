package com.betedge.valuebets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValueBetRepository extends JpaRepository<ValueBet, Long> {

    /** For ValueBetCalculationService's dedup check - the last detection for this exact (match, bookmaker, selection) triad, if any. */
    Optional<ValueBet> findTopByMatchIdAndBookmakerIdAndSelectionOrderByDetectedAtDesc(
            Long matchId, Long bookmakerId, String selection);

    /**
     * Latest value bet per (match, selection), only for matches still SCHEDULED (mirrors
     * ValueBetCalculationService.isEligible - pre-match detection only, see its comment for why
     * LIVE is excluded) and detected within the configured staleness window. A value bet detected
     * while a match was SCHEDULED must stop showing as "active" once that match goes LIVE, even
     * though the row itself isn't deleted - it's stale the moment the match starts, same as one
     * that's aged past the staleness window.
     */
    @Query(value = """
            SELECT DISTINCT ON (vb.match_id, vb.selection) vb.*
            FROM value_bet vb
            JOIN match m ON m.id = vb.match_id
            WHERE m.status = 'SCHEDULED'
              AND vb.detected_at >= :cutoff
            ORDER BY vb.match_id, vb.selection, vb.detected_at DESC
            """, nativeQuery = true)
    List<ValueBet> findActive(@Param("cutoff") Instant cutoff);

    /**
     * Same "active" definition as {@link #findActive}, scoped to one match - collapses by
     * (bookmaker, selection) instead of (match, selection), since a match's own detail view
     * should show every bookmaker's currently-live edge, not just the single most recent one
     * across all of them.
     */
    @Query(value = """
            SELECT DISTINCT ON (vb.bookmaker_id, vb.selection) vb.*
            FROM value_bet vb
            JOIN match m ON m.id = vb.match_id
            WHERE vb.match_id = :matchId
              AND m.status = 'SCHEDULED'
              AND vb.detected_at >= :cutoff
            ORDER BY vb.bookmaker_id, vb.selection, vb.detected_at DESC
            """, nativeQuery = true)
    List<ValueBet> findActiveByMatch(@Param("matchId") Long matchId, @Param("cutoff") Instant cutoff);
}
