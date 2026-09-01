package com.betedge.odds;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OddsQueryService {

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
     */
    @Transactional(readOnly = true)
    public OddsHistoryResponse findHistoryByMatch(Long matchId) {
        var entries = oddsRepository.findByMatchIdOrderByTimestampAsc(matchId).stream()
                .map(OddsHistoryEntryDto::from)
                .toList();
        var lastOddsPapiRunAt = ingestionRunRepository.findTopByProviderOrderByFinishedAtDesc(DataSource.ODDSPAPI)
                .map(IngestionRun::getFinishedAt)
                .orElse(null);
        var lastTheOddsApiRunAt = ingestionRunRepository.findTopByProviderOrderByFinishedAtDesc(DataSource.THEODDSAPI)
                .map(IngestionRun::getFinishedAt)
                .orElse(null);
        return new OddsHistoryResponse(entries, lastOddsPapiRunAt, lastTheOddsApiRunAt);
    }
}
