package com.betedge.valuebets;

import com.betedge.matches.Match;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SurebetResponse(
        Long id,
        Long matchId,
        String homeTeam,
        String awayTeam,
        String competitionName,
        Instant startTime,
        List<SurebetLeg> legs,
        BigDecimal totalImpliedProbability,
        BigDecimal profitPercentage,
        Instant detectedAt) {

    public static SurebetResponse from(Surebet surebet) {
        Match match = surebet.getMatch();
        return new SurebetResponse(
                surebet.getId(),
                match.getId(),
                match.getHomeTeam(),
                match.getAwayTeam(),
                match.getCompetition().getName(),
                match.getStartTime(),
                surebet.getLegs(),
                surebet.getTotalImpliedProbability(),
                surebet.getProfitPercentage(),
                surebet.getDetectedAt());
    }
}
