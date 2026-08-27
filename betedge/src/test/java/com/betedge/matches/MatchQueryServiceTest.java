package com.betedge.matches;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-stack verification against a real (embedded, throwaway) Postgres, same setup as
 * MatchStatusUpdateServiceTest - see its class comment. Covers findAll's finishedWithinDays age
 * filter: it must only ever hide FINISHED matches, never SCHEDULED/LIVE ones regardless of age.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MatchQueryServiceTest {

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

    @Autowired
    private MatchQueryService matchQueryService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Test
    @Transactional
    void defaultCutoffHidesFinishedMatchesOlderThanSevenDaysButNeverScheduledOrLive() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow(); // Premier League
        Instant now = Instant.now();

        Match recentFinished = seedMatch(competition, "recent-finished", now.minus(3, ChronoUnit.DAYS), MatchStatus.FINISHED);
        Match oldFinished = seedMatch(competition, "old-finished", now.minus(16, ChronoUnit.DAYS), MatchStatus.FINISHED);
        Match oldButScheduled = seedMatch(competition, "old-scheduled", now.minus(30, ChronoUnit.DAYS), MatchStatus.SCHEDULED);
        Match oldButLive = seedMatch(competition, "old-live", now.minus(30, ChronoUnit.DAYS), MatchStatus.LIVE);

        List<Long> defaultIds = idsOf(matchQueryService.findAll(null, null, null));

        assertThat(defaultIds).contains(recentFinished.getId(), oldButScheduled.getId(), oldButLive.getId());
        assertThat(defaultIds).doesNotContain(oldFinished.getId());
    }

    @Test
    @Transactional
    void explicitFinishedWithinDaysNarrowsTheCutoff() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        Instant now = Instant.now();

        Match finished10DaysAgo = seedMatch(competition, "finished-10d", now.minus(10, ChronoUnit.DAYS), MatchStatus.FINISHED);

        // Hidden with the default (7 days)...
        assertThat(idsOf(matchQueryService.findAll(null, null, null))).doesNotContain(finished10DaysAgo.getId());
        // ...but visible once the caller asks for a 30-day window.
        assertThat(idsOf(matchQueryService.findAll(null, null, 30))).contains(finished10DaysAgo.getId());
    }

    @Test
    @Transactional
    void nonPositiveFinishedWithinDaysShowsFullFinishedHistory() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        Instant now = Instant.now();

        Match veryOldFinished = seedMatch(competition, "very-old-finished", now.minus(400, ChronoUnit.DAYS), MatchStatus.FINISHED);

        assertThat(idsOf(matchQueryService.findAll(null, null, 0))).contains(veryOldFinished.getId());
    }

    @Test
    @Transactional
    void finishedWithinDaysComposesWithCompetitionAndSearchFilters() {
        Competition premierLeague = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        Competition laLiga = competitionRepository.findByExternalKeyOddsPapi("8").orElseThrow(); // La Liga
        Instant now = Instant.now();

        Match recentPL = seedMatch(premierLeague, "pl-recent", now.minus(1, ChronoUnit.DAYS), MatchStatus.FINISHED);
        recentPL.setHomeTeam("Arsenal");
        matchRepository.save(recentPL);
        Match oldPL = seedMatch(premierLeague, "pl-old", now.minus(20, ChronoUnit.DAYS), MatchStatus.FINISHED);
        oldPL.setHomeTeam("Arsenal");
        matchRepository.save(oldPL);
        Match recentLaLiga = seedMatch(laLiga, "laliga-recent", now.minus(1, ChronoUnit.DAYS), MatchStatus.FINISHED);
        recentLaLiga.setHomeTeam("Arsenal");
        matchRepository.save(recentLaLiga);

        List<Long> ids = idsOf(matchQueryService.findAll(premierLeague.getId(), "arsenal", null));

        assertThat(ids).contains(recentPL.getId());
        assertThat(ids).doesNotContain(oldPL.getId()); // excluded by age
        assertThat(ids).doesNotContain(recentLaLiga.getId()); // excluded by competition
    }

    private static List<Long> idsOf(List<MatchResponse> responses) {
        return responses.stream().map(MatchResponse::id).toList();
    }

    private Match seedMatch(Competition competition, String externalId, Instant startTime, MatchStatus status) {
        Match match = new Match();
        match.setCompetition(competition);
        match.setExternalId(externalId);
        match.setHomeTeam("Home " + externalId);
        match.setAwayTeam("Away " + externalId);
        match.setStartTime(startTime);
        match.setStatus(status);
        return matchRepository.save(match);
    }
}
