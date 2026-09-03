package com.betedge.odds;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /**
     * Same shape as {@link #findLatestOddsByMatch}, scoped to one data source - the batched
     * equivalent of {@link #findTopByMatchIdAndBookmakerIdAndSelectionAndDataSourceOrderByTimestampDesc}
     * for {@link OddsDeduplicationService#loadLastKnownPrices}: one query per match instead of one
     * per (bookmaker, selection, price) triad checked. Scoped by data_source for the exact same
     * reason as that method - see OddsDeduplicationService's own Javadoc for the incident this
     * scoping prevents. dataSource is passed as its {@code name()} (a String, matching the
     * {@code @Enumerated(STRING)} column) rather than the enum itself - a native query binds
     * parameters as their raw JDBC type, not through JPA's entity-level enum mapping.
     */
    @Query(value = """
            SELECT DISTINCT ON (bookmaker_id, selection) *
            FROM odds
            WHERE match_id = :matchId AND data_source = :dataSource
            ORDER BY bookmaker_id, selection, timestamp DESC
            """, nativeQuery = true)
    List<Odds> findLatestOddsByMatchAndDataSource(@Param("matchId") Long matchId, @Param("dataSource") String dataSource);

    /**
     * Full, unbounded history (every snapshot, every bookmaker) for a match, oldest first. Used by
     * tests that assert exact row counts/ordering against a handful of rows, where "unbounded" is
     * exactly the point - {@link OddsQueryService#findHistoryByMatch} (the real /odds/history
     * endpoint) uses {@link #findByMatchIdOrderByTimestampDesc} instead, capped at
     * HISTORY_ROW_LIMIT, since a match's real row count has no natural ceiling over its lifetime.
     */
    List<Odds> findByMatchIdOrderByTimestampAsc(Long matchId);

    /**
     * Same rows as {@link #findByMatchIdOrderByTimestampAsc}, most recent first and capped via
     * {@code pageable} - {@link OddsQueryService#findHistoryByMatch} pages this with a fixed size
     * (HISTORY_ROW_LIMIT) to get the N most recent rows, then re-sorts ascending itself for the
     * chart. DESC (not ASC) on purpose: capping an ASC-ordered query at N would silently keep the
     * OLDEST N rows and drop the newest ones - the opposite of what a truncated chart should ever
     * show.
     */
    List<Odds> findByMatchIdOrderByTimestampDesc(Long matchId, Pageable pageable);

    /** Real total row count for a match - compared against HISTORY_ROW_LIMIT to detect truncation. */
    long countByMatchId(Long matchId);

    boolean existsByMatchId(Long matchId);

    /** Every match id that has at least one odds row - for bulk hasOdds checks without N+1 queries. */
    @Query("SELECT DISTINCT o.match.id FROM Odds o")
    List<Long> findMatchIdsWithOdds();
}
