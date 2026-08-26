package com.betedge.matches;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Full-stack verification against a real (embedded, throwaway) Postgres - no HTTP clients
 * involved at all, this is pure DB read/update. Uses the default match-status-update.duration-hours
 * (2h) rather than overriding it, so the test data below is deliberately chosen relative to that
 * value. Never touches the developer's own Postgres: a fresh instance is started for this test
 * class and torn down in {@link #stopEmbeddedPostgres()}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MatchStatusUpdateServiceTest {

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
    private MatchStatusUpdateService matchStatusUpdateService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Test
    @Transactional
    void transitionsMatchesAccordingToElapsedTimeAgainstTheTwoHourDefaultDuration() {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow(); // Premier League
        Instant now = Instant.now();

        Match justKickedOff = seedMatch(competition, "live-candidate", now.minus(10, ChronoUnit.MINUTES), MatchStatus.SCHEDULED);
        Match longRunningLive = seedMatch(competition, "finish-candidate", now.minus(3, ChronoUnit.HOURS), MatchStatus.LIVE);
        // Started 5h ago (duration 2h) while still SCHEDULED - simulates a missed update cycle;
        // must jump straight to FINISHED, never transiently through LIVE.
        Match staleScheduled = seedMatch(competition, "stale-candidate", now.minus(5, ChronoUnit.HOURS), MatchStatus.SCHEDULED);
        Match future = seedMatch(competition, "future-candidate", now.plus(2, ChronoUnit.HOURS), MatchStatus.SCHEDULED);

        matchStatusUpdateService.updateStatuses();

        assertThat(statusOf(justKickedOff)).isEqualTo(MatchStatus.LIVE);
        assertThat(statusOf(longRunningLive)).isEqualTo(MatchStatus.FINISHED);
        assertThat(statusOf(staleScheduled)).isEqualTo(MatchStatus.FINISHED);
        assertThat(statusOf(future)).isEqualTo(MatchStatus.SCHEDULED);
    }

    private MatchStatus statusOf(Match match) {
        return matchRepository.findById(match.getId()).orElseThrow().getStatus();
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
