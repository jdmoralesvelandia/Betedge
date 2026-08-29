package com.betedge.odds;

import com.betedge.matches.Competition;
import com.betedge.matches.CompetitionRepository;
import com.betedge.matches.Match;
import com.betedge.matches.MatchRepository;
import com.betedge.matches.MatchStatus;
import com.betedge.valuebets.SurebetCalculationService;
import com.betedge.valuebets.ValueBetCalculationService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Ingests the complementary source (The Odds API) for the competitions it covers, reconciling
 * each fixture against Match rows already ingested by OddsPapi (the primary source) via
 * {@link MatchReconciliationService} rather than assuming any shared id scheme between the two
 * providers. Deliberately separate from {@link IngestionService}: independent scheduler, own
 * IngestionRun rows (provider=THEODDSAPI), no shared mutable state - a failure or slowdown here
 * can never affect the OddsPapi pipeline.
 */
@Service
@RequiredArgsConstructor
public class TheOddsApiIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TheOddsApiIngestionService.class);

    /** Same semantic market this codebase already uses for OddsPapi's full-time result - see IngestionService.MARKET_TYPE. */
    private static final String MARKET_TYPE = "moneyline";

    private static final String HOME_SELECTION = "home";
    private static final String AWAY_SELECTION = "away";
    private static final String DRAW_SELECTION = "draw";
    private static final String DRAW_OUTCOME_NAME = "draw";

    /** Prefixed so a The Odds API event id can never collide with an OddsPapi fixtureId's own scheme. */
    private static final String EXTERNAL_ID_PREFIX = "theoddsapi:";

    /**
     * Purely defensive: unlike OddsPapi's documented and confirmed 0.73s rate limit (see
     * IngestionService.CALL_DELAY_MS), The Odds API's limit (if any) has never actually been hit
     * or confirmed. 800ms is a reasonable precaution, not a measured minimum.
     */
    private static final int CALL_DELAY_MS = 800;

    private final CompetitionRepository competitionRepository;
    private final MatchRepository matchRepository;
    private final BookmakerRepository bookmakerRepository;
    private final OddsRepository oddsRepository;
    private final OddsDeduplicationService oddsDeduplicationService;
    private final IngestionRunRepository ingestionRunRepository;
    private final TheOddsApiClient theOddsApiClient;
    private final MatchReconciliationService matchReconciliationService;
    private final ValueBetCalculationService valueBetCalculationService;
    private final SurebetCalculationService surebetCalculationService;

    /** Curated set actually ingested per cycle - see theoddsapi-ingestion.bookmakers in application.yml. */
    @Value("${theoddsapi-ingestion.bookmakers}")
    private List<String> targetBookmakers;

    /** Fetches and ingests every competition The Odds API covers - see {@link #refreshCompetition} for a single one. */
    public IngestionRunResponse runIngestion(TriggeredBy triggeredBy) {
        Instant startedAt = Instant.now();

        List<Competition> competitions = competitionRepository.findAll();
        // Only competitions carrying a "theoddsapi" key are relevant here - Champions League has
        // none (see V11__seed_football_competitions.sql), so it's naturally skipped, not an error.
        Map<String, Competition> competitionsBySportKey = competitions.stream()
                .filter(c -> c.getExternalKeys().get(Competition.THEODDSAPI_KEY) != null)
                .collect(Collectors.toMap(
                        c -> c.getExternalKeys().get(Competition.THEODDSAPI_KEY), Function.identity()));

        Map<Long, Match> touchedMatches = new LinkedHashMap<>();
        List<CompetitionBreakdownEntry> breakdown = new ArrayList<>();

        boolean firstCall = true;
        for (Competition competition : competitionsBySportKey.values()) {
            if (!firstCall) {
                sleepBetweenCalls();
            }
            firstCall = false;

            breakdown.add(ingestCompetition(competition, touchedMatches));
        }

        return finishRun(triggeredBy, startedAt, touchedMatches, breakdown);
    }

    /**
     * Fetches and ingests a single competition - the same logic runIngestion loops over all six
     * with, extracted so HotMatchRefreshService can refresh just the one (or few) competitions
     * with a match in the final-hour window, each producing its own IngestionRun row (needed so
     * its monthly budget can be counted independently of the regular 12h cycle).
     */
    public IngestionRunResponse refreshCompetition(Competition competition, TriggeredBy triggeredBy) {
        Instant startedAt = Instant.now();
        Map<Long, Match> touchedMatches = new LinkedHashMap<>();
        CompetitionBreakdownEntry breakdown = ingestCompetition(competition, touchedMatches);
        return finishRun(triggeredBy, startedAt, touchedMatches, List.of(breakdown));
    }

    /**
     * The most recent The Odds API run (scheduled, manual, or hot-refresh), or empty if none has
     * run yet. Scoped to provider=THEODDSAPI - see the comment on
     * IngestionRunRepository.findTopByProviderOrderByFinishedAtDesc for why an unscoped query
     * would risk returning an OddsPapi run instead.
     */
    @Transactional(readOnly = true)
    public Optional<IngestionRunResponse> findLastRun() {
        return ingestionRunRepository.findTopByProviderOrderByFinishedAtDesc(DataSource.THEODDSAPI)
                .map(IngestionRunResponse::from);
    }

    private CompetitionBreakdownEntry ingestCompetition(Competition competition, Map<Long, Match> touchedMatches) {
        String sportKey = competition.getExternalKeys().get(Competition.THEODDSAPI_KEY);
        Accumulator accumulator = new Accumulator();

        List<TheOddsApiEventDto> events;
        try {
            events = theOddsApiClient.fetchOdds(sportKey);
        } catch (RestClientException e) {
            log.error("Failed to fetch odds from The Odds API for sport '{}'", sportKey, e);
            return new CompetitionBreakdownEntry(competition.getName(), 0, 0, 0);
        }

        for (TheOddsApiEventDto event : events) {
            accumulator.eventsReceived++;
            Match touchedMatch = ingestEvent(event, competition, accumulator);
            if (touchedMatch != null) {
                touchedMatches.put(touchedMatch.getId(), touchedMatch);
            }
        }

        return new CompetitionBreakdownEntry(
                competition.getName(), accumulator.eventsReceived, accumulator.matchesCreated, accumulator.oddsCreated);
    }

    private IngestionRunResponse finishRun(
            TriggeredBy triggeredBy, Instant startedAt, Map<Long, Match> touchedMatches,
            List<CompetitionBreakdownEntry> breakdown) {
        List<Match> matchesToEvaluate = new ArrayList<>(touchedMatches.values());
        int valueBetsDetected = valueBetCalculationService.calculateForMatches(matchesToEvaluate);
        int surebetsDetected = surebetCalculationService.calculateForMatches(matchesToEvaluate);

        IngestionRun run = new IngestionRun();
        run.setProvider(DataSource.THEODDSAPI);
        run.setTriggeredBy(triggeredBy);
        run.setStartedAt(startedAt);
        run.setFinishedAt(Instant.now());
        run.setTotalEventsReceived(breakdown.stream().mapToInt(CompetitionBreakdownEntry::eventsReceived).sum());
        run.setTotalNewMatches(breakdown.stream().mapToInt(CompetitionBreakdownEntry::newMatches).sum());
        run.setTotalNewOdds(breakdown.stream().mapToInt(CompetitionBreakdownEntry::newOdds).sum());
        run.setValueBetsDetected(valueBetsDetected);
        run.setSurebetsDetected(surebetsDetected);
        run.setCompetitionBreakdown(breakdown);

        return IngestionRunResponse.from(ingestionRunRepository.save(run));
    }

    /**
     * Returns the match touched by this event, or {@code null} if none of its bookmakers
     * resolved to a tracked Bookmaker row, or if the event has already kicked off.
     *
     * The already-started check can't happen earlier, before the fetchOdds call: that call is
     * per-competition, not per-event, and any real competition response mixes upcoming events
     * with ones already in progress - there's no way to know which without fetching first. So
     * this discards post-response, per-event, before any Match/Odds work happens for it. Real
     * incident this prevents: The Odds API keeps repricing "h2h" live once a match starts
     * (reacting to the live score) while OddsPapi silently stops updating the same fixture,
     * leaving a stale pre-match snapshot - ingesting the live-repriced number here would corrupt
     * Odds with a value that was never comparable to the frozen one already stored, regardless of
     * whether ValueBetCalculationService's own isEligible would later exclude the match from
     * calculation. Better to never store it at all.
     */
    private Match ingestEvent(TheOddsApiEventDto event, Competition competition, Accumulator accumulator) {
        if (event.bookmakers() == null || event.bookmakers().isEmpty()) {
            return null;
        }
        if (event.commenceTime().isBefore(Instant.now())) {
            log.debug("Skipping event {} ({} vs {}): already started at {}",
                    event.id(), event.homeTeam(), event.awayTeam(), event.commenceTime());
            return null;
        }

        Match match = null;

        for (TheOddsApiBookmakerDto bookmakerDto : event.bookmakers()) {
            // Explicit curated list now (2026-08-26), same pattern as IngestionService's
            // targetBookmakers for OddsPapi - see the comment on theoddsapi-ingestion.bookmakers
            // in application.yml for how this list was derived (confirmed clone pairs and
            // exchanges excluded, against real price data). Checked before even querying the DB,
            // so a bookmaker.key() the Bookmaker table happens to have a row for (that ~230-row
            // orphaned catalog - see ReferenceDataSyncService) but that ISN'T on this list still
            // gets skipped here.
            if (!targetBookmakers.contains(bookmakerDto.key())) {
                continue;
            }

            Bookmaker bookmaker = bookmakerRepository.findByExternalKey(bookmakerDto.key()).orElse(null);
            if (bookmaker == null) {
                continue; // on the curated list but no Bookmaker row yet - skip defensively
            }

            List<TheOddsApiMarketDto> markets = bookmakerDto.markets();
            if (markets == null || markets.isEmpty()) {
                continue; // already h2h-only per TheOddsApiClient's own filtering; empty means none survived
            }

            for (TheOddsApiMarketDto market : markets) {
                List<TheOddsApiOutcomeDto> outcomes = market.outcomes();
                if (outcomes == null || outcomes.isEmpty()) {
                    continue;
                }

                if (match == null) {
                    MatchLookup lookup = findOrCreateMatch(event, competition);
                    match = lookup.match();
                    if (lookup.created()) {
                        accumulator.matchesCreated++;
                    }
                }

                for (TheOddsApiOutcomeDto outcome : outcomes) {
                    String selection = resolveSelection(outcome.name(), event);
                    if (selection == null) {
                        log.warn("Outcome '{}' didn't match home/away/draw for event {} ({} vs {}) - skipped",
                                outcome.name(), event.id(), event.homeTeam(), event.awayTeam());
                        continue;
                    }

                    // Skip the insert entirely when the price hasn't moved since the last known
                    // snapshot for this exact (match, bookmaker, selection) - see
                    // OddsDeduplicationService's own Javadoc for why.
                    if (oddsDeduplicationService.isUnchanged(match, bookmaker, selection, DataSource.THEODDSAPI, outcome.price())) {
                        continue;
                    }
                    Odds odds = new Odds();
                    odds.setMatch(match);
                    odds.setBookmaker(bookmaker);
                    odds.setMarketType(MARKET_TYPE);
                    odds.setSelection(selection);
                    odds.setOddValue(outcome.price());
                    odds.setDataSource(DataSource.THEODDSAPI);
                    oddsRepository.save(odds);
                    accumulator.oddsCreated++;
                }
            }
        }

        return match;
    }

    private MatchLookup findOrCreateMatch(TheOddsApiEventDto event, Competition competition) {
        Optional<Match> reconciled = matchReconciliationService.findMatchingCandidate(
                competition, event.homeTeam(), event.awayTeam(), event.commenceTime());
        if (reconciled.isPresent()) {
            return new MatchLookup(reconciled.get(), false);
        }

        // Reconciliation can go from "no candidate" (this event's first run, before OddsPapi has
        // ingested the same fixture) to "ambiguous" (a later run, once OddsPapi's own independent
        // scheduler creates its Match for the same fixture) - ambiguous also returns empty, per
        // findMatchingCandidate's never-guess rule. Without this externalId lookup, re-processing
        // the same event in that second case would try to INSERT a Match with the same
        // "theoddsapi:" + event.id() a second time and crash on Match.externalId's unique
        // constraint, instead of just reusing the Match this service already created for it.
        String externalId = EXTERNAL_ID_PREFIX + event.id();
        return matchRepository.findByExternalId(externalId)
                .map(match -> new MatchLookup(match, false))
                .orElseGet(() -> {
                    Match match = new Match();
                    match.setCompetition(competition);
                    match.setExternalId(externalId);
                    match.setHomeTeam(event.homeTeam());
                    match.setAwayTeam(event.awayTeam());
                    match.setStartTime(event.commenceTime());
                    match.setStatus(MatchStatus.SCHEDULED);
                    return new MatchLookup(matchRepository.save(match), true);
                });
    }

    /**
     * The Odds API labels outcomes with the literal team name (or "Draw"), not a fixed
     * home/away/draw code like OddsPapi's bookmakerOutcomeId - see TheOddsApiOutcomeDto. Mapped
     * here to the same "home"/"away"/"draw" strings OddsPapi's ingestion already uses (confirmed
     * against real OddsPapi data: bookmakerOutcomeId is literally "home"/"away"/"draw" for the
     * full-time moneyline market), since ValueBetCalculationService/SurebetCalculationService and
     * OddsRepository.findLatestOddsByMatch's "most recent wins" query all key on Odds.selection -
     * a mismatched scheme here would silently keep the two sources' prices from ever being
     * compared against each other, defeating the whole point of this integration. Compared
     * against the event's OWN homeTeam/awayTeam (not the possibly-differently-spelled reconciled
     * Match) since both come from the same provider response and should spell a team identically.
     */
    private static String resolveSelection(String outcomeName, TheOddsApiEventDto event) {
        if (outcomeName == null) {
            return null;
        }
        String trimmed = outcomeName.trim();
        if (trimmed.equalsIgnoreCase(event.homeTeam())) {
            return HOME_SELECTION;
        }
        if (trimmed.equalsIgnoreCase(event.awayTeam())) {
            return AWAY_SELECTION;
        }
        if (trimmed.equalsIgnoreCase(DRAW_OUTCOME_NAME)) {
            return DRAW_SELECTION;
        }
        return null;
    }

    private static void sleepBetweenCalls() {
        try {
            Thread.sleep(CALL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record MatchLookup(Match match, boolean created) {
    }

    private static final class Accumulator {
        int eventsReceived;
        int matchesCreated;
        int oddsCreated;
    }
}
