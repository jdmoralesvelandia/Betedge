package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** key is the bookmaker slug (e.g. "pinnacle", "betfair_ex_eu") - title is its display name. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TheOddsApiBookmakerDto(
        String key,
        String title,
        @JsonProperty("last_update") Instant lastUpdate,
        List<TheOddsApiMarketDto> markets) {
}
