package com.betedge.odds;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ingestion")
@RequiredArgsConstructor
public class IngestionAdminController {

    private final IngestionService ingestionService;
    private final TheOddsApiIngestionService theOddsApiIngestionService;

    @PostMapping("/trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public IngestionRunResponse trigger() {
        return ingestionService.runIngestion(TriggeredBy.MANUAL);
    }

    @PostMapping("/trigger-theoddsapi")
    @PreAuthorize("hasRole('ADMIN')")
    public IngestionRunResponse triggerTheOddsApi() {
        return theOddsApiIngestionService.runIngestion(TriggeredBy.MANUAL);
    }

    /** The most recent ingestion run (scheduled or manual), or 204 if none has run yet. */
    @GetMapping("/last-run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IngestionRunResponse> lastRun() {
        return ingestionService.findLastRun()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
