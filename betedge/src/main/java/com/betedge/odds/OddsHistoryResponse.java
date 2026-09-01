package com.betedge.odds;

import java.time.Instant;
import java.util.List;

/**
 * Full odds history for a match, plus the two small facts the frontend needs to visually extend
 * each series' line to "still confirmed as of the last successful check" rather than leaving it
 * dangling at its last real update - see SingleBookmakerChart/OddsHistoryChart's own
 * withTrailingConfirmation for how these get used. One field per provider, not a map: there are
 * only ever two (see DataSource), same reasoning IngestionAdminController already split into two
 * endpoints (/last-run, /last-run-theoddsapi) instead of one parameterized one. Either can be null
 * if that provider has never completed a run yet.
 */
public record OddsHistoryResponse(
        List<OddsHistoryEntryDto> entries, Instant lastOddsPapiRunAt, Instant lastTheOddsApiRunAt) {
}
