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
import java.util.Set;
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
     *
     * "osc"/"sv"/"ac" added 2026-08-26 (Lille OSC, generic SV/AC-prefixed clubs) alongside the
     * original afc/fc/cf/sc, confirmed safe against the real rosters of the 4 leagues both
     * providers currently cover (see the class-level 2026-08-26 comment below) - none of them is
     * ever the sole distinguishing word for a club in those rosters, unlike United/City/Real.
     */
    private static final List<String> COMMON_SUFFIXES = List.of("afc", "fc", "cf", "sc", "osc", "sv", "ac");

    /**
     * Leading tokens with the same "purely organizational, never distinguishing" property as
     * {@link #COMMON_SUFFIXES}, just placed at the front instead of the back - e.g. OddsPapi's
     * "RC Celta de Vigo"/"RC Lens" vs The Odds API's "Celta Vigo"/"RC Lens" (the latter keeping
     * "RC" - stripped on both sides either way, so it's a no-op when both already agree). Kept to
     * exactly this one entry, added 2026-08-26 for a confirmed real case - don't add "Real" or
     * similar here for the same reason it's banned from COMMON_SUFFIXES.
     */
    private static final List<String> COMMON_PREFIXES = List.of("rc");

    /**
     * A trailing 2-letter token is very often a regional/state abbreviation a provider appends
     * (confirmed real case: OddsPapi's Brasileirão names like "Sao Paulo FC SP", "Coritiba FC PR"
     * vs The Odds API's bare "Sao Paulo"/"Coritiba" - SP/PR are Brazilian state codes, not part of
     * the club name). Deliberately generic (no hardcoded list) rather than enumerating known state
     * codes, since new ones could appear in any league - but this is the riskiest rule here: a
     * genuine 2-letter club-identity token would be silently stripped too. Confirmed empirically
     * against the real rosters of all 4 currently-shared leagues (Premier League, La Liga, Serie
     * A, Ligue 1) with zero false positives as of 2026-08-26, but re-verify against any NEW
     * league's roster before trusting this rule there - if a specific club's real name is known to
     * end in an inseparable 2-letter token (the brief mentioned Sporting CP as the canonical
     * example of the risk, though it isn't in any league this project currently tracks), flag it
     * instead of assuming this rule is safe for it.
     */
    private static final int GENERIC_ABBREVIATION_LENGTH = 2;

    private static final int MAX_STRIP_PASSES = 3;

    /** Below this, a normalized name is too short/generic to compare with any confidence - see normalize()'s javadoc. */
    private static final int MIN_COMPARABLE_LENGTH = 3;

    /** Matches combining diacritical marks left behind after Unicode NFD decomposition (e.g. the accent on "á"). */
    private static final Pattern DIACRITICAL_MARKS = Pattern.compile("\\p{M}+");

    private static final Pattern AMPERSAND = Pattern.compile("\\s*&\\s*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Genuine naming-convention or language differences between the two providers that no safe,
     * general normalization rule resolves - e.g. a demonym vs the city name (The Odds API's
     * "Rennes" vs OddsPapi's "Stade Rennais"), a short colloquial name vs the full official one
     * ("Racing Santander" vs "Real Racing Club de Santander" - the extra "de"/"Club" words break
     * substring containment even after every COMMON_SUFFIXES/COMMON_PREFIXES strip), or an actual
     * translation (German "1. FC Köln" vs English "1. FC Cologne" - not a spelling variant at all,
     * no string-similarity rule could ever bridge it). Each group holds every known normalize()d
     * spelling for one real club; two names match if they land in the same group, checked only
     * after the normal containment check already failed.
     *
     * Built 2026-08-26 by cross-referencing OddsPapi's and The Odds API's REAL, full rosters for
     * the 4 leagues both currently cover (Premier League, La Liga, Serie A, Ligue 1), plus 2
     * already-known historical cases from Bundesliga/Brasileirão (no longer actively ingested
     * since V19, but their existing duplicate Match rows are still real data). Confirmed by
     * fetching both providers' actual data for this season, not guessed from name patterns alone.
     *
     * MUST be reviewed at the start of each new season: promotions/relegations change which clubs
     * are in scope for each league, and a provider can change its own naming convention at any
     * time - an entry here that's no longer accurate just fails to match (safe, just missed),
     * but don't assume this list is still complete without rechecking.
     */
    private static final List<Set<String>> KNOWN_ALIAS_GROUPS = List.of(
            Set.of("celta de vigo", "celta vigo"),
            Set.of("deportivo de la coruna", "deportivo la coruna"),
            Set.of("racing santander", "real racing club de santander"),
            Set.of("stade rennais", "rennes"),
            Set.of("1. fc koln", "1. fc cologne"));

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
     *       homeTeamName either contain each other as a substring or land in the same
     *       {@link #KNOWN_ALIAS_GROUPS} entry, and likewise for the away side. Substring
     *       containment (not equality) because the two providers don't spell team names
     *       identically ("Arsenal" vs "Arsenal FC", accents present or stripped, etc) - the
     *       stored Match text itself is never altered by any of this, only the in-memory strings
     *       being compared here.
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
        // A too-short (or empty) normalized name can't be compared with any confidence - besides
        // the degenerate empty-string case (e.g. a team literally named "FC"), over-aggressive
        // stripping could otherwise reduce a real short name to something generic enough to
        // accidentally contain/be contained by an unrelated one.
        if (normalizedA.length() < MIN_COMPARABLE_LENGTH || normalizedB.length() < MIN_COMPARABLE_LENGTH) {
            return false;
        }
        if (normalizedA.contains(normalizedB) || normalizedB.contains(normalizedA)) {
            return true;
        }
        return sameKnownAlias(normalizedA, normalizedB);
    }

    private static boolean sameKnownAlias(String normalizedA, String normalizedB) {
        for (Set<String> group : KNOWN_ALIAS_GROUPS) {
            if (group.contains(normalizedA) && group.contains(normalizedB)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lowercases; strips accents/diacritics; unifies "&" and "and" (The Odds API's "Brighton and
     * Hove Albion" vs OddsPapi's "Brighton & Hove Albion") and hyphens vs spaces ("Paris
     * Saint-Germain" vs "Paris Saint Germain") into one canonical form; then iteratively strips up
     * to {@value #MAX_STRIP_PASSES} known organizational suffixes/prefixes (see
     * {@link #COMMON_SUFFIXES}, {@link #COMMON_PREFIXES}) and one trailing 2-letter abbreviation
     * token (see {@link #GENERIC_ABBREVIATION_LENGTH}) - iterative because a single name can carry
     * more than one, e.g. "Sao Paulo FC SP" needs both "SP" and "FC" stripped to reach "sao paulo".
     *
     * This is comparison-only: the returned string is never written back to a Match or Odds row -
     * see the class javadoc on {@link #findMatchingCandidate(List, String, String, Instant)} for
     * why the original, correctly-accented text is always what gets persisted.
     */
    private static String normalize(String teamName) {
        String unifiedConnectors = AMPERSAND.matcher(teamName.trim()).replaceAll(" and ");
        String lower = WHITESPACE.matcher(unifiedConnectors).replaceAll(" ").trim().toLowerCase(Locale.ROOT);
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String withoutAccents = DIACRITICAL_MARKS.matcher(decomposed).replaceAll("");
        String withoutHyphens = withoutAccents.replace('-', ' ');
        String collapsed = WHITESPACE.matcher(withoutHyphens).replaceAll(" ").trim();

        String result = collapsed;
        for (int pass = 0; pass < MAX_STRIP_PASSES; pass++) {
            String stripped = stripOneKnownToken(result);
            if (stripped.equals(result)) {
                break;
            }
            result = stripped;
        }
        return result;
    }

    private static String stripOneKnownToken(String normalized) {
        for (String suffix : COMMON_SUFFIXES) {
            if (normalized.endsWith(" " + suffix)) {
                return normalized.substring(0, normalized.length() - suffix.length() - 1).trim();
            }
        }
        for (String prefix : COMMON_PREFIXES) {
            if (normalized.startsWith(prefix + " ")) {
                return normalized.substring(prefix.length() + 1).trim();
            }
        }
        String[] tokens = normalized.split(" ");
        if (tokens.length > 1 && tokens[tokens.length - 1].length() == GENERIC_ABBREVIATION_LENGTH) {
            return normalized.substring(0, normalized.length() - GENERIC_ABBREVIATION_LENGTH - 1).trim();
        }
        return normalized;
    }
}
