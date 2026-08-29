package com.betedge.odds;

import com.betedge.matches.Match;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Odds is append-only (see its own {@code @Immutable}) and, without this check, grows every single
 * ingestion cycle regardless of whether a bookmaker's price actually moved since the last one -
 * most cycles, most (match, bookmaker, selection, dataSource) quartets report the exact same price
 * as before. This is the one check standing between "every real price change" and "every poll,
 * verbatim, forever" - skipping the insert here fixes two things flagged before launch at once: the
 * table's otherwise-unbounded growth, and OddsHistoryChart's per-bookmaker series getting crowded
 * with runs of visually-identical points for a price that never actually changed.
 *
 * <p><b>Incident (2026-08-28): comparing across sources instead of within one.</b> pinnacle is fed
 * by both {@link IngestionService} (OddsPapi) and {@link TheOddsApiIngestionService} (The Odds
 * API) under the same {@code bookmaker_id} - two independent feeds, each reporting its own price for
 * the same real market. The first version of this check compared a new price against the single
 * most recent row for the (match, bookmaker, selection) triad regardless of which source wrote it.
 * With two sources interleaving roughly one cycle apart, that meant: OddsPapi reports 1.552 (row A),
 * The Odds API reports its own stable-but-different 1.550 (row B) next, then OddsPapi reports 1.552
 * again - row A's own price never moved, but because row B (a DIFFERENT source, DIFFERENT price)
 * was the most recent row overall, the comparison saw "1.552 != 1.550" and inserted a new row
 * anyway. Repeated every cycle, this reproduced the exact "wall of clustered points" bug the
 * deduplication was built to prevent in the first place - confirmed against real data on Liverpool
 * FC vs Nottingham Forest and ACF Fiorentina vs Frosinone Calcio (match ids 113, 119): six and eight
 * rows respectively, alternating ODDSPAPI/THEODDSAPI, each source's own price provably unchanged
 * every single cycle. The fix: scope "last known price" to the SAME source as the new reading, via
 * {@link OddsRepository#findTopByMatchIdAndBookmakerIdAndSelectionAndDataSourceOrderByTimestampDesc}
 * - each feed is now compared only against its own history, never the other feed's.
 *
 * <p>Shared by both {@link IngestionService} and {@link TheOddsApiIngestionService} so the exact
 * same rule applies to both providers, rather than two copies of the same comparison drifting apart
 * - same reasoning as {@link MatchReconciliationService} being extracted rather than duplicated.
 */
@Service
@RequiredArgsConstructor
public class OddsDeduplicationService {

    private final OddsRepository oddsRepository;

    /**
     * True when the last known price from THIS EXACT source for this (match, bookmaker, selection)
     * triad already equals newValue - compared via {@link BigDecimal#compareTo}, not
     * {@code equals()}, since a source can hand back the same real price at a different scale (e.g.
     * a fresh "1.25" against an already-stored "1.2500") and those must still count as unchanged,
     * not a false price move. False when there's no prior row from this source at all (first time
     * this exact quartet is ever seen) or the price genuinely changed for this source - both cases
     * mean the caller should insert the new row.
     *
     * <p>{@code dataSource} is part of the comparison key on purpose - see this class's own Javadoc
     * for the incident that made that necessary. A caller must always pass the source IT is
     * currently ingesting from, never infer it from the database.
     */
    public boolean isUnchanged(Match match, Bookmaker bookmaker, String selection, DataSource dataSource, BigDecimal newValue) {
        return oddsRepository
                .findTopByMatchIdAndBookmakerIdAndSelectionAndDataSourceOrderByTimestampDesc(
                        match.getId(), bookmaker.getId(), selection, dataSource)
                .map(previous -> previous.getOddValue().compareTo(newValue) == 0)
                .orElse(false);
    }
}
