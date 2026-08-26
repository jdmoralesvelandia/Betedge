package com.betedge.odds;

import java.time.Instant;
import java.util.List;

public record IngestionRunResponse(
        Long id,
        DataSource provider,
        TriggeredBy triggeredBy,
        Instant startedAt,
        Instant finishedAt,
        int totalEventsReceived,
        int totalNewMatches,
        int totalNewOdds,
        int valueBetsDetected,
        int surebetsDetected,
        List<CompetitionBreakdownEntry> competitionBreakdown) {

    static IngestionRunResponse from(IngestionRun run) {
        return new IngestionRunResponse(
                run.getId(),
                run.getProvider(),
                run.getTriggeredBy(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getTotalEventsReceived(),
                run.getTotalNewMatches(),
                run.getTotalNewOdds(),
                run.getValueBetsDetected(),
                run.getSurebetsDetected(),
                run.getCompetitionBreakdown());
    }
}
