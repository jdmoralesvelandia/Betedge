package com.betedge.matches;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompetitionQueryService {

    private final CompetitionRepository competitionRepository;

    @Transactional(readOnly = true)
    public List<CompetitionResponse> findAll() {
        return competitionRepository.findAll().stream()
                .map(CompetitionResponse::from)
                .toList();
    }
}
