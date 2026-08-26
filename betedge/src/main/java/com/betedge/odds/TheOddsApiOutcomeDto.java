package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

/** name is the literal team name (or "Draw") - The Odds API doesn't label outcomes home/draw/away. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TheOddsApiOutcomeDto(
        String name,
        BigDecimal price) {
}
