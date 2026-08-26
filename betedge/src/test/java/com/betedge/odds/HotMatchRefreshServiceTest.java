package com.betedge.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betedge.matches.Competition;
import com.betedge.matches.CompetitionRepository;
import com.betedge.matches.Match;
import com.betedge.matches.MatchRepository;
import com.betedge.matches.MatchStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * Full-stack verification against a real (embedded, throwaway) Postgres - only TheOddsApiClient
 * is mocked, everything else (the hot-window query, TheOddsApiIngestionService.refreshCompetition,
 * the monthly budget count) runs for real. hot-refresh.monthly-budget is overridden to a small
 * value so the budget-cap case doesn't need 100 rows seeded. Never touches the developer's own
 * Postgres: a fresh instance is started for this test class and torn down in {@link #stopEmbeddedPostgres()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HotMatchRefreshServiceTest {

    private static final int TEST_MONTHLY_BUDGET = 3;

    private static EmbeddedPostgres embeddedPostgres;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) throws IOException {
        embeddedPostgres = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("hot-refresh.monthly-budget", () -> TEST_MONTHLY_BUDGET);
    }

    @AfterAll
    static void stopEmbeddedPostgres() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @MockitoBean
    private TheOddsApiClient theOddsApiClient;

    @Autowired
    private HotMatchRefreshService hotMatchRefreshService;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private IngestionRunRepository ingestionRunRepository;

    @Test
    @Transactional
    void triggersAllHotCompetitionsSkipsOutOfWindowThenStopsAtMonthlyBudget() {
        when(theOddsApiClient.lastKnownRequestsRemaining()).thenReturn(Optional.of(500));
        when(theOddsApiClient.fetchOdds(anyString())).thenReturn(List.of());
        when(theOddsApiClient.fetchOdds("soccer_epl")).thenReturn(List.of(fakeEvent("epl-evt-1")));
        when(theOddsApiClient.fetchOdds("soccer_spain_la_liga")).thenReturn(List.of(fakeEvent("laliga-evt-1")));

        Competition premierLeague = competitionRepository.findByExternalKeyTheOddsApi("soccer_epl").orElseThrow();
        Competition laLiga = competitionRepository.findByExternalKeyTheOddsApi("soccer_spain_la_liga").orElseThrow();
        Competition serieA = competitionRepository.findByExternalKeyTheOddsApi("soccer_italy_serie_a").orElseThrow();

        Instant now = Instant.now();
        seedMatch(premierLeague, "pl-fixture", now.plus(30, ChronoUnit.MINUTES)); // in the 60-min window
        seedMatch(laLiga, "laliga-fixture", now.plus(45, ChronoUnit.MINUTES));    // in the 60-min window
        seedMatch(serieA, "seriea-fixture", now.plus(90, ChronoUnit.MINUTES));    // outside it

        // --- both in-window competitions refresh; the out-of-window one is left alone ---
        hotMatchRefreshService.refreshHotMatches();

        List<IngestionRun> runsAfterFirstCheck = ingestionRunRepository.findAll();
        assertThat(runsAfterFirstCheck).hasSize(2);
        assertThat(runsAfterFirstCheck).allMatch(
                r -> r.getTriggeredBy() == TriggeredBy.HOT_REFRESH && r.getProvider() == DataSource.THEODDSAPI);
        assertThat(runsAfterFirstCheck).extracting(r -> r.getCompetitionBreakdown().get(0).competitionName())
                .containsExactlyInAnyOrder("Premier League", "La Liga");
        verify(theOddsApiClient, never()).fetchOdds("soccer_italy_serie_a");

        // --- monthly budget (overridden above to 3) reached blocks every further refresh ---
        // One more HOT_REFRESH run, inserted directly (simulating budget already spent elsewhere
        // this month), brings the count to exactly the cap.
        ingestionRunRepository.save(hotRefreshRunWithNoActivity());
        assertThat(ingestionRunRepository.count()).isEqualTo(TEST_MONTHLY_BUDGET);

        // Serie A's match is now in-window too, so all three competitions are "hot" this check.
        Match serieAMatch = matchRepository.findByExternalId("seriea-fixture").orElseThrow();
        serieAMatch.setStartTime(now.plus(20, ChronoUnit.MINUTES));
        matchRepository.save(serieAMatch);

        hotMatchRefreshService.refreshHotMatches();

        // No new runs at all - budget was already at the cap before this check even started.
        assertThat(ingestionRunRepository.count()).isEqualTo(TEST_MONTHLY_BUDGET);
        verify(theOddsApiClient, never()).fetchOdds("soccer_italy_serie_a");
    }

    @Test
    @Transactional
    void doesNotTriggerWhenNoKnownCreditRemains() {
        when(theOddsApiClient.lastKnownRequestsRemaining()).thenReturn(Optional.of(0));

        Competition premierLeague = competitionRepository.findByExternalKeyTheOddsApi("soccer_epl").orElseThrow();
        seedMatch(premierLeague, "pl-fixture-no-credit", Instant.now().plus(10, ChronoUnit.MINUTES));

        hotMatchRefreshService.refreshHotMatches();

        assertThat(ingestionRunRepository.count()).isZero();
        verify(theOddsApiClient, never()).fetchOdds(anyString());
    }

    private Match seedMatch(Competition competition, String externalId, Instant startTime) {
        Match match = new Match();
        match.setCompetition(competition);
        match.setExternalId(externalId);
        match.setHomeTeam("Home " + externalId);
        match.setAwayTeam("Away " + externalId);
        match.setStartTime(startTime);
        match.setStatus(MatchStatus.SCHEDULED);
        return matchRepository.save(match);
    }

    private static IngestionRun hotRefreshRunWithNoActivity() {
        IngestionRun run = new IngestionRun();
        run.setProvider(DataSource.THEODDSAPI);
        run.setTriggeredBy(TriggeredBy.HOT_REFRESH);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(Instant.now());
        run.setTotalEventsReceived(0);
        run.setTotalNewMatches(0);
        run.setTotalNewOdds(0);
        run.setValueBetsDetected(0);
        run.setSurebetsDetected(0);
        run.setCompetitionBreakdown(List.of());
        return run;
    }

    private static TheOddsApiEventDto fakeEvent(String id) {
        List<TheOddsApiOutcomeDto> outcomes = List.of(
                new TheOddsApiOutcomeDto("Home Team", new BigDecimal("1.90")),
                new TheOddsApiOutcomeDto("Away Team", new BigDecimal("3.80")),
                new TheOddsApiOutcomeDto("Draw", new BigDecimal("3.40")));
        TheOddsApiMarketDto h2h = new TheOddsApiMarketDto("h2h", Instant.now(), outcomes);
        TheOddsApiBookmakerDto pinnacle = new TheOddsApiBookmakerDto("pinnacle", "Pinnacle", Instant.now(), List.of(h2h));
        return new TheOddsApiEventDto(
                id, "sport", "Sport", Instant.now().plus(1, ChronoUnit.HOURS), "Home Team", "Away Team", List.of(pinnacle));
    }
}
