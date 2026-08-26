package com.betedge.odds;

import java.time.Instant;

public record LastIngestionResponse(
        Instant lastOddsTimestamp) {
}
