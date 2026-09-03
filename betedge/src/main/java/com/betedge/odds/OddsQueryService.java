package com.betedge.odds;

import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OddsQueryService {

    /**
     * Safety cap for /odds/history (2026-09-02): a match's row count has no natural ceiling over
     * its lifetime, and OddsHistoryChart/SingleBookmakerChart render every entry the endpoint
     * returns with no limit of their own (confirmed against real data the same day - the busiest
     * match then had 322 rows, well under this). 2000 is generous headroom over that, not a
     * measured ceiling - a real-world hazard only if history keeps accumulating far beyond today's
     * volume. Always the MOST RECENT rows when the cap is hit - see
     * OddsRepository.findByMatchIdOrderByTimestampDesc's own comment for why DESC, not ASC, is the
     * query that gets capped.
     */
    static final int HISTORY_ROW_LIMIT = 2000;

    private final OddsRepository oddsRepository;
    private final IngestionRunRepository ingestionRunRepository;

    /**
     * lastOddsPapiRunAt/lastTheOddsApiRunAt are each that provider's last COMPLETED run - a run
     * only ever gets an ingestion_run row once it reaches the end of runIngestion (see
     * IngestionService/TheOddsApiIngestionService's own finishRun), so
     * findTopByProviderOrderByFinishedAtDesc already excludes anything that crashed before that
     * point without needing a separate success/failure flag - same query
     * IngestionAdminController's own last-run endpoints use for the admin panel's "última
     * actualización: hace N min".
     *
     * <p>truncated (2026-09-02) is true when the match's real row count exceeds HISTORY_ROW_LIMIT -
     * a plain COUNT(*), separate from the capped fetch below, since comparing "did the capped fetch
     * return exactly the limit" alone can't distinguish an exact-2000-row match (not truncated)
     * from a 2001+-row one (truncated); the frontend surfaces this rather than silently dropping
     * older rows with no indication anything was cut.
     */
    @Transactional(readOnly = true)
    public OddsHistoryResponse findHistoryByMatch(Long matchId) {
        var mostRecentDesc = oddsRepository.findByMatchIdOrderByTimestampDesc(
                matchId, PageRequest.of(0, HISTORY_ROW_LIMIT));
        boolean truncated = oddsRepository.countByMatchId(matchId) > HISTORY_ROW_LIMIT;

        var entries = mostRecentDesc.stream()
                .sorted(Comparator.comparing(Odds::getTimestamp))
                .map(OddsHistoryEntryDto::from)
                .toList();
        var lastOddsPapiRunAt = ingestionRunRepository.findTopByProviderOrderByFinishedAtDesc(DataSource.ODDSPAPI)
                .map(IngestionRun::getFinishedAt)
                .orElse(null);
        var lastTheOddsApiRunAt = ingestionRunRepository.findTopByProviderOrderByFinishedAtDesc(DataSource.THEODDSAPI)
                .map(IngestionRun::getFinishedAt)
                .orElse(null);
        return new OddsHistoryResponse(entries, lastOddsPapiRunAt, lastTheOddsApiRunAt, truncated);
    }
}
