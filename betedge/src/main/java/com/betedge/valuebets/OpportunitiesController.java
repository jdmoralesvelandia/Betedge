package com.betedge.valuebets;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OpportunitiesController {

    private final ValueBetQueryService valueBetQueryService;
    private final SurebetQueryService surebetQueryService;

    @GetMapping("/value-bets")
    public List<ValueBetResponse> activeValueBets() {
        return valueBetQueryService.findActive();
    }

    @GetMapping("/value-bets/active")
    public List<ValueBetResponse> activeValueBetsForMatch(@RequestParam Long matchId) {
        return valueBetQueryService.findActiveByMatch(matchId);
    }

    @GetMapping("/surebets")
    public List<SurebetResponse> activeSurebets() {
        return surebetQueryService.findActive();
    }

    @GetMapping("/surebets/active")
    public List<SurebetResponse> activeSurebetsForMatch(@RequestParam Long matchId) {
        return surebetQueryService.findActiveByMatch(matchId);
    }
}
