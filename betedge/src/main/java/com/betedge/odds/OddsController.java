package com.betedge.odds;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only odds endpoints, open to any authenticated role (no admin restriction). */
@RestController
@RequestMapping("/odds")
@RequiredArgsConstructor
public class OddsController {

    private final IngestionService ingestionService;
    private final OddsQueryService oddsQueryService;

    /** Same "when was the data last refreshed" info as the admin endpoint, open to any authenticated role. */
    @GetMapping("/last-updated")
    public LastIngestionResponse lastUpdated() {
        return new LastIngestionResponse(ingestionService.getMostRecentOddsTimestamp());
    }

    /**
     * Full odds history for a match (every bookmaker, every snapshot) - for the odds-evolution
     * chart. Also carries each provider's last completed run timestamp, used to visually extend a
     * series' line to "still confirmed as of then" - see OddsHistoryResponse's own Javadoc.
     */
    @GetMapping("/history")
    public OddsHistoryResponse history(@RequestParam Long matchId) {
        return oddsQueryService.findHistoryByMatch(matchId);
    }
}
