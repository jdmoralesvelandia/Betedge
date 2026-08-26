package com.betedge.valuebets;

import com.betedge.matches.Match;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ValueBetResponse(
        Long id,
        Long matchId,
        String homeTeam,
        String awayTeam,
        String competitionName,
        Instant startTime,
        String bookmakerSlug,
        String selection,
        BigDecimal impliedProbability,
        BigDecimal estimatedTrueProbability,
        BigDecimal edgePercentage,
        Map<String, BigDecimal> bookmakerProbabilities,
        Instant detectedAt) {

    public static ValueBetResponse from(ValueBet valueBet) {
        Match match = valueBet.getMatch();
        return new ValueBetResponse(
                valueBet.getId(),
                match.getId(),
                match.getHomeTeam(),
                match.getAwayTeam(),
                match.getCompetition().getName(),
                match.getStartTime(),
                valueBet.getBookmaker().getExternalKey(),
                valueBet.getSelection(),
                valueBet.getImpliedProbability(),
                valueBet.getEstimatedTrueProbability(),
                valueBet.getEdgePercentage(),
                valueBet.getBookmakerProbabilities(),
                valueBet.getDetectedAt());
    }
}
