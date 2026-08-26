package com.betedge.matches;

import java.time.Instant;

public record MatchResponse(
        Long id,
        String homeTeam,
        String awayTeam,
        String competitionName,
        Instant startTime,
        MatchStatus status,
        boolean hasOdds) {

    static MatchResponse from(Match match, boolean hasOdds) {
        return new MatchResponse(
                match.getId(),
                match.getHomeTeam(),
                match.getAwayTeam(),
                match.getCompetition().getName(),
                match.getStartTime(),
                match.getStatus(),
                hasOdds);
    }
}
