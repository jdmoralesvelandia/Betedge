package com.betedge.matches;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatchRepository extends JpaRepository<Match, Long> {

    Optional<Match> findByExternalId(String externalId);

    List<Match> findByCompetition(Competition competition);

    /** Kicked off (startTime <= now) but still inside the match-duration window - candidates for SCHEDULED -> LIVE. */
    @Query("SELECT m FROM Match m WHERE m.status = :status AND m.startTime <= :now AND m.startTime > :liveWindowStart")
    List<Match> findMatchesToMarkLive(
            @Param("status") MatchStatus status, @Param("now") Instant now, @Param("liveWindowStart") Instant liveWindowStart);

    /** startTime + match-duration already in the past - candidates for -> FINISHED, from either SCHEDULED (a missed cycle) or LIVE. */
    @Query("SELECT m FROM Match m WHERE m.status IN :statuses AND m.startTime <= :finishedThreshold")
    List<Match> findMatchesToMarkFinished(
            @Param("statuses") List<MatchStatus> statuses, @Param("finishedThreshold") Instant finishedThreshold);

    /**
     * All three filters are optional and combine with AND; pass null to skip any of them. The
     * explicit CAST(:search AS string) is required - confirmed against the real DB: without it,
     * Postgres can't infer a type for the parameter when it's bound NULL (it appears twice in one
     * prepared statement, once in a plain IS NULL check and once inside CONCAT) and resolves it to
     * bytea, so LOWER(CONCAT(...)) fails with "function lower(bytea) does not exist" on every
     * request. finishedCutoff only ever constrains FINISHED matches - SCHEDULED and LIVE always
     * show regardless of startTime. It is deliberately never null (see MatchQueryService's
     * resolveFinishedCutoff): a bare "PARAM IS NULL" here would hit the exact same
     * can't-infer-a-type Postgres error as :search did before its CAST fix, but a CAST doesn't
     * apply cleanly to a temporal parameter's IS NULL branch - so instead of passing null to mean
     * "no age limit", the caller passes a sentinel Instant far enough in the past that no real
     * match can ever be older than it, making the filter a no-op without ever binding a null.
     */
    @Query("""
            SELECT m FROM Match m
            WHERE (:competitionId IS NULL OR m.competition.id = :competitionId)
            AND (:search IS NULL
                 OR LOWER(m.homeTeam) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                 OR LOWER(m.awayTeam) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            AND (m.status <> com.betedge.matches.MatchStatus.FINISHED
                 OR m.startTime >= :finishedCutoff)
            ORDER BY m.startTime ASC
            """)
    List<Match> findAllFiltered(
            @Param("competitionId") Long competitionId,
            @Param("search") String search,
            @Param("finishedCutoff") Instant finishedCutoff);
}
