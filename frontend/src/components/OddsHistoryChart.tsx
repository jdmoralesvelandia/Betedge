import { useEffect, useMemo, useState } from 'react'
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, XAxis, YAxis } from 'recharts'
import type { OddsHistoryEntryDto } from '../api/types'
import { bookmakerLabel } from '../lib/format'
import { hasHistoryBeyondRecentWindow, windowSeries } from '../lib/oddsWindow'
import {
  CHART_DOT_STYLE,
  decimalPlacesForRange,
  formatOddValue,
  xAxisTickFormatterForRange,
  yAxisDomain,
} from '../lib/oddsChartFormat'
import { ChevronIcon } from './ChevronIcon'
import { CursorTooltip, type FlatPoint } from './OddsChartTooltip'

const SERIES_COLORS = [
  'var(--color-series-1)',
  'var(--color-series-2)',
  'var(--color-series-3)',
  'var(--color-series-4)',
  'var(--color-series-5)',
  'var(--color-series-6)',
  'var(--color-series-7)',
  'var(--color-series-8)',
]
const MAX_SERIES = SERIES_COLORS.length
/** How many bookmakers are auto-checked when a tab first opens - lower than MAX_SERIES on purpose,
 * so the chart starts readable; the user can still check up to MAX_SERIES manually via the table. */
const DEFAULT_SERIES_COUNT = 3

interface Point {
  x: number
  y: number
}

/**
 * One row per real timestamp across every plotted bookmaker - see the class-level comment on why
 * <Line> no longer gets its own independent `data` array. A bookmaker with no report at that exact
 * timestamp gets `undefined`, not a guessed value.
 */
type MergedRow = { x: number } & Record<string, number | undefined>

/**
 * Collapsible ranking table - every bookmaker with a current price for this selection, best odd
 * first, one checkbox per row. Lets the user override which subset of MAX_SERIES the chart draws,
 * instead of always the automatic top MAX_SERIES. Closed by default, same pattern as this
 * project's other collapsible sections (chevron icon that rotates on open).
 */
