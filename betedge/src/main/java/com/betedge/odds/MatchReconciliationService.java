package com.betedge.odds;

import com.betedge.matches.Competition;
import com.betedge.matches.Match;
import com.betedge.matches.MatchRepository;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reconciles fixtures from a complementary provider (e.g. The Odds API) against Match rows
 * already ingested from the primary source (OddsPapi), so the two can be recognized as the same
 * real-world event despite each source spelling team names and timestamps slightly differently.
 *
 * Never guesses: a candidate is only returned when exactly one existing Match satisfies both the
 * kickoff-time and team-name conditions. Zero matches or more than one (ambiguous) both yield
 * empty - a missed reconciliation is cheap to notice and retry later; a wrong one silently
 * corrupts two providers' odds into a single Match forever.
 */
@Service
@RequiredArgsConstructor
public class MatchReconciliationService {

    private static final Duration TIME_TOLERANCE = Duration.ofMinutes(5);

    /**
     * Trailing tokens that one provider tacks onto a club name and the other typically omits
     * (or vice versa) - e.g. OddsPapi's "Arsenal FC" vs The Odds API's "Arsenal". Stripped so
     * both sides normalize to the same core name. Restricted to purely organizational suffixes
     * that are NEVER part of a club's distinguishing identity. Words like "United", "City",
     * "Athletic", or "Real" must NEVER be added here, no matter how tempting for some other
     * pair of providers: for many clubs that exact word is what distinguishes them from another
     * club in the same city, e.g. "Manchester United" vs "Manchester City" - stripping "United"
     * made the former's normalized name a substring of the latter's, so namesMatch() silently
     * treated two different clubs as the same one. Time tolerance doesn't save this case either:
     * fixtures in the same league routinely share an exact kickoff instant (see
     * distinguishesSimultaneousKickoffsByTeamNameNotJustTime in the test class), so name
     * normalization has to be correct on its own, not just "usually correct."
     */
    private static final List<String> COMMON_SUFFIXES = List.of("afc", "fc", "cf", "sc");

    /** Matches combining diacritical marks left behind after Unicode NFD decomposition (e.g. the accent on "á"). */
    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{M}+");

    private final MatchRepository matchRepository;

    /**
     * Looks among the Match rows already ingested for {@code competition} (normally via OddsPapi)
     * for exactly one that plausibly represents the same real-world fixture as the given
     * complementary-source data. See {@link #findMatchingCandidate(List, String, String, Instant)}
     * for the actual matching rules.
     */
    public Optional<Match> findMatchingCandidate(
            Competition competition, String homeTeamName, String awayTeamName, Instant commenceTime) {
        List<Match> candidates = matchRepository.findByCompetition(competition);
        return findMatchingCandidate(candidates, homeTeamName, awayTeamName, commenceTime);
    }

    /**
     * Pure matching logic, independent of how {@code candidates} was fetched - this is what the
     * unit tests exercise directly, with hand-built Match instances instead of a real repository.
     *
     * A candidate qualifies only if BOTH hold:
     *   (a) its startTime is within {@link #TIME_TOLERANCE} of commenceTime in either direction;
     *   (b) after normalizing (see {@link #normalize}), the candidate's homeTeam and the given
     *       homeTeamName each contain the other as a substring, and likewise for the away side.
     *       Substring containment (not equality) because the two providers don't spell team
     *       names identically ("Arsenal" vs "Arsenal FC", accents present or stripped, etc).
     *
     * If more than one candidate qualifies, the match is ambiguous and this returns empty rather
     * than guessing - per the project's standing rule, a silently-wrong reconciliation is far
     * worse than a missed one.
     */
    static Optional<Match> findMatchingCandidate(
            List<Match> candidates, String homeTeamName, String awayTeamName, Instant commenceTime) {

        List<Match> qualifying = candidates.stream()
                .filter(m -> withinTimeTolerance(m.getStartTime(), commenceTime))
                .filter(m -> namesMatch(m.getHomeTeam(), homeTeamName) && namesMatch(m.getAwayTeam(), awayTeamName))
                .toList();

        return qualifying.size() == 1 ? Optional.of(qualifying.get(0)) : Optional.empty();
    }

    private static boolean withinTimeTolerance(Instant a, Instant b) {
        return Duration.between(a, b).abs().compareTo(TIME_TOLERANCE) <= 0;
    }

    private static boolean namesMatch(String a, String b) {
        String normalizedA = normalize(a);
        String normalizedB = normalize(b);
        // An empty string is a Java-quirky "substring" of everything - guard against a name that
        // strips down to nothing (e.g. a team literally named "FC") ever matching by accident.
        if (normalizedA.isEmpty() || normalizedB.isEmpty()) {
            return false;
        }
        return normalizedA.contains(normalizedB) || normalizedB.contains(normalizedA);
    }

    /**
     * Lowercases, strips accents/diacritics, and removes one trailing common club suffix
     * (see {@link #COMMON_SUFFIXES}) so "Arsenal FC" and "arsenal" normalize to the same "arsenal".
     * Order matters: accents are stripped before suffix-matching so an accented suffix (were one
     * ever added) would still be recognized.
     */
    private static String normalize(String teamName) {
        String lower = teamName.trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String withoutAccents = DIACRITICAL_MARKS.matcher(decomposed).replaceAll("");

        for (String suffix : COMMON_SUFFIXES) {
            if (withoutAccents.endsWith(" " + suffix)) {
                return withoutAccents.substring(0, withoutAccents.length() - suffix.length() - 1).trim();
            }
        }
        return withoutAccents.trim();
    }
}
