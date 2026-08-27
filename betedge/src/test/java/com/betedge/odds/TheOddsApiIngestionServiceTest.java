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
    void reconcilesAgainstOddsPapiMatchCreatesNewMatchWhenUnmatchedAndLatestOddsIgnoresSource() {
        // --- Arrange: OddsPapi returns one fixture (Arsenal vs Chelsea, tournament 17 = Premier
        // League per V11's seed data), with a full-time-moneyline Pinnacle price for all 3 selections.
        when(oddsPapiClient.fetchOddsByTournaments(anyList(), anyString()))
                .thenReturn(List.of(oddsPapiFixture()));
        when(oddsPapiClient.fetchParticipantNames(anyInt()))
                .thenReturn(Map.of("101", "Arsenal", "102", "Chelsea"));

        // The Odds API (soccer_epl) returns two events: the SAME real fixture (Arsenal vs Chelsea,
        // same kickoff) that OddsPapi already ingested, plus a second fixture OddsPapi never saw.
        // Every other tracked league's sportKey is left unstubbed (Mockito returns List.of()), so
        // this test only ever deals with Premier League data.
        when(theOddsApiClient.fetchOdds(PREMIER_LEAGUE_THEODDSAPI_SPORT_KEY))
                .thenReturn(List.of(reconcilingEvent(), newEvent()));

        // --- Act 1: OddsPapi ingests first, as it would in production (its own independent
        // schedule) - creates the Match and its Pinnacle odds.
        ingestionService.runIngestion(TriggeredBy.MANUAL);

        Match oddsPapiMatch = matchRepository.findByExternalId(RECONCILING_FIXTURE_ID).orElseThrow();
        List<Odds> afterOddsPapiOnly = oddsRepository.findByMatchIdOrderByTimestampAsc(oddsPapiMatch.getId());
        assertThat(afterOddsPapiOnly).hasSize(3); // home/away/draw, all dataSource=ODDSPAPI

        // --- Act 2: The Odds API ingests, independently.
        theOddsApiIngestionService.runIngestion(TriggeredBy.MANUAL);

        // Case: reconciles against the existing Match - no duplicate created for the same fixture.
        // Compared by id, not by the Match object itself: each repository call opens its own
        // session, so Match (no custom equals()) would never be reference-equal across them even
        // when both calls resolve to the very same row.
        assertThat(matchRepository.findByExternalId(RECONCILING_FIXTURE_ID)).map(Match::getId).contains(oddsPapiMatch.getId());

        List<Odds> afterBothSources = oddsRepository.findByMatchIdOrderByTimestampAsc(oddsPapiMatch.getId());
        assertThat(afterBothSources).hasSize(6); // 3 more: home/away/draw, dataSource=THEODDSAPI

        // Case: two Pinnacle rows (one per source) coexist for the same match+selection.
        List<Odds> pinnacleHomeRows = afterBothSources.stream()
                .filter(o -> o.getBookmaker().getExternalKey().equals("pinnacle"))
                .filter(o -> o.getSelection().equals("home"))
                .toList();
        assertThat(pinnacleHomeRows).hasSize(2);
        assertThat(pinnacleHomeRows).extracting(Odds::getDataSource)
                .containsExactlyInAnyOrder(DataSource.ODDSPAPI, DataSource.THEODDSAPI);

        // Case: "latest wins" picks TheOddsApi's row here, since it was inserted second (later
        // timestamp) - not because of anything specific to which source it is.
        Odds latestPinnacleHome = latestPinnacleHome(oddsPapiMatch.getId());
        assertThat(latestPinnacleHome.getDataSource()).isEqualTo(DataSource.THEODDSAPI);

        // Case: no candidate found for the second event - a new Match is created with the
        // "theoddsapi:" prefix, distinct from OddsPapi's own fixtureId scheme.
        Optional<Match> newMatch = matchRepository.findByExternalId(NEW_EVENT_EXTERNAL_ID);
        assertThat(newMatch).isPresent();
        assertThat(newMatch.get().getHomeTeam()).isEqualTo("Manchester United");
        assertThat(newMatch.get().getAwayTeam()).isEqualTo("Liverpool");
        assertThat(matchRepository.findAll()).hasSize(2); // exactly these two, no stray duplicates

        // --- Act 3: OddsPapi ingests again (its own later poll) - proves "latest wins" really
        // tracks the timestamp, not the source: now the newer row is ODDSPAPI again.
        ingestionService.runIngestion(TriggeredBy.MANUAL);

        Odds latestPinnacleHomeAfterSecondOddsPapiRun = latestPinnacleHome(oddsPapiMatch.getId());
        assertThat(latestPinnacleHomeAfterSecondOddsPapiRun.getDataSource()).isEqualTo(DataSource.ODDSPAPI);
    }

    private Odds latestPinnacleHome(Long matchId) {
        return oddsRepository.findLatestOddsByMatch(matchId).stream()
                .filter(o -> o.getBookmaker().getExternalKey().equals("pinnacle"))
                .filter(o -> o.getSelection().equals("home"))
                .findFirst()
                .orElseThrow();
    }

    private static OddsPapiFixtureDto oddsPapiFixture() {
        // One OddsPapiOutcomeDto per selection, keyed by OddsPapi's own outcome id (101/102/103) -
        // IngestionService.OUTCOME_KEY_TO_SELECTION reads the selection from this outer map key,
        // not from OddsPapiPlayerPriceDto.bookmakerOutcomeId (that field is bookmaker-internal and
        // only ever used as-is for Odds.selection on the OLD, pre-fix code path). bookmakerOutcomeId
        // is set to a realistic opaque value here precisely to make clear it's NOT what selection
        // comes from.
        OddsPapiOutcomeDto homeOutcome = new OddsPapiOutcomeDto(
                Map.of("p1", new OddsPapiPlayerPriceDto(true, "48601", new BigDecimal("1.80"))));
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
        TheOddsApiBookmakerDto pinnacle = new TheOddsApiBookmakerDto("pinnacle", "Pinnacle", commenceTime, List.of(h2h));
        return new TheOddsApiEventDto(
                id, PREMIER_LEAGUE_THEODDSAPI_SPORT_KEY, "EPL", commenceTime, homeTeam, awayTeam, List.of(pinnacle));
    }
}
