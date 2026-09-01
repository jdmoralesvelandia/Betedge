import { useEffect, useMemo, useState } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, XAxis, YAxis } from 'recharts'
import type { OddsHistoryEntryDto } from '../api/types'
import { bookmakerLabel, formatDateTime } from '../lib/format'
import { dataSourceBySlug, hasHistoryBeyondRecentWindow, windowSeries, withTrailingConfirmation } from '../lib/oddsWindow'
import {
  CHART_DOT_STYLE,
  decimalPlacesForRange,
  formatOddValue,
  xAxisTickFormatterForRange,
  yAxisDomain,
} from '../lib/oddsChartFormat'
import { CursorTooltip, type FlatPoint } from './OddsChartTooltip'

interface Point {
  x: number
  y: number
}

/** The single line's own accent color - a fixed choice (not rank-based) since there's only ever one series here. */
const LINE_COLOR = 'var(--color-series-1)'

/**
 * The default view when opening a match: one bookmaker's full price history at a time, picked from
 * a dropdown of every bookmaker with data for the active selection (not capped at MAX_SERIES - a
 * single line has no color-collision problem the comparison view's 8-series cap exists for).
 * Reuses the exact same windowing (72h/full-history, via lib/oddsWindow), 2D-distance hover
 * tooltip, and dynamic-decimal axis formatting as OddsHistoryChart - both charts pull from the same
 * shared modules (lib/oddsChartFormat.ts, OddsChartTooltip.tsx) rather than duplicating that logic.
 *
 * showFullHistory/onToggleFullHistory are controlled from MatchDetailPage, shared with
 * OddsHistoryChart - see that component's own comment on why. lastOddsPapiRunAt/
 * lastTheOddsApiRunAt (ms since epoch, or null if that provider has never completed a run) come
 * from the same place - see withTrailingConfirmation's own Javadoc for what they're for.
 */
