package com.betedge.matches;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps Match.status current on its own schedule, independent of when a match is next touched by
 * odds ingestion - pure DB housekeeping, no external calls, no API budget spent. A SCHEDULED
 * match whose full duration has already elapsed (e.g. this update was down for a while) goes
 * straight to FINISHED rather than passing through LIVE first, so a missed cycle can never strand
 * a match short of its true status.
 */
@Service
@RequiredArgsConstructor
public class MatchStatusUpdateService {

    private static final Logger log = LoggerFactory.getLogger(MatchStatusUpdateService.class);

    private static final List<MatchStatus> NOT_YET_FINISHED = List.of(MatchStatus.SCHEDULED, MatchStatus.LIVE);

    private final MatchRepository matchRepository;

    @Value("${match-status-update.duration-hours}")
    private int matchDurationHours;

    /**
     * Transactional so the entities fetched below are managed and their status changes are
     * persisted via Hibernate's normal dirty-checking on commit, without an explicit save() call.
     */
    @Transactional
    public void updateStatuses() {
        Instant now = Instant.now();
        // The instant a match's full duration elapses, counting back from now - doubles as the
        // LIVE window's lower (exclusive) bound and the FINISHED threshold's upper (inclusive)
        // bound, since those are the same instant looked at from either side.
        Instant durationElapsedThreshold = now.minus(matchDurationHours, ChronoUnit.HOURS);

        List<Match> toMarkLive = matchRepository.findMatchesToMarkLive(MatchStatus.SCHEDULED, now, durationElapsedThreshold);
        toMarkLive.forEach(match -> match.setStatus(MatchStatus.LIVE));

        List<Match> toMarkFinished = matchRepository.findMatchesToMarkFinished(NOT_YET_FINISHED, durationElapsedThreshold);
        toMarkFinished.forEach(match -> match.setStatus(MatchStatus.FINISHED));

        if (!toMarkLive.isEmpty() || !toMarkFinished.isEmpty()) {
            log.info("Match status update: {} -> LIVE, {} -> FINISHED", toMarkLive.size(), toMarkFinished.size());
        }
    }
}
