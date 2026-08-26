package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import java.util.Set;

/**
 * OddsPapi reuses the "/moneyline" suffix in {@code bookmakerMarketId} for
 * more than one market: confirmed against GET /v4/markets that marketId
 * 101 ("Full Time Result", period "fulltime") ends in "/0/moneyline" while
 * marketId 10208 ("First Half Result", period "p1") ends in "/1/moneyline".
 * Matching on "/moneyline" alone would silently mix full-match and
 * first-half prices into the same Odds rows, so the period segment must be
 * checked too.
 *
 * The period suffix alone still isn't enough, though: confirmed against a real
 * Brasileirão response that Pinnacle can carry a SECOND market - id 10911, still
 * unidentified beyond "not Full Time Result" - that also ends in "/0/moneyline" and
 * uses the same home/draw/away outcome labels, but with a completely different (often
 * near-mirrored) set of prices for the same real fixture. Real incident this caused:
 * ingesting both under the same selection made ValueBetCalculationService compare two
 * unrelated markets as if they were one, producing edges over 100% for several
 * Brasileirão matches (Palmeiras vs Internacional, Flamengo vs Vitoria, Bahia vs Vasco
 * da Gama - confirmed systematic across the whole league's Pinnacle data, not isolated
 * to one fixture). isFullTimeMoneyline now requires the caller's own map key (the
 * actual numeric market id OddsPapi assigns, only visible at the
 * bookmakerOdds.markets() map level, never inside OddsPapiMarketDto itself) to be
 * exactly "101" - id 10911's rows still parse fine, they're just no longer accepted
 * as the full-time moneyline market.
 *
 * The "/0/moneyline" bookmakerMarketId suffix turned out to be a Pinnacle-specific
 * convention, not universal: confirmed against real betano/betplay responses
 * (2026-08-25, 40 fixtures across all 5 tracked leagues) that both carry a real,
 * active, 3-outcome market 101 with the correct home/draw/away prices (cross-checked
 * against Pinnacle's own price for the same fixtureId - 30/30 pairs same order of
 * magnitude, none inverted), but their own bookmakerMarketId is just a bare
 * bookmaker-internal number (e.g. "2913550627"), never ending in "/0/moneyline" - the
 * suffix check alone would silently drop every one of their odds. Since marketId=="101"
 * on its own isn't safe either (that's exactly what let 10911 through before), the
 * fallback for the non-Pinnacle shape re-adds an equivalent guard: the market's own
 * outcome keys must be exactly {"101","102","103"} - confirmed stable across the same
 * 40-fixture sample (no other market ever carries that same 3-key shape), so a
 * same-numbered phantom market would still need to fake that exact shape too, not just
 * the marketId, to slip through.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OddsPapiMarketDto(
        String bookmakerMarketId,
        boolean marketActive,
        Map<String, OddsPapiOutcomeDto> outcomes) {

    private static final String FULL_TIME_MONEYLINE_SUFFIX = "/0/moneyline";

    /** OddsPapi's confirmed, documented "Full Time Result" market id (GET /v4/markets) - the only one this codebase treats as the real moneyline market. */
    private static final String FULL_TIME_RESULT_MARKET_ID = "101";

    /** The 3 outcome keys OddsPapi documents for market 101 (home/draw/away) - see IngestionService.OUTCOME_KEY_TO_SELECTION. */
    private static final Set<String> FULL_TIME_RESULT_OUTCOME_KEYS = Set.of("101", "102", "103");

    public boolean isFullTimeMoneyline(String marketId) {
        if (!FULL_TIME_RESULT_MARKET_ID.equals(marketId)) {
            return false;
        }
        if (bookmakerMarketId != null && bookmakerMarketId.endsWith(FULL_TIME_MONEYLINE_SUFFIX)) {
            return true; // Pinnacle/bet365/unibet-style bookmakerMarketId
        }
        // betano/betplay-style: no path-suffix bookmakerMarketId, so fall back to shape.
        return outcomes != null && outcomes.keySet().equals(FULL_TIME_RESULT_OUTCOME_KEYS);
    }
}
