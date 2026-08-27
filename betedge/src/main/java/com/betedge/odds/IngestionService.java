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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final String MARKET_TYPE = "moneyline";

    /**
     * OddsPapi's own documented outcome keys for market 101 (Full Time Result), not each
     * bookmaker's own bookmakerOutcomeId - the latter is a bookmaker-internal identifier
     * (literal "home"/"draw"/"away" for Pinnacle/bet365/unibet by convention, but a raw
     * numeric id specific to that bookmaker's own system for betano/betplay). Keying off
     * OddsPapi's own outer map key instead works identically for every bookmaker: confirmed
     * against 30 real fixtures (15 betano + 15 betplay, all 5 tracked leagues, 2026-08-25)
     * that price[outcomeKey="101"] lines up with Pinnacle's own home price for the same
     * fixtureId (same order of magnitude, never inverted), and separately confirmed Pinnacle
     * itself uses these same outer keys (101/102/103) mapped 1:1 to its own "home"/"draw"/
     * "away" bookmakerOutcomeId - so this is a no-op change for Pinnacle, not just a fix for
     * the other two.
     */
    private static final Map<String, String> OUTCOME_KEY_TO_SELECTION = Map.of(
            "101", "home",
            "102", "draw",
            "103", "away");

    private final CompetitionRepository competitionRepository;
    private final MatchRepository matchRepository;
    private final BookmakerRepository bookmakerRepository;
    private final OddsRepository oddsRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final OddsPapiClient oddsPapiClient;
    private final ReferenceDataSyncService referenceDataSyncService;
    private final MatchReconciliationService matchReconciliationService;
    private final ValueBetCalculationService valueBetCalculationService;
    private final SurebetCalculationService surebetCalculationService;

    /**
     * Fixed, small set of bookmakers actually queried per ingestion cycle, each independently
     * confirmed non-cloned by hand - see the comment on odds-ingestion.bookmakers in
     * application.yml for the budget math this size (and the tournament-id batching below) is
     * tied to.
     */
    @Value("${odds-ingestion.bookmakers}")
    private List<String> targetBookmakers;

    public IngestionRunResponse runIngestion(TriggeredBy triggeredBy) {
        Instant startedAt = Instant.now();

        List<Competition> competitions = competitionRepository.findAll();
        // Only competitions carrying an "oddspapi" key are relevant to this (OddsPapi-only) pass;
        // a future TheOddsApi-only competition would simply be skipped here, not crash on a null key.
        Map<String, Competition> competitionsByOddsPapiKey = competitions.stream()
                .filter(c -> c.getExternalKeys().get(Competition.ODDSPAPI_KEY) != null)
                .collect(Collectors.toMap(c -> c.getExternalKeys().get(Competition.ODDSPAPI_KEY), Function.identity()));

        Map<String, String> participantNames = referenceDataSyncService.getParticipantNames();

        List<OddsPapiFixtureDto> fixtures =
                fetchFixturesSafely(competitionsByOddsPapiKey.keySet(), targetBookmakers);

        Map<String, Accumulator> accumulators = new HashMap<>();
        for (String oddsPapiKey : competitionsByOddsPapiKey.keySet()) {
            accumulators.put(oddsPapiKey, new Accumulator());
        }

        Map<Long, Match> touchedMatches = new LinkedHashMap<>();

        for (OddsPapiFixtureDto fixture : fixtures) {
            String tournamentKey = String.valueOf(fixture.tournamentId());
            Competition competition = competitionsByOddsPapiKey.get(tournamentKey);
            Accumulator accumulator = accumulators.get(tournamentKey);
            if (competition == null || accumulator == null) {
                continue; // fixture for a tournament we don't track
            }

            accumulator.eventsReceived++;
            Match touchedMatch = ingestFixture(fixture, competition, participantNames, accumulator);
            if (touchedMatch != null) {
                touchedMatches.put(touchedMatch.getId(), touchedMatch);
            }
        }

        List<Match> matchesToEvaluate = new ArrayList<>(touchedMatches.values());
        int valueBetsDetected = valueBetCalculationService.calculateForMatches(matchesToEvaluate);
        int surebetsDetected = surebetCalculationService.calculateForMatches(matchesToEvaluate);

        List<CompetitionBreakdownEntry> breakdown = competitionsByOddsPapiKey.values().stream()
                .map(c -> {
                    Accumulator accumulator = accumulators.get(c.getExternalKeys().get(Competition.ODDSPAPI_KEY));
                    return new CompetitionBreakdownEntry(
                            c.getName(), accumulator.eventsReceived, accumulator.matchesCreated,
                            accumulator.oddsCreated);
                })
                .toList();

        IngestionRun run = new IngestionRun();
        run.setProvider(DataSource.ODDSPAPI);
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

    /** The most recent ingestion run (scheduled or manual), or empty if none has run yet. */
    @Transactional(readOnly = true)
    public Optional<IngestionRunResponse> findLastRun() {
        return ingestionRunRepository.findTopByProviderOrderByFinishedAtDesc(DataSource.ODDSPAPI)
                .map(IngestionRunResponse::from);
    }

    public Instant getMostRecentOddsTimestamp() {
        return oddsRepository.findMostRecentTimestamp();
    }

    /** Returns the match touched by this fixture, or {@code null} if it had no valid moneyline data at all. */
    private Match ingestFixture(
            OddsPapiFixtureDto fixture,
            Competition competition,
            Map<String, String> participantNames,
            Accumulator accumulator) {

        Map<String, OddsPapiBookmakerOddsDto> bookmakerOdds = fixture.bookmakerOdds();
        if (bookmakerOdds == null || bookmakerOdds.isEmpty()) {
            return null; // fixture without any bookmaker line yet - normal, not an error
        }

        Match match = null;

        // No clone-filtering here: bookmakerOdds only ever carries keys we explicitly asked for
        // (targetBookmakers), each already confirmed non-cloned by hand - see the comment on
        // odds-ingestion.bookmakers in application.yml.
        for (Map.Entry<String, OddsPapiBookmakerOddsDto> entry : bookmakerOdds.entrySet()) {
            String bookmakerKey = entry.getKey();
            OddsPapiBookmakerOddsDto bookmakerBlock = entry.getValue();
            if (!bookmakerBlock.bookmakerIsActive() || bookmakerBlock.suspended()) {
                continue;
            }

            Map<String, OddsPapiMarketDto> markets = bookmakerBlock.markets();
            if (markets == null) {
                continue;
            }

            for (Map.Entry<String, OddsPapiMarketDto> marketEntry : markets.entrySet()) {
                OddsPapiMarketDto market = marketEntry.getValue();
                // isFullTimeMoneyline needs the market's own map key (its numeric OddsPapi
                // market id, e.g. "101") - not visible from the OddsPapiMarketDto value alone -
                // see the class-level comment on OddsPapiMarketDto for why the bookmakerMarketId
                // suffix check by itself isn't enough.
                if (!market.marketActive() || !market.isFullTimeMoneyline(marketEntry.getKey())) {
                    continue;
                }

                Map<String, OddsPapiOutcomeDto> outcomes = market.outcomes();
                if (outcomes == null || outcomes.isEmpty()) {
                    continue;
                }

                if (match == null) {
                    MatchLookup lookup = findOrCreateMatch(fixture, competition, participantNames);
                    match = lookup.match();
                    if (lookup.created()) {
                        accumulator.matchesCreated++;
                    }
                }

                Bookmaker bookmaker = bookmakerRepository.findByExternalKey(bookmakerKey).orElse(null);
                if (bookmaker == null) {
                    continue; // reference-data cache and DB disagree; skip defensively
                }

                for (Map.Entry<String, OddsPapiOutcomeDto> outcomeEntry : outcomes.entrySet()) {
                    String selection = OUTCOME_KEY_TO_SELECTION.get(outcomeEntry.getKey());
                    if (selection == null) {
                        continue; // not one of the 3 documented Full Time Result outcome keys
                    }
                    OddsPapiOutcomeDto outcome = outcomeEntry.getValue();
                    if (outcome.players() == null) {
                        continue;
                    }
                    for (OddsPapiPlayerPriceDto price : outcome.players().values()) {
                        Odds odds = new Odds();
                        odds.setMatch(match);
                        odds.setBookmaker(bookmaker);
                        odds.setMarketType(MARKET_TYPE);
                        odds.setSelection(selection);
                        odds.setOddValue(price.price());
                        odds.setDataSource(DataSource.ODDSPAPI);
                        oddsRepository.save(odds);
                        accumulator.oddsCreated++;
                    }
                }
            }
        }

        return match;
    }

    /**
     * OddsPapi is the primary source, but it isn't guaranteed to be the FIRST to see a given
     * real-world fixture: The Odds API runs on its own independent schedule and can ingest (and
     * create a Match for) the same fixture first. Without this reconciliation attempt, that
     * ordering alone used to produce a silent duplicate - two Match rows for one real game, split
     * ODDSPAPI vs THEODDSAPI Odds between them - regardless of how good the name-matching logic
     * was, simply because this path never even tried it. Mirrors
     * TheOddsApiIngestionService.findOrCreateMatch's own reconciliation step exactly: on a
     * successful reconcile, the existing row (whatever its externalId or original source) is
     * reused as-is - never overwritten - same as that side does. Nothing in this codebase reads
     * Match.externalId's format to infer which provider "owns" a Match (confirmed 2026-08-27 -
     * its only functional use anywhere is the exact-string findByExternalId lookup below), so a
     * Match created by The Odds API keeping its "theoddsapi:"-prefixed externalId after OddsPapi
     * reconciles onto it is safe.
     */
    private MatchLookup findOrCreateMatch(
            OddsPapiFixtureDto fixture, Competition competition, Map<String, String> participantNames) {
        Optional<Match> existing = matchRepository.findByExternalId(fixture.fixtureId());
        if (existing.isPresent()) {
            return new MatchLookup(existing.get(), false);
        }

        String homeTeam = resolveParticipantName(fixture.participant1Id(), participantNames);
        String awayTeam = resolveParticipantName(fixture.participant2Id(), participantNames);

        Optional<Match> reconciled = matchReconciliationService.findMatchingCandidate(
                competition, homeTeam, awayTeam, fixture.startTime());
        if (reconciled.isPresent()) {
            return new MatchLookup(reconciled.get(), false);
        }

        Match match = new Match();
        match.setCompetition(competition);
        match.setExternalId(fixture.fixtureId());
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setStartTime(fixture.startTime());
        match.setStatus(MatchStatus.SCHEDULED);
        return new MatchLookup(matchRepository.save(match), true);
    }

    private static String resolveParticipantName(Long participantId, Map<String, String> participantNames) {
        if (participantId == null) {
            return "Unknown participant";
        }
        return participantNames.getOrDefault(
                String.valueOf(participantId), "Unknown participant (" + participantId + ")");
    }

    /**
     * Confirmed against the real API: back-to-back calls to /odds-by-tournaments trip OddsPapi's
     * rate limiter ("429 RATE_LIMITED ... wait 0.73 seconds"). With no delay, only the very first
     * call in the whole cycle ever succeeds and the rest fail silently (caught below) - which is
     * especially bad here since ValueBetCalculationService needs >=3 bookmakers per match to
     * detect anything at all. 1500ms comfortably clears the observed ~0.73s minimum. Applies
     * between every call, not just between bookmakers - the limit is per-endpoint, not per-bookmaker.
     */
    private static final int CALL_DELAY_MS = 1500;

    /** Confirmed against the real API: a 6th tournamentId in one call returns 400 INVALID_PARAMETER. */
    private static final int MAX_TOURNAMENT_IDS_PER_CALL = 5;

    /**
     * OddsPapi requires exactly one bookmaker AND at most {@link #MAX_TOURNAMENT_IDS_PER_CALL}
     * tournamentIds per /odds-by-tournaments call (see OddsPapiClient.fetchOddsByTournaments), so
     * this fetches once per (configured target bookmaker x tournament-id batch) pair and merges
     * the results by fixtureId - each response only carries that call's bookmaker's entry in
     * bookmakerOdds, so fixtures seen under more than one bookmaker get their bookmakerOdds maps
     * combined rather than overwritten. A single call failing (logged below) doesn't abort the rest.
     */
    private List<OddsPapiFixtureDto> fetchFixturesSafely(Set<String> tournamentIds, List<String> bookmakerKeys) {
        List<List<String>> tournamentIdBatches = chunk(List.copyOf(tournamentIds), MAX_TOURNAMENT_IDS_PER_CALL);
        Map<String, OddsPapiFixtureDto> fixturesByExternalId = new LinkedHashMap<>();

        boolean firstCall = true;
        for (String bookmakerKey : bookmakerKeys) {
            for (List<String> tournamentIdBatch : tournamentIdBatches) {
                if (!firstCall) {
                    sleepBetweenCalls();
                }
                firstCall = false;

                List<OddsPapiFixtureDto> fixtures;
                try {
                    fixtures = oddsPapiClient.fetchOddsByTournaments(tournamentIdBatch, bookmakerKey);
                } catch (RestClientException e) {
                    log.error("Failed to fetch odds-by-tournaments from OddsPapi for bookmaker '{}', tournaments {}",
                            bookmakerKey, tournamentIdBatch, e);
                    continue;
                }
                for (OddsPapiFixtureDto fixture : fixtures) {
                    fixturesByExternalId.merge(fixture.fixtureId(), fixture, IngestionService::mergeBookmakerOdds);
                }
            }
        }

        return List.copyOf(fixturesByExternalId.values());
    }

    private static List<List<String>> chunk(List<String> items, int maxSize) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += maxSize) {
            chunks.add(items.subList(i, Math.min(i + maxSize, items.size())));
        }
        return chunks;
    }

    private static void sleepBetweenCalls() {
        try {
            Thread.sleep(CALL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static OddsPapiFixtureDto mergeBookmakerOdds(OddsPapiFixtureDto a, OddsPapiFixtureDto b) {
        Map<String, OddsPapiBookmakerOddsDto> combined = new HashMap<>();
        if (a.bookmakerOdds() != null) {
            combined.putAll(a.bookmakerOdds());
        }
        if (b.bookmakerOdds() != null) {
            combined.putAll(b.bookmakerOdds());
        }
        return new OddsPapiFixtureDto(
                a.fixtureId(), a.participant1Id(), a.participant2Id(), a.tournamentId(), a.startTime(), combined);
    }

    private record MatchLookup(Match match, boolean created) {
    }

    private static final class Accumulator {
        int eventsReceived;
        int matchesCreated;
        int oddsCreated;
    }
}
