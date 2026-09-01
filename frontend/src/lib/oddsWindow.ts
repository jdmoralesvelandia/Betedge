import type { DataSource, OddsHistoryEntryDto } from '../api/types'

/**
 * Shared "recent window" rule used by both OddsHistoryChart and OddsMovementsTable so the two stay
 * in sync under one toggle - by default only the last 72h of history is shown, relative to the
 * MOST RECENT timestamp actually present in the data, never to "now" (a finished match's chart/
 * table must still show its last hours of real odds, not go blank just because the match itself,
 * and its last update, are already in the past).
 */
export const HOUR_MS = 60 * 60 * 1000
export const DAY_MS = 24 * HOUR_MS
export const DEFAULT_RECENT_WINDOW_MS = 72 * HOUR_MS

/**
 * ISO timestamp string -> ms since epoch, passing null through unchanged - for the optional
 * per-provider run timestamps on OddsHistoryResponseDto (lastOddsPapiRunAt/lastTheOddsApiRunAt),
 * converted once in MatchDetailPage the same way entries' own timestamps already are everywhere
 * else in these charts, rather than each chart component re-parsing the ISO string itself.
 */
export function toEpochMs(iso: string | null): number | null {
  return iso === null ? null : new Date(iso).getTime()
}

/** Most recent of the given timestamps (ms since epoch), or null for an empty list. */
export function latestOf(timestamps: number[]): number | null {
  let max: number | null = null
  for (const t of timestamps) {
    if (max === null || t > max) max = t
  }
  return max
}

/**
 * The recent-window cutoff (inclusive lower bound), or null when there's nothing to compute from.
 * Pass showFullHistory=true to disable the window entirely (returns null, meaning "no cutoff").
 */
export function recentWindowCutoff(timestamps: number[], showFullHistory: boolean): number | null {
  if (showFullHistory) return null
  const latest = latestOf(timestamps)
  return latest === null ? null : latest - DEFAULT_RECENT_WINDOW_MS
}

/**
 * Whether the full span of `timestamps` exceeds the default recent window - i.e. whether toggling
 * to "full history" would actually reveal anything. When false, the toggle is a harmless no-op
 * (everything is already within the recent window) - both OddsHistoryChart and OddsMovementsTable
 * surface that to the user instead of leaving a "why didn't anything change?" toggle.
 */
export function hasHistoryBeyondRecentWindow(timestamps: number[]): boolean {
  const latest = latestOf(timestamps)
  if (latest === null) return false
  let min = latest
  for (const t of timestamps) {
    if (t < min) min = t
  }
  return latest - min > DEFAULT_RECENT_WINDOW_MS
}

/**
 * Applies the 72h/full-history window to ONE series' own points, anchored to THAT series' own
 * latest timestamp - never another series' fresher (or staler) one. This is the one windowing
 * rule OddsHistoryChart (many series, windows each independently) and SingleBookmakerChart (one
 * series) both need to share, rather than each computing its own cutoff.
 *
 * Incident this fixes (2026-08-28, TSG Hoffenheim vs Borussia Dortmund / Tipico): OddsHistoryChart
 * used to compute ONE cutoff for the whole visible group, anchored to whichever selected
 * bookmaker happened to have the most recent update - a rarely-updated bookmaker's own history
 * could then get silently narrowed, or eventually excluded entirely, by another bookmaker simply
 * updating more often, even though relative to ITS OWN data nothing was actually out of window.
 * SingleBookmakerChart never had this problem (it only ever windows one series against itself) -
 * this function is that same, already-correct rule, extracted so OddsHistoryChart uses it too.
 */
export function windowSeries<P extends { x: number }>(points: P[], showFullHistory: boolean): P[] {
  const cutoff = recentWindowCutoff(
    points.map((p) => p.x),
    showFullHistory,
  )
  return cutoff === null ? points : points.filter((p) => p.x >= cutoff)
}

/**
 * bookmakerSlug -> the one DataSource that ever reports it. Safe to take the first entry seen per
 * slug: since 2026-08-28 (pinnacle's structural exclusion from theoddsapi-ingestion.bookmakers -
 * see application.yml's own comment) every curated bookmaker comes from exactly one provider,
 * never both - there's no longer a real (match, bookmaker) pair straddling two sources.
 */
export function dataSourceBySlug(entries: OddsHistoryEntryDto[]): Map<string, DataSource> {
  const map = new Map<string, DataSource>()
  for (const entry of entries) {
    if (!map.has(entry.bookmakerSlug)) map.set(entry.bookmakerSlug, entry.dataSource)
  }
  return map
}

/**
 * Appends a synthetic trailing point - same y as the series' own last real point, at
 * lastSuccessfulRunAt - when that provider's last completed run happened AFTER the series' last
 * real point. Visually extends the line to "still confirmed unchanged as of the last real check",
 * instead of leaving it dangling at its last update (which reads as "unknown what happened
 * since", not "confirmed still the same"). No-op (returns `points` itself, unmodified) when
 * there's nothing to extend from, or the last successful run isn't actually newer.
 *
 * The synthetic point is ONLY ever meant for the array literally handed to <LineChart data={...}>
 * - callers must build every other derived value (tooltip points, decimal-precision/domain
 * calculations, "how many real points does this series have" checks) from the ORIGINAL points
 * array, never from this function's return value, so the synthetic point can never be mistaken
 * for - or counted as - a real registered price.
 */
export function withTrailingConfirmation<P extends { x: number }>(
  points: P[],
  lastSuccessfulRunAt: number | null,
): P[] {
  if (points.length === 0 || lastSuccessfulRunAt === null) return points
  const lastReal = points[points.length - 1]
  if (lastSuccessfulRunAt <= lastReal.x) return points
  return [...points, { ...lastReal, x: lastSuccessfulRunAt }]
}
