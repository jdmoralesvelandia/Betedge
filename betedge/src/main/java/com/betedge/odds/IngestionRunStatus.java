package com.betedge.odds;

/**
 * Outcome of an ingestion run - separate from {@link TriggeredBy} (who/what fired it), this is
 * what actually happened. Defaults to {@link #COMPLETED} at the field level on {@link IngestionRun}
 * so every pre-existing row (and every TheOddsApi row, which never touches this at all) is
 * unaffected without needing any change to TheOddsApiIngestionService.
 */
public enum IngestionRunStatus {
    COMPLETED,
    /**
     * OddsPapi only (see IngestionService.runIngestion's quota guard) - a SCHEDULED run that
     * skipped its real /odds-by-tournaments calls entirely because GET /account (free, confirmed
     * unmetered - see OddsPapiClient.fetchAccountInfo) reported remaining quota below
     * odds-ingestion.min-remaining-quota. A MANUAL trigger never produces this status - an admin
     * deliberately firing one always goes through for real, low quota or not.
     */
    SKIPPED_LOW_QUOTA
}
