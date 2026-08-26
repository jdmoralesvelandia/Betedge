package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OddsPapiFixtureDto(
        String fixtureId,
        Long participant1Id,
        Long participant2Id,
        Integer tournamentId,
        Instant startTime,
        Map<String, OddsPapiBookmakerOddsDto> bookmakerOdds) {
}
