package com.betedge.odds;

import java.math.BigDecimal;
import java.time.Instant;

public record OddsHistoryEntryDto(
        String bookmakerSlug,
        String selection,
        BigDecimal oddValue,
        Instant timestamp) {

    static OddsHistoryEntryDto from(Odds odds) {
        return new OddsHistoryEntryDto(
                odds.getBookmaker().getExternalKey(), odds.getSelection(), odds.getOddValue(), odds.getTimestamp());
    }
}
