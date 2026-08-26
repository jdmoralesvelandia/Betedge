package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** One real-world fixture, as returned by GET /v4/sports/{sportKey}/odds. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TheOddsApiEventDto(
        String id,
        @JsonProperty("sport_key") String sportKey,
        @JsonProperty("sport_title") String sportTitle,
        @JsonProperty("commence_time") Instant commenceTime,
        @JsonProperty("home_team") String homeTeam,
        @JsonProperty("away_team") String awayTeam,
        List<TheOddsApiBookmakerDto> bookmakers) {
}
