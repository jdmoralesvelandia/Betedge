package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * key distinguishes market variants within one bookmaker - "h2h" (moneyline, what we want) vs
 * e.g. "h2h_lay" (Betfair exchange's lay/against price). See TheOddsApiClient.fetchOdds, which
 * keeps only "h2h" markets before this DTO ever leaves the client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TheOddsApiMarketDto(
        String key,
        @JsonProperty("last_update") Instant lastUpdate,
        List<TheOddsApiOutcomeDto> outcomes) {
}
