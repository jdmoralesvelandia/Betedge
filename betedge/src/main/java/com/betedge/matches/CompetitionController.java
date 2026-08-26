package com.betedge.matches;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionQueryService competitionQueryService;

    @GetMapping
    public List<CompetitionResponse> listCompetitions() {
        return competitionQueryService.findAll();
    }
}
