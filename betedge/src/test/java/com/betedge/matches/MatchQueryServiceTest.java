package com.betedge.matches;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
 * MatchStatusUpdateServiceTest - see its class comment. Covers findAll's finishedWithinDays/
 * finishedToday age filters (must only ever hide FINISHED matches, never SCHEDULED/LIVE ones
 * regardless of age) and its status-priority ordering.
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

        List<Long> defaultIds = idsOf(matchQueryService.findAll(null, null, null, null));

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
        assertThat(idsOf(matchQueryService.findAll(null, null, null, null))).doesNotContain(finished10DaysAgo.getId());
        // ...but visible once the caller asks for a 30-day window.
        assertThat(idsOf(matchQueryService.findAll(null, null, 30, null))).contains(finished10DaysAgo.getId());
    }

    @Test
    @Transactional
    void nonPositiveFinishedWithinDaysShowsFullFinishedHistory() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        Instant now = Instant.now();

        Match veryOldFinished = seedMatch(competition, "very-old-finished", now.minus(400, ChronoUnit.DAYS), MatchStatus.FINISHED);

        assertThat(idsOf(matchQueryService.findAll(null, null, 0, null))).contains(veryOldFinished.getId());
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

        List<Long> ids = idsOf(matchQueryService.findAll(premierLeague.getId(), "arsenal", null, null));

        assertThat(ids).contains(recentPL.getId());
        assertThat(ids).doesNotContain(oldPL.getId()); // excluded by age
        assertThat(ids).doesNotContain(recentLaLiga.getId()); // excluded by competition
    }

    @Test
    @Transactional
    void finishedTodayUsesAmericaBogotaCalendarDayNotServerDefaultZone() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        ZoneId bogota = ZoneId.of("America/Bogota");
        Instant bogotaMidnight = LocalDate.now(bogota).atStartOfDay(bogota).toInstant();

        // Bogota is UTC-5 year-round (no DST) - if the implementation used UTC, or any zone with a
        // different offset, or the JVM/server default zone instead of America/Bogota explicitly,
        // this boundary would be off by hours and at least one of the two assertions below would flip.
        Match justBeforeBogotaMidnight =
                seedMatch(competition, "just-before-bogota-midnight", bogotaMidnight.minusSeconds(1), MatchStatus.FINISHED);
        Match justAfterBogotaMidnight =
                seedMatch(competition, "just-after-bogota-midnight", bogotaMidnight.plusSeconds(1), MatchStatus.FINISHED);

        List<Long> ids = idsOf(matchQueryService.findAll(null, null, null, true));

        assertThat(ids).doesNotContain(justBeforeBogotaMidnight.getId()); // yesterday in Bogota
        assertThat(ids).contains(justAfterBogotaMidnight.getId()); // today in Bogota
    }

    @Test
    @Transactional
    void finishedTodayIsARollingWindowNotTheLast24Hours() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        ZoneId bogota = ZoneId.of("America/Bogota");
        Instant bogotaMidnight = LocalDate.now(bogota).atStartOfDay(bogota).toInstant();

        // Finished earlier today (after today's Bogota midnight) but possibly more than 24h before
        // "now" if the test happens to run late in the day - finishedToday must still include it,
        // proving it's a calendar-day cutoff, not a sliding "last 24 hours" window.
        Match earlyToday = seedMatch(competition, "early-today", bogotaMidnight.plusSeconds(1), MatchStatus.FINISHED);

        assertThat(idsOf(matchQueryService.findAll(null, null, null, true))).contains(earlyToday.getId());
    }

    @Test
    @Transactional
    void finishedTodayTakesPriorityOverFinishedWithinDaysWhenBothAreSent() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        Instant now = Instant.now();

        // Within the last 30 days but not today - would show under finishedWithinDays=30 alone,
        // must still be hidden once finishedToday=true is also present.
        Match finished5DaysAgo = seedMatch(competition, "finished-5d", now.minus(5, ChronoUnit.DAYS), MatchStatus.FINISHED);

        assertThat(idsOf(matchQueryService.findAll(null, null, 30, true))).doesNotContain(finished5DaysAgo.getId());
    }

    @Test
    @Transactional
    void ordersByStatusPriorityThenPerGroupDirectionRegardlessOfFinishedFilter() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow();
        Instant now = Instant.now();

        // FINISHED must come out DESC (most recently finished first).
        Match finishedOlder = seedMatch(competition, "finished-older", now.minus(3, ChronoUnit.DAYS), MatchStatus.FINISHED);
        Match finishedNewer = seedMatch(competition, "finished-newer", now.minus(1, ChronoUnit.DAYS), MatchStatus.FINISHED);

        // LIVE must come out ASC (been playing longest first = oldest startTime first).
        Match liveNewer = seedMatch(competition, "live-newer", now.minus(10, ChronoUnit.MINUTES), MatchStatus.LIVE);
        Match liveOlder = seedMatch(competition, "live-older", now.minus(40, ChronoUnit.MINUTES), MatchStatus.LIVE);

        // SCHEDULED must come out ASC (soonest kickoff first).
        Match scheduledLater = seedMatch(competition, "scheduled-later", now.plus(3, ChronoUnit.DAYS), MatchStatus.SCHEDULED);
        Match scheduledSooner = seedMatch(competition, "scheduled-sooner", now.plus(1, ChronoUnit.DAYS), MatchStatus.SCHEDULED);

        // "Todos" (0) so the full FINISHED history is visible too - priority ordering must still
        // hold, not just when the FINISHED group is narrowed down to a handful of recent rows.
        List<Long> ids = idsOf(matchQueryService.findAll(null, null, 0, null));

        assertThat(ids).containsExactly(
                finishedNewer.getId(), finishedOlder.getId(),
                liveOlder.getId(), liveNewer.getId(),
                scheduledSooner.getId(), scheduledLater.getId());
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