function BookmakerSelectionTable({
  rankedBookmakers,
  selectedSlugs,
  onToggle,
  decimalPlaces,
}: {
  rankedBookmakers: [string, Point[]][]
  selectedSlugs: Set<string>
  onToggle: (slug: string) => void
  decimalPlaces: number
}) {
  const [isOpen, setIsOpen] = useState(false)
  const atLimit = selectedSlugs.size >= MAX_SERIES

  return (
    <div className="mt-6">
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-expanded={isOpen}
        className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-ink-faint transition-colors hover:text-ink-soft"
      >
        <ChevronIcon open={isOpen} />
        Seleccionar casas a mostrar ({selectedSlugs.size}/{MAX_SERIES})
      </button>

      {isOpen && (
        <div className="mt-3 overflow-x-auto rounded-xl border border-border bg-surface">
          <div className="min-w-[320px]">
            <div className="grid grid-cols-[auto_1fr_auto] items-center gap-4 border-b border-border px-4 py-2 text-xs uppercase tracking-wide text-ink-faint">
              <span className="w-4" />
              <span>Casa</span>
              <span>Cuota</span>
            </div>
            {rankedBookmakers.map(([slug, points]) => {
              const checked = selectedSlugs.has(slug)
              const disabled = !checked && atLimit
              const latest = points[points.length - 1].y
              return (
                <label
                  key={slug}
                  className={`grid grid-cols-[auto_1fr_auto] items-center gap-4 border-b border-border px-4 py-2 text-sm last:border-0 ${
                    disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer hover:bg-surface-2'
                  }`}
                >
                  <input
                    type="checkbox"
                    checked={checked}
                    disabled={disabled}
                    onChange={() => onToggle(slug)}
                    className="h-4 w-4 accent-series-1"
                  />
                  <span className="truncate text-ink-soft">{bookmakerLabel(slug)}</span>
                  <span className="whitespace-nowrap font-medium text-ink">{formatOddValue(latest, decimalPlaces)}</span>
                </label>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}

/**
 * The comparison view - up to MAX_SERIES bookmakers plotted together (see PROBLEMA 3 history in
 * git blame for why the shared-dataset/CursorTooltip approach exists), with a ranking table to
 * override which ones. showFullHistory/onToggleFullHistory are controlled from MatchDetailPage
 * (not local state) so this view and SingleBookmakerChart, its sibling, share one time window
 * instead of each tracking its own.
 */
export function OddsHistoryChart({
  entries,
  showFullHistory,
  onToggleFullHistory,
}: {
  entries: OddsHistoryEntryDto[]
  showFullHistory: boolean
  onToggleFullHistory: () => void
}) {
  /**
   * Every bookmaker with at least one point for this selection, full (unwindowed) history, ranked
   * by CURRENT best odd (their own latest real price - the last point after the ascending sort
   * below, since a higher odd pays more) descending. This is the complete ranking
   * BookmakerSelectionTable shows - the chart itself only draws whichever subset is currently in
   * `selectedSlugs` below, not automatically the first MAX_SERIES of this list.
   *
   * Recalculates automatically on every selection switch, since `entries` itself is a new
   * reference the moment MatchDetailPage re-filters by selection - no extra dependency needed
   * here.
   */
  const rankedBookmakers = useMemo(() => {
    const byBookmaker = new Map<string, Point[]>()
    for (const entry of entries) {
      const points = byBookmaker.get(entry.bookmakerSlug) ?? []
      points.push({ x: new Date(entry.timestamp).getTime(), y: entry.oddValue })
      byBookmaker.set(entry.bookmakerSlug, points)
    }
    for (const points of byBookmaker.values()) {
      points.sort((a, b) => a.x - b.x)
    }
    return [...byBookmaker.entries()].sort((a, b) => {
      const latestA = a[1][a[1].length - 1].y
      const latestB = b[1][b[1].length - 1].y
      return latestB - latestA
    })
  }, [entries])

  const autoTopSlugs = useMemo(
    () => new Set(rankedBookmakers.slice(0, DEFAULT_SERIES_COUNT).map(([slug]) => slug)),
    [rankedBookmakers],
  )

  /**
   * Which bookmakers are actually drawn - defaults to the automatic top DEFAULT_SERIES_COUNT by
   * current best odd, user-adjustable via BookmakerSelectionTable's checkboxes (hard-capped at
   * MAX_SERIES there, since that's also how many SERIES_COLORS exist - the default is just a
   * cleaner starting point, not a lower cap).
   *
   * Resets to the automatic default every time `entries` changes - i.e. every time the
   * Local/Empate/Visitante tab switches - rather than persisting the user's manual picks across
   * tabs. Chosen deliberately: `entries` is a completely different dataset per tab (different
   * bookmakers can even be present), so a hand-picked set of chart lines for "Local" carries no
   * particular meaning once you're looking at "Visitante" - each tab gets its own ranking AND its
   * own selection, always starting from that tab's own top DEFAULT_SERIES_COUNT.
   */
  const [selectedSlugs, setSelectedSlugs] = useState<Set<string>>(autoTopSlugs)
  useEffect(() => {
    setSelectedSlugs(autoTopSlugs)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entries])

  function toggleSlug(slug: string) {
    setSelectedSlugs((prev) => {
      const next = new Set(prev)
      if (next.has(slug)) {
        next.delete(slug)
      } else if (next.size < MAX_SERIES) {
        next.add(slug)
      }
      return next
    })
  }

  // The subset of rankedBookmakers currently checked, still in rank order - which <Line>s exist,
  // and in what order colors get assigned. Stable across the 72h/full-history toggle: that toggle
  // only trims how much of each CHOSEN bookmaker's own history gets drawn (see windowedSeries
  // below), never which bookmakers are chosen.
  const bookmakerSeries = useMemo(
    () => rankedBookmakers.filter(([slug]) => selectedSlugs.has(slug)),
    [rankedBookmakers, selectedSlugs],
  )

  const colorBySlug = useMemo(
    () => new Map(bookmakerSeries.map(([slug], index) => [slug, SERIES_COLORS[index % MAX_SERIES]])),
    [bookmakerSeries],
  )

  // Every visible series' full (unwindowed) timestamps, flattened together - purely for the
  // "toggling full-history would/wouldn't reveal anything" hint below, unrelated to the actual
  // per-bookmaker cutoff windowedSeries applies (see its own comment).
  const seriesTimestamps = useMemo(
    () => bookmakerSeries.flatMap(([, points]) => points.map((p) => p.x)),
    [bookmakerSeries],
  )

  // Whether toggling to "full history" would actually reveal anything - if the match's whole
  // tracked history is already under 72h, the toggle is a harmless no-op (still shown, per design).
  const hasHiddenHistory = useMemo(() => hasHistoryBeyondRecentWindow(seriesTimestamps), [seriesTimestamps])

  // Whether NONE of the currently visible bookmakers have EVER reported more than one real price,
  // in their whole tracked history - not scoped to the 72h window. Same concept as
  // SingleBookmakerChart's own hasOnlyOnePointEver, generalized to the whole visible group since
  // this view can show several bookmakers at once: only worth surfacing when it's true of ALL of
  // them (a mix of fresh and established bookmakers already reads fine as-is, with the fresh one's
  // single dot sitting alongside the others' real lines).
  const allSeriesHaveOnlyOnePointEver = useMemo(
    () => bookmakerSeries.length > 0 && bookmakerSeries.every(([, points]) => points.length === 1),
    [bookmakerSeries],
  )

  // Per-slug points actually plotted right now - full history, or just the last 72h relative to
  // THAT bookmaker's OWN latest timestamp (windowSeries, shared with SingleBookmakerChart - see
  // its own Javadoc in lib/oddsWindow.ts for the incident a single group-wide cutoff caused: a
  // rarely-updated bookmaker's own history silently narrowed by another bookmaker updating more
  // often). Only used to build mergedData/decimalPlaces/flatPoints below, never to decide which
  // <Line>s exist (that's bookmakerSeries, kept stable - see its own comment).
  const windowedSeries = useMemo(
    () => bookmakerSeries.map(([slug, points]) => [slug, windowSeries(points, showFullHistory)] as const),
    [bookmakerSeries, showFullHistory],
  )

  /**
   * One shared dataset, keyed by timestamp, instead of a separate `data` array per <Line> - see
   * this file's PROBLEMA 3 investigation notes in the PR/commit for the full trace. Recharts'
   * shared Tooltip (recharts/es6/state/selectors/combiners/combineTooltipPayload.js) first tries
   * to find, in each graphical item's OWN data array, an entry whose axis value exactly equals the
   * hovered activeLabel; when that exact match fails (near-guaranteed here, since two different
   * bookmakers' real ingestion timestamps essentially never land on the exact same millisecond) it
   * falls back to arrayTooltipSearcher (recharts/es6/state/optionsSlice.js), which is a plain
   * `data[activeIndex]` positional lookup - the SAME numeric index applied to every series' own
   * array regardless of that series' actual length or timestamps. With independent per-Line data
   * of wildly different lengths (confirmed against match 48/Bayern-Stuttgart: pinnacle 27 points,
   * onexbet/tipico_de/winamax_de only 2), that index landed on an unrelated point in time for most
   * series - shown under the correct bookmaker name/color, but at the wrong moment, reading as "the
   * wrong house's price" wherever series lengths diverge. Sharing one dataset makes every line's
   * lookup index refer to the same real row, so there's no cross-series index to misalign in the
   * first place.
   *
   * The hover tooltip itself no longer even goes through Recharts' Tooltip machinery at all -
   * see CursorTooltip/findNearestPoint in OddsChartTooltip.tsx - but the merged dataset is still
   * what keeps the <Line>s themselves (their stepAfter rendering, connectNulls) correct.
   */
  const mergedData = useMemo(() => {
    const rowsByTimestamp = new Map<number, MergedRow>()
    for (const [slug, points] of windowedSeries) {
      for (const point of points) {
        let existing = rowsByTimestamp.get(point.x)
        if (!existing) {
          existing = { x: point.x }
          rowsByTimestamp.set(point.x, existing)
        }
        existing[slug] = point.y
      }
    }
    return [...rowsByTimestamp.values()].sort((a, b) => a.x - b.x)
  }, [windowedSeries])

  const flatPoints = useMemo(
    () => windowedSeries.flatMap(([slug, points]) => points.map((p): FlatPoint => ({ slug, x: p.x, y: p.y }))),
    [windowedSeries],
  )

  // Every value actually drawn right now (post-window, post-selection) - a bookmaker not currently
  // checked, or a point outside the current 72h/full-history window, shouldn't be able to widen
  // the precision shown, or influence the Y-axis domain, for what's actually drawn.
  const visibleValues = useMemo(
    () => windowedSeries.flatMap(([, points]) => points.map((p) => p.y)),
    [windowedSeries],
  )

  const decimalPlaces = useMemo(() => {
    if (visibleValues.length === 0) return 2
    const range = Math.max(...visibleValues) - Math.min(...visibleValues)
    return decimalPlacesForRange(range)
  }, [visibleValues])

  const xAxisTickFormatter = useMemo(() => {
    if (mergedData.length === 0) return xAxisTickFormatterForRange(0)
    const xs = mergedData.map((row) => row.x)
    const rangeMs = Math.max(...xs) - Math.min(...xs)
    return xAxisTickFormatterForRange(rangeMs)
  }, [mergedData])

  if (entries.length === 0) {
    return <p className="text-sm text-ink-faint">Todavía no hay histórico de cuotas para este resultado.</p>
  }

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <p className="text-xs text-ink-faint">
          {showFullHistory
            ? 'Mostrando todo el historial disponible.'
            : 'Mostrando las últimas 72 horas de cada casa.'}
        </p>
        <button
          type="button"
          onClick={onToggleFullHistory}
          className="whitespace-nowrap rounded-md border border-border bg-surface-2 px-3 py-1.5 text-xs font-medium text-ink-soft transition-colors hover:bg-surface"
        >
          {showFullHistory ? 'Ver solo lo reciente' : 'Ver historial completo'}
        </button>
      </div>

      <div className="h-72 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={mergedData} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
            <CartesianGrid stroke="var(--color-grid)" vertical={false} />
            <XAxis
              dataKey="x"
              type="number"
              domain={['dataMin', 'dataMax']}
              tickFormatter={xAxisTickFormatter}
              stroke="var(--color-axis)"
              tick={{ fill: 'var(--color-ink-faint)', fontSize: 12 }}
            />
            <YAxis
              type="number"
              domain={yAxisDomain(visibleValues)}
              tickFormatter={(value: number) => formatOddValue(value, decimalPlaces)}
              stroke="var(--color-axis)"
              tick={{ fill: 'var(--color-ink-faint)', fontSize: 12 }}
              width={40}
            />
            <Legend
              wrapperStyle={{ fontSize: 12, color: 'var(--color-ink-soft)' }}
              formatter={(value: string) => bookmakerLabel(value)}
            />
            {bookmakerSeries.map(([slug], index) => (
              <Line
                key={slug}
                dataKey={slug}
                name={slug}
                type="stepAfter"
                connectNulls
                stroke={SERIES_COLORS[index % MAX_SERIES]}
                strokeWidth={2}
                dot={CHART_DOT_STYLE}
                isAnimationActive={false}
              />
            ))}
            <CursorTooltip points={flatPoints} colorBySlug={colorBySlug} decimalPlaces={decimalPlaces} />
          </LineChart>
        </ResponsiveContainer>
      </div>
      {allSeriesHaveOnlyOnePointEver && (
        <p className="mt-1 text-xs text-ink-faint">
          Ninguna casa visible registró más de un precio todavía - aún no hay suficiente historial para ver evolución.
        </p>
      )}
      {!showFullHistory && !hasHiddenHistory && (
        <p className="mt-1 text-xs text-ink-faint">
          Este partido tiene menos de 72 horas de historial - &ldquo;Ver historial completo&rdquo; no cambiará nada.
        </p>
      )}

      <BookmakerSelectionTable
        rankedBookmakers={rankedBookmakers}
        selectedSlugs={selectedSlugs}
        onToggle={toggleSlug}
        decimalPlaces={decimalPlaces}
      />
    </div>
  )
}
