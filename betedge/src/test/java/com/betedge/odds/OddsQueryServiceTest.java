package com.betedge.odds;

import static org.assertj.core.api.Assertions.assertThat;

import com.betedge.matches.Competition;
import com.betedge.matches.CompetitionRepository;
import com.betedge.matches.Match;
import com.betedge.matches.MatchRepository;
import com.betedge.matches.MatchStatus;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
 * MatchQueryServiceTest - see its class comment. Covers /odds/history's row cap
 * (OddsQueryService.HISTORY_ROW_LIMIT, added 2026-09-02 after confirming the query and the chart
 * components had no limit at all): a match with more rows than the cap must get the MOST RECENT
 * ones, never the oldest, and the response must say so via truncated=true - never a
 * silently-incomplete chart.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OddsQueryServiceTest {

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
    private OddsQueryService oddsQueryService;

    @Autowired
    private OddsRepository oddsRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private CompetitionRepository competitionRepository;

    @Autowired
    private BookmakerRepository bookmakerRepository;

    @Test
    @Transactional
    void withinTheLimitReturnsEveryRowAndNoTruncationFlag() {
        Match match = seedMatch("under-limit");
        Bookmaker pinnacle = bookmakerRepository.findByExternalKey("pinnacle").orElseThrow();
        Instant base = Instant.now().minusSeconds(10_000);
        for (int i = 0; i < 3; i++) {
            oddsRepository.save(oddsRow(match, pinnacle, base.plusSeconds(i), i));
        }

        OddsHistoryResponse response = oddsQueryService.findHistoryByMatch(match.getId());

        assertThat(response.truncated()).isFalse();
        assertThat(response.entries()).hasSize(3);
    }

    @Test
    @Transactional
    void beyondTheLimitReturnsOnlyTheMostRecentRowsAndFlagsTruncation() {
        Match match = seedMatch("over-limit");
        Bookmaker pinnacle = bookmakerRepository.findByExternalKey("pinnacle").orElseThrow();

        // 50 more than HISTORY_ROW_LIMIT so the oldest 50 must be the ones dropped - strictly
        // increasing timestamps (one per second) make "which rows survived" unambiguous to assert.
        int totalRows = OddsQueryService.HISTORY_ROW_LIMIT + 50;
        Instant base = Instant.now().minusSeconds(totalRows + 10_000L);
        List<Odds> rows = new ArrayList<>(totalRows);
        for (int i = 0; i < totalRows; i++) {
            rows.add(oddsRow(match, pinnacle, base.plusSeconds(i), i));
        }
        oddsRepository.saveAll(rows);

        assertThat(oddsRepository.countByMatchId(match.getId())).isEqualTo(totalRows);

        OddsHistoryResponse response = oddsQueryService.findHistoryByMatch(match.getId());

        assertThat(response.truncated())
                .as("real row count (%d) exceeds HISTORY_ROW_LIMIT (%d)", totalRows, OddsQueryService.HISTORY_ROW_LIMIT)
                .isTrue();
        assertThat(response.entries()).hasSize(OddsQueryService.HISTORY_ROW_LIMIT);

        // Entries come back oldest-first (chart order) - see OddsQueryService.findHistoryByMatch's
        // own re-sort. The oldest entry kept must be row #50 (the 51st inserted, 0-indexed) - rows
        // #0-49, the actual oldest 50, must have been the ones dropped, not the newest 50.
        assertThat(response.entries().get(0).timestamp()).isEqualTo(base.plusSeconds(50));
        assertThat(response.entries().get(response.entries().size() - 1).timestamp())
                .isEqualTo(base.plusSeconds(totalRows - 1));
        assertThat(response.entries()).isSortedAccordingTo(java.util.Comparator.comparing(OddsHistoryEntryDto::timestamp));
    }

    private Match seedMatch(String externalId) {
        Competition competition = competitionRepository.findByExternalKeyOddsPapi("17").orElseThrow(); // Premier League
        Match match = new Match();
        match.setCompetition(competition);
        match.setExternalId(externalId);
        match.setHomeTeam("Home " + externalId);
        match.setAwayTeam("Away " + externalId);
        match.setStartTime(Instant.now());
        match.setStatus(MatchStatus.SCHEDULED);
        return matchRepository.save(match);
    }

    private static Odds oddsRow(Match match, Bookmaker bookmaker, Instant timestamp, int index) {
        Odds odds = new Odds();
        odds.setMatch(match);
        odds.setBookmaker(bookmaker);
        odds.setMarketType("moneyline");
        odds.setSelection("home");
        odds.setOddValue(BigDecimal.valueOf(1.50 + (index % 100) * 0.001));
        odds.setDataSource(DataSource.ODDSPAPI);
        odds.setTimestamp(timestamp);
        return odds;
    }
}
