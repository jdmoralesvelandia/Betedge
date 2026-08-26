package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OddsPapiPlayerPriceDto(
        boolean active,
        String bookmakerOutcomeId,
        BigDecimal price) {
}
