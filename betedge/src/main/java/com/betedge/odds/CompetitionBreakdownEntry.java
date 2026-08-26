package com.betedge.odds;

public record CompetitionBreakdownEntry(
        String competitionName,
        int eventsReceived,
        int newMatches,
        int newOdds) {
}
