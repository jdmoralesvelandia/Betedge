package com.betedge.odds;

import static org.assertj.core.api.Assertions.assertThat;

import com.betedge.matches.Match;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MatchReconciliationServiceTest {

    private static final Instant KICKOFF = Instant.parse("2026-08-15T19:00:00Z");

    @Test
    void matchesWhenTeamNamesAreIdentical() {
        Match candidate = match("Arsenal", "Chelsea", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Arsenal", "Chelsea", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void matchesAcrossDifferentClubSuffixes() {
        // OddsPapi-style names with trailing suffixes vs The Odds API-style bare names.
        Match candidate = match("Arsenal FC", "Chelsea FC", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Arsenal", "Chelsea", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void matchesWhenKickoffDiffersButStaysWithinFiveMinuteTolerance() {
        Match candidate = match("Arsenal", "Chelsea", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Arsenal", "Chelsea", KICKOFF.plus(5, ChronoUnit.MINUTES));

        assertThat(result).contains(candidate);
    }

    @Test
    void doesNotMatchWhenKickoffDiffersByMoreThanFiveMinutes() {
        Match candidate = match("Arsenal", "Chelsea", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Arsenal", "Chelsea", KICKOFF.plus(6, ChronoUnit.MINUTES));

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotMatchCompletelyDifferentTeamNames() {
        Match candidate = match("Arsenal", "Chelsea", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Barcelona", "Real Madrid", KICKOFF);

        assertThat(result).isEmpty();
    }

    @Test
    void doesNotGuessWhenTwoCandidatesAreAmbiguous() {
        // Two plausible candidates for the same search - neither should be picked at random.
        Match candidateA = match("Arsenal", "Chelsea", KICKOFF);
        Match candidateB = match("Arsenal", "Chelsea", KICKOFF.plus(2, ChronoUnit.MINUTES));

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidateA, candidateB), "Arsenal", "Chelsea", KICKOFF.plus(1, ChronoUnit.MINUTES));

        assertThat(result).isEmpty();
    }

    @Test
    void ignoresAccentsWhenComparingNames() {
        Match candidate = match("Atletico Madrid", "Deportivo Alaves", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Atlético Madrid", "Deportivo Alavés", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void onlyDisambiguatesTheAmbiguousCandidateNotUnrelatedOnes() {
        // A genuinely unrelated fixture in the candidate pool shouldn't affect matching the real one.
        Match unrelated = match("Bayern Munich", "Dortmund", KICKOFF);
        Match candidate = match("Arsenal FC", "Chelsea FC", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(unrelated, candidate), "Arsenal", "Chelsea", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void manchesterUnitedAndManchesterCityNeverMatchAtIdenticalKickoff() {
        // Away team held constant (and matching) so the only variable under test is the
        // home-team pair - if "United" were still stripped as a suffix, this would wrongly match.
        Match candidate = match("Manchester United", "Everton", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Manchester City", "Everton", KICKOFF);

        assertThat(result).isEmpty();
    }

    @Test
    void manchesterUnitedAndManchesterCityNeverMatchWhenSearchOrderIsReversed() {
        Match candidate = match("Manchester City", "Everton", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Manchester United", "Everton", KICKOFF);

        assertThat(result).isEmpty();
    }

    @Test
    void manchesterUnitedAndManchesterCityNeverMatchWithinTimeTolerance() {
        // A non-zero but in-tolerance offset - confirms the rejection comes from name
        // normalization, not from incidentally falling outside the time window.
        Match candidate = match("Manchester United", "Everton", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Manchester City", "Everton", KICKOFF.plus(3, ChronoUnit.MINUTES));

        assertThat(result).isEmpty();
    }

    @Test
    void distinguishesSimultaneousKickoffsByTeamNameNotJustTime() {
        // Realistic scenario: several league fixtures kick off at the exact same instant (e.g.
        // Saturday 3pm), so time tolerance alone gives zero disambiguation between them - team
        // names have to do all the work, in both directions.
        Match unitedFixture = match("Manchester United", "Everton", KICKOFF);
        Match cityFixture = match("Manchester City", "Liverpool", KICKOFF);
        List<Match> simultaneousKickoffs = List.of(unitedFixture, cityFixture);

        Optional<Match> unitedResult = MatchReconciliationService.findMatchingCandidate(
                simultaneousKickoffs, "Manchester United", "Everton", KICKOFF);
        Optional<Match> cityResult = MatchReconciliationService.findMatchingCandidate(
                simultaneousKickoffs, "Manchester City", "Liverpool", KICKOFF);

        assertThat(unitedResult).contains(unitedFixture);
        assertThat(cityResult).contains(cityFixture);
    }

    @Test
    void treatsAmpersandAndAndAsEquivalent() {
        Match candidate = match("Brighton & Hove Albion", "Aston Villa", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Brighton and Hove Albion", "Aston Villa", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void treatsHyphenatedAndSpacedNamesAsEquivalent() {
        Match candidate = match("Lille OSC", "Paris Saint-Germain", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Lille", "Paris Saint Germain", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void stripsLeadingRcPrefix() {
        Match candidate = match("RC Lens", "Nice", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Racing Club De Lens", "Nice", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void stripsMultipleStackedSuffixesInOnePass() {
        // OddsPapi-style Brasileirão names stack a club suffix AND a 2-letter state code.
        Match candidate = match("Sao Paulo FC SP", "Coritiba FC PR", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Sao Paulo", "Coritiba", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void doesNotForceAMatchWhenNormalizationWouldLeaveNamesTooShort() {
        // A team literally named "FC" would normalize to "" after suffix-stripping - must never
        // match anything by accident just because an empty/tiny string is trivially "contained".
        Match candidate = match("FC", "Chelsea", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "FC", "Chelsea", KICKOFF);

        assertThat(result).isEmpty();
    }

    @Test
    void resolvesKnownAliasCeltaDeVigo() {
        Match candidate = match("RC Celta de Vigo", "CA Osasuna", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Celta Vigo", "CA Osasuna", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void resolvesKnownAliasDeportivoLaCoruna() {
        Match candidate = match("RC Deportivo de La Coruna", "Valencia CF", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Deportivo La Coruna", "Valencia", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void resolvesKnownAliasRacingSantander() {
        Match candidate = match("Racing Santander", "Real Betis", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Real Racing Club de Santander", "Real Betis", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void resolvesKnownAliasStadeRennaisVsRennes() {
        Match candidate = match("Stade Rennais FC", "Lyon", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Rennes", "Lyon", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void resolvesKnownAliasKolnVsCologneEvenWithRealDiacritic() {
        // The stored Match text keeps the real German spelling with its diacritic - normalize()
        // strips accents for comparison only, it never rewrites what's persisted.
        Match candidate = match("1. FC Köln", "TSG Hoffenheim", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "1. FC Cologne", "TSG Hoffenheim", KICKOFF);

        assertThat(result).contains(candidate);
    }

    @Test
    void aliasGroupsDoNotLeakIntoUnrelatedComparisons() {
        // Sanity check that being in a KNOWN_ALIAS_GROUPS entry doesn't make a name match
        // something outside its own group - Rennes must still not match an unrelated club.
        Match candidate = match("Stade Rennais FC", "Lyon", KICKOFF);

        Optional<Match> result = MatchReconciliationService.findMatchingCandidate(
                List.of(candidate), "Marseille", "Lyon", KICKOFF);

        assertThat(result).isEmpty();
    }

    private static Match match(String homeTeam, String awayTeam, Instant startTime) {
        Match match = new Match();
        match.setHomeTeam(homeTeam);
        match.setAwayTeam(awayTeam);
        match.setStartTime(startTime);
        return match;
    }
}
