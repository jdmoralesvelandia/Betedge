package com.betedge.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.betedge.matches.Match;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the 2026-08-28 incident described in OddsDeduplicationService's own
 * Javadoc: pinnacle is fed by two independent sources (OddsPapi, The Odds API) under one shared
 * bookmaker_id, and comparing a new price against "the last row regardless of source" made each
 * source's own unchanged price look like a change whenever the OTHER source's row landed in
 * between - reproducing the exact clustered-points bug the deduplication exists to prevent.
 */
class OddsDeduplicationServiceTest {

    private static final Match MATCH = match(113L);
    private static final Bookmaker PINNACLE = bookmaker(7L);
    private static final String SELECTION = "home";

    @Test
    void treatsOwnSourcesPriceAsUnchangedEvenWhenTheOtherSourceReportedMoreRecently() {
        // Reproduces Liverpool FC vs Nottingham Forest (match 113) exactly: OddsPapi's own last
        // reading for this triad was 1.552 - unchanged since. In between, The Odds API wrote its
        // own stable-but-different 1.550 for the SAME (match, bookmaker, selection), so it is the
        // most recent row overall - but that's irrelevant to whether OddsPapi's own price moved.
        OddsRepository oddsRepository = mock(OddsRepository.class);
        when(oddsRepository.findTopByMatchIdAndBookmakerIdAndSelectionAndDataSourceOrderByTimestampDesc(
                        MATCH.getId(), PINNACLE.getId(), SELECTION, DataSource.ODDSPAPI))
                .thenReturn(java.util.Optional.of(oddsRow(new BigDecimal("1.5520"), DataSource.ODDSPAPI)));

        OddsDeduplicationService service = new OddsDeduplicationService(oddsRepository);

        boolean unchanged = service.isUnchanged(MATCH, PINNACLE, SELECTION, DataSource.ODDSPAPI, new BigDecimal("1.5520"));

        assertThat(unchanged)
                .as("OddsPapi's own price didn't move - must be treated as unchanged regardless of "
                        + "what The Odds API most recently reported for the same triad")
                .isTrue();
    }

    @Test
    void stillDetectsARealChangeWithinTheSameSource() {
        OddsRepository oddsRepository = mock(OddsRepository.class);
        when(oddsRepository.findTopByMatchIdAndBookmakerIdAndSelectionAndDataSourceOrderByTimestampDesc(
                        MATCH.getId(), PINNACLE.getId(), SELECTION, DataSource.ODDSPAPI))
                .thenReturn(java.util.Optional.of(oddsRow(new BigDecimal("1.5520"), DataSource.ODDSPAPI)));

        OddsDeduplicationService service = new OddsDeduplicationService(oddsRepository);

        boolean unchanged = service.isUnchanged(MATCH, PINNACLE, SELECTION, DataSource.ODDSPAPI, new BigDecimal("1.5400"));

        assertThat(unchanged).isFalse();
    }

    @Test
    void treatsFirstEverReadingFromASourceAsChanged() {
        OddsRepository oddsRepository = mock(OddsRepository.class);
        when(oddsRepository.findTopByMatchIdAndBookmakerIdAndSelectionAndDataSourceOrderByTimestampDesc(
                        MATCH.getId(), PINNACLE.getId(), SELECTION, DataSource.THEODDSAPI))
                .thenReturn(java.util.Optional.empty());

        OddsDeduplicationService service = new OddsDeduplicationService(oddsRepository);

        boolean unchanged = service.isUnchanged(MATCH, PINNACLE, SELECTION, DataSource.THEODDSAPI, new BigDecimal("1.5500"));

        assertThat(unchanged).isFalse();
    }

    private static Odds oddsRow(BigDecimal value, DataSource dataSource) {
        Odds odds = new Odds();
        odds.setMatch(MATCH);
        odds.setBookmaker(PINNACLE);
        odds.setSelection(SELECTION);
        odds.setOddValue(value);
        odds.setDataSource(dataSource);
        odds.setTimestamp(Instant.now());
        return odds;
    }

    private static Match match(Long id) {
        Match match = new Match();
        match.setId(id);
        return match;
    }

    private static Bookmaker bookmaker(Long id) {
        Bookmaker bookmaker = new Bookmaker();
        bookmaker.setId(id);
        return bookmaker;
    }
}
