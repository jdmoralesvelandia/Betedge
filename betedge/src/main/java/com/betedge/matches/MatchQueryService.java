package com.betedge.matches;

import com.betedge.odds.OddsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
     * finishedWithinDays default when the caller passes neither finishedWithinDays nor
     * finishedToday - a reasonable fallback for any caller that doesn't specify an age filter at
     * all. The "Partidos" page's own default selector state is finishedToday=true instead (see
     * that param), not this constant - it's kept here only as the sane default for callers that
     * skip the whole concept.
     */
    private static final int DEFAULT_FINISHED_WITHIN_DAYS = 7;

    /** America/Bogota, explicitly - never the server/JVM default zone (could be UTC in production), same care as the cron schedulers' explicit zone="America/Bogota". */
    private static final ZoneId BOGOTA_ZONE = ZoneId.of("America/Bogota");

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
     * Every ingested match, grouped by status priority (FINISHED, then LIVE, then SCHEDULED - see
     * MatchRepository.findAllFiltered's Javadoc for the exact per-group order) - optionally
     * narrowed by competition and/or a partial, case-insensitive match against either team name
     * (both combine with AND with each other and with the age filter below; pass null to skip
     * either one).
     *
     * <p>finishedToday and finishedWithinDays both narrow which FINISHED matches show
     * (SCHEDULED/LIVE are never affected by either, regardless of their startTime) - they're two
     * genuinely different concepts, not two spellings of the same one:
     * <ul>
     *   <li>finishedToday=true: only matches whose startTime falls on today's calendar date in
     *       America/Bogota - a fixed midnight-to-midnight window, not "the last 24 hours." This is
     *       the "Partidos" page's own default (no filter touched yet).</li>
     *   <li>finishedWithinDays: a rolling N-day window ending now - null falls back to
     *       {@link #DEFAULT_FINISHED_WITHIN_DAYS}, and {@link #SHOW_ALL_FINISHED_SENTINEL} (or any
     *       other non-positive value) disables the age filter entirely. This is what the page's
     *       selector sends once the user actually picks "Últimos N días" / "Todos".</li>
     * </ul>
     * When finishedToday is true, it wins over finishedWithinDays regardless of what the latter is
     * set to - the frontend never sends both at once (they're mutually exclusive select options),
     * but if a caller did, "today only" is the more specific ask.
     */
    @Transactional(readOnly = true)
    public List<MatchResponse> findAll(Long competitionId, String search, Integer finishedWithinDays, Boolean finishedToday) {
        Instant finishedCutoff = resolveFinishedCutoff(finishedWithinDays, finishedToday);
        List<Match> matches = matchRepository.findAllFiltered(competitionId, normalize(search), finishedCutoff);
        Set<Long> matchIdsWithOdds = new HashSet<>(oddsRepository.findMatchIdsWithOdds());
        return matches.stream()
                .map(match -> MatchResponse.from(match, matchIdsWithOdds.contains(match.getId())))
                .toList();
    }

    private static Instant resolveFinishedCutoff(Integer finishedWithinDays, Boolean finishedToday) {
        if (Boolean.TRUE.equals(finishedToday)) {
            return LocalDate.now(BOGOTA_ZONE).atStartOfDay(BOGOTA_ZONE).toInstant();
        }
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
