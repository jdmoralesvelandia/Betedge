package com.betedge.valuebets;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SurebetQueryService {

    private final SurebetRepository surebetRepository;

    @Value("${opportunity.staleness-hours}")
    private long stalenessHours;

    @Transactional(readOnly = true)
    public List<SurebetResponse> findActive() {
        Instant cutoff = Instant.now().minus(stalenessHours, ChronoUnit.HOURS);
        return surebetRepository.findActive(cutoff).stream().map(SurebetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<SurebetResponse> findActiveByMatch(Long matchId) {
        Instant cutoff = Instant.now().minus(stalenessHours, ChronoUnit.HOURS);
        return surebetRepository.findActiveByMatch(matchId, cutoff).stream()
                .map(SurebetResponse::from)
                .toList();
    }
}
