package com.betedge.valuebets;

import com.betedge.matches.Match;
import com.betedge.matches.MatchStatus;
import com.betedge.odds.Odds;
import com.betedge.odds.OddsRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * For each eligible match: takes the latest odds snapshot per bookmaker and,
 * for every selection, keeps only the single best (highest) odd available
 * across all bookmakers. If those best odds combined imply less than 100%
 * probability, backing all of them simultaneously locks in a guaranteed
 * profit regardless of the result - a surebet.
 */
@Service
@RequiredArgsConstructor
public class SurebetCalculationService {

    private static final int REQUIRED_SELECTIONS = 3;
    private static final BigDecimal PROFIT_THRESHOLD_PERCENT = new BigDecimal("0.5");
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);
    private static final int STORAGE_SCALE = 4;

    /** See the identical constant on ValueBetCalculationService for why this exists. */
    private static final BigDecimal DEDUP_THRESHOLD_PERCENT = new BigDecimal("0.01");

    private final OddsRepository oddsRepository;
    private final SurebetRepository surebetRepository;

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

    /** SCHEDULED only - see the identical comment on ValueBetCalculationService.isEligible for why LIVE was removed. */
    private static boolean isEligible(Match match) {
        return match.getStatus() == MatchStatus.SCHEDULED;
    }

    private int calculateForMatch(Match match) {
        List<Odds> latestOdds = oddsRepository.findLatestOddsByMatch(match.getId());
        if (latestOdds.isEmpty()) {
            return 0;
        }

        Map<String, Odds> bestBySelection = new LinkedHashMap<>();
        for (Odds o : latestOdds) {
            Odds current = bestBySelection.get(o.getSelection());
            if (current == null || o.getOddValue().compareTo(current.getOddValue()) > 0) {
                bestBySelection.put(o.getSelection(), o);
            }
        }

        if (bestBySelection.size() < REQUIRED_SELECTIONS) {
            return 0; // need a best price for every outcome to evaluate the combination
        }

        BigDecimal totalImpliedProbability = BigDecimal.ZERO;
        for (Odds o : bestBySelection.values()) {
            totalImpliedProbability = totalImpliedProbability.add(BigDecimal.ONE.divide(o.getOddValue(), MC));
        }

        if (totalImpliedProbability.compareTo(BigDecimal.ONE) >= 0) {
            return 0; // no arbitrage
        }

        BigDecimal profitPercentage = BigDecimal.ONE.divide(totalImpliedProbability, MC)
                .subtract(BigDecimal.ONE)
                .multiply(ONE_HUNDRED, MC);

        if (profitPercentage.compareTo(PROFIT_THRESHOLD_PERCENT) < 0) {
            return 0;
        }

        if (isUnchangedSinceLastDetection(match.getId(), profitPercentage)) {
            return 0;
        }

        List<SurebetLeg> legs = new ArrayList<>();
        for (Odds o : bestBySelection.values()) {
            BigDecimal legImpliedProbability = BigDecimal.ONE.divide(o.getOddValue(), MC);
            BigDecimal stakePercentage = legImpliedProbability.divide(totalImpliedProbability, MC)
                    .multiply(ONE_HUNDRED, MC);
            legs.add(new SurebetLeg(
                    o.getSelection(),
                    o.getBookmaker().getExternalKey(),
                    o.getOddValue().setScale(STORAGE_SCALE, RoundingMode.HALF_UP),
                    stakePercentage.setScale(STORAGE_SCALE, RoundingMode.HALF_UP)));
        }

        Surebet surebet = new Surebet();
        surebet.setMatch(match);
        surebet.setLegs(legs);
        surebet.setTotalImpliedProbability(totalImpliedProbability.setScale(STORAGE_SCALE, RoundingMode.HALF_UP));
        surebet.setProfitPercentage(profitPercentage.setScale(STORAGE_SCALE, RoundingMode.HALF_UP));
        surebetRepository.save(surebet);
        return 1;
    }

    private boolean isUnchangedSinceLastDetection(Long matchId, BigDecimal profitPercentage) {
        return surebetRepository.findTopByMatchIdOrderByDetectedAtDesc(matchId)
                .map(previous -> profitPercentage.subtract(previous.getProfitPercentage()).abs().compareTo(DEDUP_THRESHOLD_PERCENT) < 0)
                .orElse(false);
    }
}
