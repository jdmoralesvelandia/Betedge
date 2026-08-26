package com.betedge.valuebets;

import com.betedge.matches.Match;
import com.betedge.matches.MatchStatus;
import com.betedge.odds.Odds;
import com.betedge.odds.OddsRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * For each eligible match: takes the latest odds snapshot per bookmaker,
 * removes each bookmaker's own margin (normalizes its implied
 * probabilities to sum to 1), then averages the normalized probabilities
 * across bookmakers to get a market-consensus "true" probability per
 * selection. A bookmaker offering an odd better than that consensus
 * warrants by more than {@link #EDGE_THRESHOLD_PERCENT} is recorded as a
 * value bet.
 */
@Service
@RequiredArgsConstructor
public class ValueBetCalculationService {

    private static final int MIN_BOOKMAKERS = 3;
    private static final BigDecimal EDGE_THRESHOLD_PERCENT = new BigDecimal("3.0");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final int STORAGE_SCALE = 4;

    /**
     * Below this, a new detection is treated as "nothing really changed" and isn't stored - manual
     * triggers repeated while testing (before real production traffic) would otherwise fill the
     * history with redundant rows carrying the same edge to within rounding noise, with no new
     * information in any of them. The real first detection of a given edge is always kept; only
     * repeats of an unchanged value are skipped.
     */
    private static final BigDecimal DEDUP_THRESHOLD_PERCENT = new BigDecimal("0.01");

    private final OddsRepository oddsRepository;
    private final ValueBetRepository valueBetRepository;

    /**
     * Transactional so the Hibernate session stays open long enough to resolve each
     * {@code Odds.bookmaker} lazy proxy (open-in-view is disabled project-wide).
     */
    @Transactional
    public int calculateForMatches(List<Match> matches) {
        int created = 0;
        for (Match match : matches) {
            if (isEligible(match)) {
                created += calculateForMatch(match);
            }
        }
        return created;
    }

    /**
     * SCHEDULED only - this project detects pre-match value, not live/in-play trading. LIVE was
     * included here until a real incident showed why that's wrong: once a match kicks off, "h2h"
     * stops meaning the same thing across providers - The Odds API reprices it live (reacting to
     * the actual score), while OddsPapi/others may simply stop updating a fixture once it starts,
     * leaving a stale pre-match snapshot in place. Comparing those two under one "consensus" isn't
     * a real market disagreement, it's comparing two different kinds of number - see the
     * Clube do Remo vs Atletico Mineiro case (241% "edge") this rule exists to prevent.
     */
    private static boolean isEligible(Match match) {
        return match.getStatus() == MatchStatus.SCHEDULED;
    }

    private int calculateForMatch(Match match) {
        List<Odds> latestOdds = oddsRepository.findLatestOddsByMatch(match.getId());
        if (latestOdds.isEmpty()) {
            return 0;
        }

        Map<Long, List<Odds>> byBookmaker = latestOdds.stream()
                .collect(Collectors.groupingBy(o -> o.getBookmaker().getId(), LinkedHashMap::new, Collectors.toList()));
        if (byBookmaker.size() < MIN_BOOKMAKERS) {
            return 0;
        }

        // normalized[bookmakerSlug][selection] = that bookmaker's own implied probability
        // with its margin removed (divided by the sum of its raw implied probabilities).
        Map<String, Map<String, BigDecimal>> normalizedByBookmaker = new LinkedHashMap<>();
        for (List<Odds> bookmakerOdds : byBookmaker.values()) {
            String bookmakerSlug = bookmakerOdds.get(0).getBookmaker().getExternalKey();

            Map<String, BigDecimal> rawImplied = new LinkedHashMap<>();
            BigDecimal sum = BigDecimal.ZERO;
            for (Odds o : bookmakerOdds) {
                BigDecimal implied = BigDecimal.ONE.divide(o.getOddValue(), MC);
                rawImplied.put(o.getSelection(), implied);
                sum = sum.add(implied);
            }

            Map<String, BigDecimal> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, BigDecimal> entry : rawImplied.entrySet()) {
                normalized.put(entry.getKey(), entry.getValue().divide(sum, MC));
            }
            normalizedByBookmaker.put(bookmakerSlug, normalized);
        }

        Map<String, BigDecimal> consensusBySelection = computeConsensus(normalizedByBookmaker);

        int created = 0;
        for (Odds o : latestOdds) {
            String selection = o.getSelection();
            BigDecimal trueProbability = consensusBySelection.get(selection);
            if (trueProbability == null) {
                continue;
            }

            BigDecimal impliedProbability = BigDecimal.ONE.divide(o.getOddValue(), MC);
            BigDecimal edgePercentage = trueProbability.multiply(o.getOddValue(), MC)
                    .subtract(BigDecimal.ONE)
                    .multiply(ONE_HUNDRED, MC);

            if (edgePercentage.compareTo(EDGE_THRESHOLD_PERCENT) < 0) {
                continue;
            }

            if (isUnchangedSinceLastDetection(match.getId(), o.getBookmaker().getId(), selection, edgePercentage)) {
                continue;
            }

            ValueBet valueBet = new ValueBet();
            valueBet.setMatch(match);
            valueBet.setBookmaker(o.getBookmaker());
            valueBet.setSelection(selection);
            valueBet.setImpliedProbability(impliedProbability.setScale(STORAGE_SCALE, RoundingMode.HALF_UP));
            valueBet.setEstimatedTrueProbability(trueProbability.setScale(STORAGE_SCALE, RoundingMode.HALF_UP));
            valueBet.setEdgePercentage(edgePercentage.setScale(STORAGE_SCALE, RoundingMode.HALF_UP));
            valueBet.setBookmakerProbabilities(bookmakerProbabilitiesFor(normalizedByBookmaker, selection));
            valueBetRepository.save(valueBet);
            created++;
        }
        return created;
    }

    private boolean isUnchangedSinceLastDetection(Long matchId, Long bookmakerId, String selection, BigDecimal edgePercentage) {
        return valueBetRepository.findTopByMatchIdAndBookmakerIdAndSelectionOrderByDetectedAtDesc(matchId, bookmakerId, selection)
                .map(previous -> edgePercentage.subtract(previous.getEdgePercentage()).abs().compareTo(DEDUP_THRESHOLD_PERCENT) < 0)
                .orElse(false);
    }

    private static Map<String, BigDecimal> computeConsensus(Map<String, Map<String, BigDecimal>> normalizedByBookmaker) {
        Set<String> selections = normalizedByBookmaker.values().stream()
                .flatMap(m -> m.keySet().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, BigDecimal> consensus = new LinkedHashMap<>();
        for (String selection : selections) {
            List<BigDecimal> probabilities = normalizedByBookmaker.values().stream()
                    .map(m -> m.get(selection))
                    .filter(Objects::nonNull)
                    .toList();
            BigDecimal sum = probabilities.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            consensus.put(selection, sum.divide(BigDecimal.valueOf(probabilities.size()), MC));
        }
        return consensus;
    }

    private static Map<String, BigDecimal> bookmakerProbabilitiesFor(
            Map<String, Map<String, BigDecimal>> normalizedByBookmaker, String selection) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, BigDecimal>> entry : normalizedByBookmaker.entrySet()) {
            BigDecimal probability = entry.getValue().get(selection);
            if (probability != null) {
                result.put(entry.getKey(), probability.setScale(STORAGE_SCALE, RoundingMode.HALF_UP));
            }
        }
        return result;
    }
}
