package com.betedge.odds;

import com.betedge.matches.Match;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
 * every single cycle. The fix: scope "last known price" to the SAME source as the new reading.
 *
 * <p><b>Batched lookups (2026-09-02).</b> Originally one DB round-trip per (bookmaker, selection,
 * price) checked - measured live against real triggers on 2026-09-02: 864 calls / 1183ms for a
 * 3-bookmaker OddsPapi run, 4788 calls / 9894ms for a 21-bookmaker The Odds API run (there, ~49% of
 * the run's entire wall-clock time, the single biggest line item in the whole ingestion cycle -
 * bigger than the HTTP fetch itself). {@link #loadLastKnownPrices} now fetches every (bookmaker,
 * selection) latest price for a match+dataSource in ONE query (see
 * {@link OddsRepository#findLatestOddsByMatchAndDataSource}), and {@link #isUnchanged} compares
 * against that pre-fetched snapshot in memory instead of hitting Postgres per price. The
 * "changed/unchanged" criterion itself - same-source comparison, {@link BigDecimal#compareTo} not
 * {@code equals()} - is unchanged; only the number of round-trips is (one per match instead of one
 * per price).
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
     * Fetches the latest known price per (bookmaker, selection) for this match, scoped to
     * dataSource - one query, meant to be called ONCE per match (right after it's resolved, before
     * iterating its bookmakers/selections/prices) and reused across every {@link #isUnchanged}
     * check for that match. {@code dataSource} scoping is on purpose - see this class's own Javadoc
     * for the incident that made it necessary; a caller must always pass the source IT is currently
     * ingesting from, never infer it from the database.
     */
    public Map<String, BigDecimal> loadLastKnownPrices(Match match, DataSource dataSource) {
        List<Odds> latest = oddsRepository.findLatestOddsByMatchAndDataSource(match.getId(), dataSource.name());
        return latest.stream()
                .collect(Collectors.toMap(
                        odds -> priceKey(odds.getBookmaker().getId(), odds.getSelection()),
                        Odds::getOddValue));
    }

    /**
     * True when {@code lastKnownPrices} (from {@link #loadLastKnownPrices}, already scoped to this
     * match's own dataSource) already has this exact price for this (bookmaker, selection) pair -
     * compared via {@link BigDecimal#compareTo}, not {@code equals()}, since a source can hand back
     * the same real price at a different scale (e.g. a fresh "1.25" against an already-stored
     * "1.2500") and those must still count as unchanged, not a false price move. False when there's
     * no entry for this pair at all (first time this exact quartet is ever seen for this match) or
     * the price genuinely changed - both cases mean the caller should insert the new row.
     */
    public boolean isUnchanged(Map<String, BigDecimal> lastKnownPrices, Bookmaker bookmaker, String selection, BigDecimal newValue) {
        BigDecimal previous = lastKnownPrices.get(priceKey(bookmaker.getId(), selection));
        return previous != null && previous.compareTo(newValue) == 0;
    }

    private static String priceKey(Long bookmakerId, String selection) {
        return bookmakerId + ":" + selection;
    }
}
