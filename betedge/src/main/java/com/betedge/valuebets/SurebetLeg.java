package com.betedge.valuebets;

import java.math.BigDecimal;

public record SurebetLeg(
        String selection,
        String bookmakerSlug,
        BigDecimal oddValue,
        BigDecimal stakePercentage) {
}
