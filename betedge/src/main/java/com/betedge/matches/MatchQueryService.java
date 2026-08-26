package com.betedge.matches;

import com.betedge.odds.OddsRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class MatchQueryService {

    private final MatchRepository matchRepository;
    private final OddsRepository oddsRepository;

    @Transactional(readOnly = true)
    public MatchResponse findById(Long id) {
        Match match = matchRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        return MatchResponse.from(match, oddsRepository.existsByMatchId(id));
    }

    /**
     * Every ingested match, oldest kickoff first, regardless of whether it produced an
     * opportunity - optionally narrowed by competition and/or a partial, case-insensitive match
     * against either team name (both combine with AND; pass null to skip either one).
     */
    @Transactional(readOnly = true)
    public List<MatchResponse> findAll(Long competitionId, String search) {
        List<Match> matches = matchRepository.findAllFiltered(competitionId, normalize(search));
        Set<Long> matchIdsWithOdds = new HashSet<>(oddsRepository.findMatchIdsWithOdds());
        return matches.stream()
                .map(match -> MatchResponse.from(match, matchIdsWithOdds.contains(match.getId())))
                .toList();
    }

    private static String normalize(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
