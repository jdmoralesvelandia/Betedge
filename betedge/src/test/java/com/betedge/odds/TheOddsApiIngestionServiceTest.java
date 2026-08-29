package com.betedge.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.betedge.matches.Match;
import com.betedge.matches.MatchRepository;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-stack verification of the Paso C integration against a real (embedded, throwaway) Postgres
 * - both HTTP clients are mocked, but everything from the two ingestion services down through
 * Flyway's actual schema and the real DISTINCT ON "latest odds" query runs for real. Never touches
 * the developer's own Postgres: a fresh instance is started for this test class and torn down in
 * {@link #stopEmbeddedPostgres()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TheOddsApiIngestionServiceTest {

    private static EmbeddedPostgres embeddedPostgres;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) throws IOException {
        embeddedPostgres = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stopEmbeddedPostgres() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @MockitoBean
    private OddsPapiClient oddsPapiClient;

    @MockitoBean
    private TheOddsApiClient theOddsApiClient;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private TheOddsApiIngestionService theOddsApiIngestionService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private OddsRepository oddsRepository;

    private static final Instant KICKOFF = Instant.parse("2026-09-12T18:00:00Z");
    private static final String PREMIER_LEAGUE_ODDSPAPI_TOURNAMENT_ID = "17";
    private static final String PREMIER_LEAGUE_THEODDSAPI_SPORT_KEY = "soccer_epl";
    private static final String RECONCILING_FIXTURE_ID = "oddspapi-fixture-1";
    private static final String NEW_EVENT_EXTERNAL_ID = "theoddsapi:theodds-evt-2";

    // Keeps one Hibernate session open for the whole method, purely so the assertions below can
    // resolve Odds.bookmaker's lazy proxy (open-in-view is disabled project-wide, same reason
    // ValueBetCalculationService/SurebetCalculationService need @Transactional). The three
    // runIngestion() calls below aren't @Transactional themselves in production, but nothing
    // asserted here depends on their normal per-call transaction boundaries.
    @Test
    @Transactional
    void reconcilesAgainstOddsPapiMatchCreatesNewMatchWhenUnmatchedAndNeverIngestsPinnacleFromThisSource() {
        // --- Arrange: OddsPapi returns one fixture (Arsenal vs Chelsea, tournament 17 = Premier
        // League per V11's seed data), with a full-time-moneyline Pinnacle price for all 3 selections.
        when(oddsPapiClient.fetchOddsByTournaments(anyList(), anyString()))
                .thenReturn(List.of(oddsPapiFixture(new BigDecimal("1.80"))));
        when(oddsPapiClient.fetchParticipantNames(anyInt()))
                .thenReturn(Map.of("101", "Arsenal", "102", "Chelsea"));

        // The Odds API (soccer_epl) returns two events: the SAME real fixture (Arsenal vs Chelsea,
        // same kickoff) that OddsPapi already ingested, plus a second fixture OddsPapi never saw.
        // Every other tracked league's sportKey is left unstubbed (Mockito returns List.of()), so
        // this test only ever deals with Premier League data. Each event carries TWO bookmakers -
        // "pinnacle" and "gtbets" - deliberately: pinnacle is a real bookmaker.key() The Odds API
        // genuinely returns (it was in theoddsapi-ingestion.bookmakers until 2026-08-28), so this
        // proves the curated-list filter excludes it specifically, not just because the event
        // happened to carry no ingestible bookmaker at all - gtbets (still curated) is the control
        // that proves normal ingestion/reconciliation is otherwise unaffected.
        when(theOddsApiClient.fetchOdds(PREMIER_LEAGUE_THEODDSAPI_SPORT_KEY))
                .thenReturn(List.of(reconcilingEvent(), newEvent()));

        // --- Act 1: OddsPapi ingests first, as it would in production (its own independent
        // schedule) - creates the Match and its Pinnacle odds.
        ingestionService.runIngestion(TriggeredBy.MANUAL);

        Match oddsPapiMatch = matchRepository.findByExternalId(RECONCILING_FIXTURE_ID).orElseThrow();
        List<Odds> afterOddsPapiOnly = oddsRepository.findByMatchIdOrderByTimestampAsc(oddsPapiMatch.getId());
        assertThat(afterOddsPapiOnly).hasSize(3); // home/away/draw, all dataSource=ODDSPAPI, bookmaker=pinnacle

        // --- Act 2: The Odds API ingests, independently.
        theOddsApiIngestionService.runIngestion(TriggeredBy.MANUAL);

        // Case: reconciles against the existing Match - no duplicate created for the same fixture.
        // Compared by id, not by the Match object itself: each repository call opens its own
        // session, so Match (no custom equals()) would never be reference-equal across them even
        // when both calls resolve to the very same row.
        assertThat(matchRepository.findByExternalId(RECONCILING_FIXTURE_ID)).map(Match::getId).contains(oddsPapiMatch.getId());

        List<Odds> afterBothSources = oddsRepository.findByMatchIdOrderByTimestampAsc(oddsPapiMatch.getId());
        // 3 more, not 6: gtbets's home/away/draw only - pinnacle's own 3 from this event are
        // filtered out entirely by targetBookmakers before a Bookmaker lookup even happens (see
        // TheOddsApiIngestionService.ingestEvent).
        assertThat(afterBothSources).hasSize(6);

        // Case: pinnacle stays exclusively OddsPapi's - never a second, THEODDSAPI-sourced row for
        // the same triad. This is the actual structural fix (2026-08-28): pinnacle was removed
        // from theoddsapi-ingestion.bookmakers specifically so this can no longer happen - see
        // application.yml's own comment and OddsDeduplicationService's incident writeup for why a
        // source-aware dedup check alone wasn't considered enough.
        List<Odds> pinnacleHomeRows = afterBothSources.stream()
                .filter(o -> o.getBookmaker().getExternalKey().equals("pinnacle"))
                .filter(o -> o.getSelection().equals("home"))
                .toList();
        assertThat(pinnacleHomeRows).hasSize(1);
        assertThat(pinnacleHomeRows.get(0).getDataSource()).isEqualTo(DataSource.ODDSPAPI);

        // Case: gtbets (still curated) ingests normally from The Odds API - the exclusion above
        // is specific to pinnacle, not a side effect of some broader breakage.
        List<Odds> gtbetsHomeRows = afterBothSources.stream()
                .filter(o -> o.getBookmaker().getExternalKey().equals("gtbets"))
                .filter(o -> o.getSelection().equals("home"))
                .toList();
        assertThat(gtbetsHomeRows).hasSize(1);
        assertThat(gtbetsHomeRows.get(0).getDataSource()).isEqualTo(DataSource.THEODDSAPI);

        // Case: no candidate found for the second event - a new Match is created with the
        // "theoddsapi:" prefix, distinct from OddsPapi's own fixtureId scheme. (Its own pinnacle
        // bookmaker is excluded the same way; only its gtbets odds actually get ingested.)
        Optional<Match> newMatch = matchRepository.findByExternalId(NEW_EVENT_EXTERNAL_ID);
        assertThat(newMatch).isPresent();
        assertThat(newMatch.get().getHomeTeam()).isEqualTo("Manchester United");
        assertThat(newMatch.get().getAwayTeam()).isEqualTo("Liverpool");
        assertThat(matchRepository.findAll()).hasSize(2); // exactly these two, no stray duplicates

        // --- Act 3: OddsPapi ingests again (its own later poll), this time with a genuinely
        // different price (1.80 -> 1.75) - proves the source-aware dedup fix still lets a real
        // price change through (it's a defense in depth, not what's carrying this test's main
        // point anymore - see OddsDeduplicationServiceTest for that logic in isolation). A
        // same-price re-poll here (still "1.80") would correctly insert nothing.
        when(oddsPapiClient.fetchOddsByTournaments(anyList(), anyString()))
                .thenReturn(List.of(oddsPapiFixture(new BigDecimal("1.75"))));
        ingestionService.runIngestion(TriggeredBy.MANUAL);

        List<Odds> pinnacleHomeRowsAfterThirdRun = oddsRepository.findByMatchIdOrderByTimestampAsc(oddsPapiMatch.getId()).stream()
                .filter(o -> o.getBookmaker().getExternalKey().equals("pinnacle"))
                .filter(o -> o.getSelection().equals("home"))
                .toList();
        assertThat(pinnacleHomeRowsAfterThirdRun).hasSize(2); // the genuine change, correctly inserted
        assertThat(pinnacleHomeRowsAfterThirdRun).allMatch(o -> o.getDataSource() == DataSource.ODDSPAPI);
        assertThat(pinnacleHomeRowsAfterThirdRun.get(1).getOddValue()).isEqualByComparingTo("1.75");
    }

    private static OddsPapiFixtureDto oddsPapiFixture(BigDecimal homePrice) {
        // One OddsPapiOutcomeDto per selection, keyed by OddsPapi's own outcome id (101/102/103) -
        // IngestionService.OUTCOME_KEY_TO_SELECTION reads the selection from this outer map key,
        // not from OddsPapiPlayerPriceDto.bookmakerOutcomeId (that field is bookmaker-internal and
        // only ever used as-is for Odds.selection on the OLD, pre-fix code path). bookmakerOutcomeId
        // is set to a realistic opaque value here precisely to make clear it's NOT what selection
        // comes from. homePrice is parameterized so callers can simulate a genuine OddsPapi-side
        // price move across two calls (see Act 3's own comment above).
        OddsPapiOutcomeDto homeOutcome = new OddsPapiOutcomeDto(
                Map.of("p1", new OddsPapiPlayerPriceDto(true, "48601", homePrice)));
        OddsPapiOutcomeDto drawOutcome = new OddsPapiOutcomeDto(
                Map.of("p1", new OddsPapiPlayerPriceDto(true, "48602", new BigDecimal("3.60"))));
        OddsPapiOutcomeDto awayOutcome = new OddsPapiOutcomeDto(
                Map.of("p1", new OddsPapiPlayerPriceDto(true, "48603", new BigDecimal("4.20"))));
        OddsPapiMarketDto moneyline = new OddsPapiMarketDto(
                "101/0/moneyline", true, Map.of("101", homeOutcome, "102", drawOutcome, "103", awayOutcome));
        // Key must be the real OddsPapi market id ("101"), not an arbitrary placeholder -
        // OddsPapiMarketDto.isFullTimeMoneyline requires the caller's own map key (this one) to be
        // exactly "101", on top of the bookmakerMarketId suffix check - see its class Javadoc.
        OddsPapiBookmakerOddsDto pinnacleOdds = new OddsPapiBookmakerOddsDto(true, false, Map.of("101", moneyline));
        return new OddsPapiFixtureDto(
                RECONCILING_FIXTURE_ID, 101L, 102L,
                Integer.valueOf(PREMIER_LEAGUE_ODDSPAPI_TOURNAMENT_ID), KICKOFF, Map.of("pinnacle", pinnacleOdds));
    }

    private static TheOddsApiEventDto reconcilingEvent() {
        return theOddsApiEvent("theodds-evt-1", "Arsenal", "Chelsea", KICKOFF);
    }

    private static TheOddsApiEventDto newEvent() {
        return theOddsApiEvent("theodds-evt-2", "Manchester United", "Liverpool", KICKOFF.plus(1, ChronoUnit.DAYS));
    }

    private static TheOddsApiEventDto theOddsApiEvent(String id, String homeTeam, String awayTeam, Instant commenceTime) {
        List<TheOddsApiOutcomeDto> outcomes = List.of(
                new TheOddsApiOutcomeDto(homeTeam, new BigDecimal("1.85")),
                new TheOddsApiOutcomeDto(awayTeam, new BigDecimal("4.10")),
                new TheOddsApiOutcomeDto("Draw", new BigDecimal("3.55")));
        TheOddsApiMarketDto h2h = new TheOddsApiMarketDto("h2h", commenceTime, outcomes);
        // Two bookmakers per event on purpose - see this test method's own comment on why both are
        // needed: pinnacle (a real bookmaker.key() The Odds API still returns, but no longer on
        // theoddsapi-ingestion.bookmakers since 2026-08-28) proves the exclusion; gtbets (still
        // curated) is the control proving normal ingestion is otherwise unaffected.
        TheOddsApiBookmakerDto pinnacle = new TheOddsApiBookmakerDto("pinnacle", "Pinnacle", commenceTime, List.of(h2h));
        TheOddsApiBookmakerDto gtbets = new TheOddsApiBookmakerDto("gtbets", "GTbets", commenceTime, List.of(h2h));
        return new TheOddsApiEventDto(
                id, PREMIER_LEAGUE_THEODDSAPI_SPORT_KEY, "EPL", commenceTime, homeTeam, awayTeam, List.of(pinnacle, gtbets));
    }
}
