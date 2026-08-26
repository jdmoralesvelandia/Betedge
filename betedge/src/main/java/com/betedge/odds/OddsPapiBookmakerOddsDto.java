package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OddsPapiBookmakerOddsDto(
        boolean bookmakerIsActive,
        boolean suspended,
        Map<String, OddsPapiMarketDto> markets) {
}
