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
public class ValueBetQueryService {

    private final ValueBetRepository valueBetRepository;

    @Value("${opportunity.staleness-hours}")
    private long stalenessHours;

    @Transactional(readOnly = true)
    public List<ValueBetResponse> findActive() {
        Instant cutoff = Instant.now().minus(stalenessHours, ChronoUnit.HOURS);
        return valueBetRepository.findActive(cutoff).stream().map(ValueBetResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ValueBetResponse> findActiveByMatch(Long matchId) {
        Instant cutoff = Instant.now().minus(stalenessHours, ChronoUnit.HOURS);
        return valueBetRepository.findActiveByMatch(matchId, cutoff).stream()
                .map(ValueBetResponse::from)
                .toList();
    }
}
