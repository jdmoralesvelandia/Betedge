package com.betedge.matches;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/matches")
@RequiredArgsConstructor
public class MatchController {

    private final MatchQueryService matchQueryService;

    @GetMapping
    public List<MatchResponse> listMatches(
            @RequestParam(required = false) Long competitionId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer finishedWithinDays) {
        return matchQueryService.findAll(competitionId, search, finishedWithinDays);
    }

    @GetMapping("/{id}")
    public MatchResponse getMatch(@PathVariable Long id) {
        return matchQueryService.findById(id);
    }
}
