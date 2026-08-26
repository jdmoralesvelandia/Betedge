package com.betedge.odds;

import com.betedge.matches.Competition;
import com.betedge.matches.CompetitionRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Refreshes competitions whose next Match is about to kick off, on a much tighter cycle than
 * TheOddsApiIngestionService's regular 12h run - odds move fastest in the final hour before
 * kickoff. Unlike MatchStatusUpdateService (pure DB housekeeping), this spends real API budget,
 * so every trigger is gated by two independent checks: known remaining credit, and this
 * feature's own separate monthly cap (tracked via IngestionRun.triggeredBy=HOT_REFRESH, kept
 * apart from the regular cycle's own budget so the two can't silently cannibalize each other).
 * No per-league cooldown or daily cap on top of that - the match calendar itself bounds how often
 * one league can appear "hot" inside the final-hour window between checks.
 */
@Service
@RequiredArgsConstructor
public class HotMatchRefreshService {

    private static final Logger log = LoggerFactory.getLogger(HotMatchRefreshService.class);

    /**
     * A hard floor, not a safety margin: The Odds API has no separate quota-check call, so this
     * is only ever as fresh as the last real call any caller made - see
     * TheOddsApiClient.lastKnownRequestsRemaining. Blocking once known-remaining credit runs out
     * is the most that can honestly be claimed from a lagging signal.
     */
    private static final int MIN_REQUIRED_CREDIT = 1;

    /**
     * Purely defensive, same rationale as TheOddsApiIngestionService.CALL_DELAY_MS - applied only
     * between two real triggers within the same check cycle, never before a skipped
     * (credit- or budget-blocked) one.
     */
    private static final int CALL_DELAY_MS = 800;

    private final CompetitionRepository competitionRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final TheOddsApiClient theOddsApiClient;
    private final TheOddsApiIngestionService theOddsApiIngestionService;

    @Value("${hot-refresh.final-hour-window-minutes}")
    private int finalHourWindowMinutes;

    @Value("${hot-refresh.monthly-budget}")
    private int monthlyBudget;

    public void refreshHotMatches() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(finalHourWindowMinutes, ChronoUnit.MINUTES);
        List<Competition> hotCompetitions =
                competitionRepository.findTheOddsApiCompetitionsWithMatchStartingBetween(now, windowEnd);

        boolean anyTriggeredYet = false;
        for (Competition competition : hotCompetitions) {
            if (!hasRemainingCredit()) {
                log.warn("Skipping hot refresh for '{}': no known remaining The Odds API credit", competition.getName());
                continue;
            }
            // Re-checked fresh for every competition, not computed once for the whole cycle: the
            // first trigger below can itself push the count up to the cap, and a later
            // competition in this SAME cycle must see that updated count, not a stale one.
            if (!underMonthlyBudget()) {
                log.warn("Skipping hot refresh for '{}': monthly hot-refresh budget ({}) reached",
                        competition.getName(), monthlyBudget);
                continue;
            }

            if (anyTriggeredYet) {
                sleepBetweenCalls();
            }
            theOddsApiIngestionService.refreshCompetition(competition, TriggeredBy.HOT_REFRESH);
            anyTriggeredYet = true;
        }
    }

    private boolean hasRemainingCredit() {
        return theOddsApiClient.lastKnownRequestsRemaining()
                .map(remaining -> remaining >= MIN_REQUIRED_CREDIT)
                .orElse(false);
    }

    private boolean underMonthlyBudget() {
        long countThisMonth = ingestionRunRepository.countByProviderAndTriggeredByAndStartedAtGreaterThanEqual(
                DataSource.THEODDSAPI, TriggeredBy.HOT_REFRESH, startOfCurrentMonthUtc());
        return countThisMonth < monthlyBudget;
    }

    /** UTC: Instant (used throughout this codebase) carries no timezone of its own, so this is a fixed, unambiguous reference point for "calendar month" rather than the server's local zone. */
    private static Instant startOfCurrentMonthUtc() {
        return YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static void sleepBetweenCalls() {
        try {
            Thread.sleep(CALL_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
