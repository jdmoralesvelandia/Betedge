package com.betedge.odds;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OddsQueryService {

    private final OddsRepository oddsRepository;

    @Transactional(readOnly = true)
    public List<OddsHistoryEntryDto> findHistoryByMatch(Long matchId) {
        return oddsRepository.findByMatchIdOrderByTimestampAsc(matchId).stream()
                .map(OddsHistoryEntryDto::from)
                .toList();
    }
}
