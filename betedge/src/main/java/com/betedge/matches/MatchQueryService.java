package com.betedge.matches;

import com.betedge.odds.OddsRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

    /**
     * finishedWithinDays default when the caller (frontend) doesn't pass one at all - matches the
     * "Partidos" page's own default selector state. Kept here, not on the controller, so every
     * caller of findAll (including any future one that doesn't come through the REST layer) gets
     * the same default rather than each having to know the number.
     */
    private static final int DEFAULT_FINISHED_WITHIN_DAYS = 7;

    /**
     * finishedWithinDays <= 0 means "todos" - show the full FINISHED history with no age cutoff.
     * 0 was chosen over a negative sentinel (e.g. -1) because it needs no sign parsing on either
     * side and reads naturally as "no limit" (the same convention several pagination/limit APIs
     * use); any other non-positive value the frontend might send is treated the same way rather
     * than rejected, since none of them carry a sensible finite meaning here.
     */
    private static final int SHOW_ALL_FINISHED_SENTINEL = 0;

    /**
     * Stand-in for "no age cutoff" - see MatchRepository.findAllFiltered's Javadoc for why this
     * is a sentinel Instant rather than a bound null parameter. Epoch is safely older than any
     * real match's startTime, so comparing m.startTime >= this is always true.
     */
    private static final Instant NO_FINISHED_CUTOFF = Instant.EPOCH;

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
     * against either team name (both combine with AND with each other and with the age filter
     * below; pass null to skip either one).
     *
     * <p>finishedWithinDays additionally hides FINISHED matches older than that many days
     * (SCHEDULED/LIVE are never affected by it, regardless of their startTime) - null falls back
     * to {@link #DEFAULT_FINISHED_WITHIN_DAYS}, and {@link #SHOW_ALL_FINISHED_SENTINEL} (or any
     * other non-positive value) disables the age filter entirely.
     */
    @Transactional(readOnly = true)
    public List<MatchResponse> findAll(Long competitionId, String search, Integer finishedWithinDays) {
        Instant finishedCutoff = resolveFinishedCutoff(finishedWithinDays);
        List<Match> matches = matchRepository.findAllFiltered(competitionId, normalize(search), finishedCutoff);
        Set<Long> matchIdsWithOdds = new HashSet<>(oddsRepository.findMatchIdsWithOdds());
        return matches.stream()
                .map(match -> MatchResponse.from(match, matchIdsWithOdds.contains(match.getId())))
                .toList();
    }

    private static Instant resolveFinishedCutoff(Integer finishedWithinDays) {
        int days = finishedWithinDays == null ? DEFAULT_FINISHED_WITHIN_DAYS : finishedWithinDays;
        if (days <= SHOW_ALL_FINISHED_SENTINEL) {
            return NO_FINISHED_CUTOFF;
        }
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private static String normalize(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