export function SingleBookmakerChart({
  entries,
  showFullHistory,
  onToggleFullHistory,
  lastOddsPapiRunAt,
  lastTheOddsApiRunAt,
}: {
  entries: OddsHistoryEntryDto[]
  showFullHistory: boolean
  onToggleFullHistory: () => void
  lastOddsPapiRunAt: number | null
  lastTheOddsApiRunAt: number | null
}) {
  // Every bookmaker with data for this selection, full history, ranked by current best odd
  // descending - same ranking rule as OddsHistoryChart's, just never sliced to MAX_SERIES here.
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

  const dataSourceForSlug = useMemo(() => dataSourceBySlug(entries), [entries])

  // Rough precision just for the dropdown's own price labels - each option shows one bookmaker's
  // own single latest value, so this only needs to be reasonable across the whole list, not tied
  // to the chart's own (narrower, windowed, single-bookmaker) decimalPlaces below.
  const dropdownDecimalPlaces = useMemo(() => {
    const latestValues = rankedBookmakers.map(([, points]) => points[points.length - 1].y)
    if (latestValues.length === 0) return 2
    const range = Math.max(...latestValues) - Math.min(...latestValues)
    return decimalPlacesForRange(range)
  }, [rankedBookmakers])

  /**
   * Which single bookmaker is charted - defaults to the current best odd (rankedBookmakers[0]),
   * user-adjustable via the dropdown. Resets to that default every time `entries` changes (i.e.
   * every Local/Empate/Visitante switch) - same reasoning as OddsHistoryChart's selectedSlugs: a
   * hand-picked bookmaker for "Local" carries no particular meaning once looking at "Visitante".
   */
  const [selectedSlug, setSelectedSlug] = useState<string | null>(rankedBookmakers[0]?.[0] ?? null)
  useEffect(() => {
    setSelectedSlug(rankedBookmakers[0]?.[0] ?? null)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entries])

  const selectedPoints = useMemo(
    () => rankedBookmakers.find(([slug]) => slug === selectedSlug)?.[1] ?? [],
    [rankedBookmakers, selectedSlug],
  )

  const selectedTimestamps = useMemo(() => selectedPoints.map((p) => p.x), [selectedPoints])

  // Whether toggling to "full history" would actually reveal anything for THIS bookmaker - if its
  // own tracked history is already under 72h, the toggle is a harmless no-op (still shown, per
  // design, same as OddsHistoryChart).
  const hasHiddenHistory = useMemo(() => hasHistoryBeyondRecentWindow(selectedTimestamps), [selectedTimestamps])

  // Whether this bookmaker has EVER reported more than one real price, in its whole tracked
  // history - not scoped to the 72h window (a single point can't show "evolution" no matter how
  // wide the window is). Independent of hasHiddenHistory above: that one is about whether
  // toggling the window would reveal MORE of an already-multi-point history; this is about there
  // being no real history to reveal at all yet - both can be shown together (a brand-new
  // bookmaker's single point is trivially "under 72h" too) without contradicting each other.
  const hasOnlyOnePointEver = selectedPoints.length === 1

  const windowedPoints = useMemo(
    () => windowSeries(selectedPoints, showFullHistory),
    [selectedPoints, showFullHistory],
  )

  // The last successful run of THIS bookmaker's own source - see withTrailingConfirmation's own
  // Javadoc in lib/oddsWindow.ts for what this is used for.
  const lastRunAtForSelected = useMemo(() => {
    const source = selectedSlug ? dataSourceForSlug.get(selectedSlug) : undefined
    if (source === 'ODDSPAPI') return lastOddsPapiRunAt
    if (source === 'THEODDSAPI') return lastTheOddsApiRunAt
    return null
  }, [selectedSlug, dataSourceForSlug, lastOddsPapiRunAt, lastTheOddsApiRunAt])

  // Real points plus, when applicable, one synthetic trailing point extending the line to the
  // last successful check of this bookmaker's own source - fed to <LineChart data={...}> below
  // AND to flatPoints (tagged isSynthetic there, so it's hoverable but never rendered as a real
  // price - see that useMemo's own comment). decimalPlaces, xAxisTickFormatter's non-empty branch,
  // yAxisDomain and hasOnlyOnePointEver above still read windowedPoints/selectedPoints instead -
  // see withTrailingConfirmation's own Javadoc for why the synthetic point can never be mistaken
  // for a real registered price in any of those.
  const chartPoints = useMemo(
    () => withTrailingConfirmation(windowedPoints, lastRunAtForSelected),
    [windowedPoints, lastRunAtForSelected],
  )
  const hasTrailingConfirmation = chartPoints.length > windowedPoints.length

  // Built from chartPoints (real + the trailing synthetic point, if any) - not windowedPoints -
  // so the synthetic point is hoverable too, tagged isSynthetic so CursorTooltip renders it as
  // "confirmed unchanged" instead of a real price. It's always the LAST element of chartPoints
  // when present (withTrailingConfirmation only ever appends one, at the end).
  const flatPoints = useMemo(
    (): FlatPoint[] =>
      selectedSlug
        ? chartPoints.map(
            (p, i): FlatPoint => ({
              slug: selectedSlug,
              x: p.x,
              y: p.y,
              isSynthetic: hasTrailingConfirmation && i === chartPoints.length - 1,
            }),
          )
        : [],
    [chartPoints, selectedSlug, hasTrailingConfirmation],
  )

  const colorBySlug = useMemo(
    () => (selectedSlug ? new Map([[selectedSlug, LINE_COLOR]]) : new Map<string, string>()),
    [selectedSlug],
  )

  const decimalPlaces = useMemo(() => {
    if (windowedPoints.length === 0) return 2
    const values = windowedPoints.map((p) => p.y)
    const range = Math.max(...values) - Math.min(...values)
    return decimalPlacesForRange(range)
  }, [windowedPoints])

  // Based on chartPoints (real + synthetic, if any) so the tick format matches the span actually
  // drawn - a trailing confirmation stretching the line out by a day or more should be able to
  // switch the axis to a day-aware format same as any other range change would.
  const xAxisTickFormatter = useMemo(() => {
    if (chartPoints.length === 0) return xAxisTickFormatterForRange(0)
    const xs = chartPoints.map((p) => p.x)
    const rangeMs = Math.max(...xs) - Math.min(...xs)
    return xAxisTickFormatterForRange(rangeMs)
  }, [chartPoints])

  if (entries.length === 0 || selectedSlug === null) {
    return <p className="text-sm text-ink-faint">Todavía no hay histórico de cuotas para este resultado.</p>
  }

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <select
          value={selectedSlug}
          onChange={(e) => setSelectedSlug(e.target.value)}
          aria-label="Casa de apuestas"
          className="rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink-soft"
        >
          {rankedBookmakers.map(([slug, points]) => (
            <option key={slug} value={slug}>
              {bookmakerLabel(slug)} — {formatOddValue(points[points.length - 1].y, dropdownDecimalPlaces)}
            </option>
          ))}
        </select>
        <div className="flex items-center gap-2">
          <p className="text-xs text-ink-faint">
            {showFullHistory ? 'Todo el historial.' : 'Últimas 72 horas.'}
          </p>
          <button
            type="button"
            onClick={onToggleFullHistory}
            className="whitespace-nowrap rounded-md border border-border bg-surface-2 px-3 py-1.5 text-xs font-medium text-ink-soft transition-colors hover:bg-surface"
          >
            {showFullHistory ? 'Ver solo lo reciente' : 'Ver historial completo'}
          </button>
        </div>
      </div>

      <div className="h-72 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={chartPoints} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
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
              domain={yAxisDomain(windowedPoints.map((p) => p.y))}
              tickFormatter={(value: number) => formatOddValue(value, decimalPlaces)}
              stroke="var(--color-axis)"
              tick={{ fill: 'var(--color-ink-faint)', fontSize: 12 }}
              width={40}
            />
            <Line
              dataKey="y"
              name={selectedSlug}
              type="stepAfter"
              stroke={LINE_COLOR}
              strokeWidth={2}
              dot={CHART_DOT_STYLE}
              isAnimationActive={false}
            />
            <CursorTooltip points={flatPoints} colorBySlug={colorBySlug} decimalPlaces={decimalPlaces} />
          </LineChart>
        </ResponsiveContainer>
      </div>
      {hasOnlyOnePointEver &&
        (hasTrailingConfirmation ? (
          <p className="mt-1 text-xs text-ink-faint">
            Esta casa está confirmada sin cambios desde {formatDateTime(new Date(selectedPoints[0].x).toISOString())}.
          </p>
        ) : (
          <p className="mt-1 text-xs text-ink-faint">
            Esta casa apenas registró su primer precio - todavía no hay suficiente historial para ver evolución.
          </p>
        ))}
      {!showFullHistory && !hasHiddenHistory && (
        <p className="mt-1 text-xs text-ink-faint">
          Esta casa tiene menos de 72 horas de historial - &ldquo;Ver historial completo&rdquo; no cambiará nada.
        </p>
      )}
    </div>
  )
}
