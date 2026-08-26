package com.betedge.valuebets;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SurebetRepository extends JpaRepository<Surebet, Long> {

    /** For SurebetCalculationService's dedup check - the last detection for this match, if any. Surebet has no single bookmaker/selection of its own (profit_percentage spans all 3 legs), so match is the finest grain available. */
    Optional<Surebet> findTopByMatchIdOrderByDetectedAtDesc(Long matchId);

    /**
     * Latest surebet per match, only for matches still SCHEDULED (mirrors
     * SurebetCalculationService.isEligible - pre-match detection only) and detected within the
     * configured staleness window. See the identical comment on ValueBetRepository.findActive for
     * why LIVE is excluded here too.
     */
    @Query(value = """
            SELECT DISTINCT ON (sb.match_id) sb.*
            FROM surebet sb
            JOIN match m ON m.id = sb.match_id
            WHERE m.status = 'SCHEDULED'
              AND sb.detected_at >= :cutoff
            ORDER BY sb.match_id, sb.detected_at DESC
            """, nativeQuery = true)
    List<Surebet> findActive(@Param("cutoff") Instant cutoff);

    /**
     * Same "active" definition as {@link #findActive}, scoped to one match. Still DISTINCT ON
     * (match_id) rather than (bookmaker_id, selection) - unlike ValueBet, a surebet's
     * profit_percentage already spans all 3 legs/bookmakers at once, so there's only ever one
     * "current" surebet per match to begin with, not one per bookmaker+selection.
     */
    @Query(value = """
            SELECT DISTINCT ON (sb.match_id) sb.*
            FROM surebet sb
            JOIN match m ON m.id = sb.match_id
            WHERE sb.match_id = :matchId
              AND m.status = 'SCHEDULED'
              AND sb.detected_at >= :cutoff
            ORDER BY sb.match_id, sb.detected_at DESC
            """, nativeQuery = true)
    List<Surebet> findActiveByMatch(@Param("matchId") Long matchId, @Param("cutoff") Instant cutoff);
}
