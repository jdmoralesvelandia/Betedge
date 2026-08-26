import { useMemo } from 'react'
import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { OddsHistoryEntryDto } from '../api/types'
import { bookmakerLabel } from '../lib/format'

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

interface Point {
  x: number
  y: number
}

interface TooltipPayloadEntry {
  color?: string
  name?: string
  value?: number
}

/** Odds ranges as narrow as 1.16-1.18 round identically at 2 decimals - show more precision the narrower the range gets. */
function decimalPlacesForRange(range: number): number {
  if (range >= 1) return 2
  if (range >= 0.1) return 3
  return 4
}

function ChartTooltip({
  active,
  payload,
  label,
  decimalPlaces,
}: {
  active?: boolean
  payload?: TooltipPayloadEntry[]
  label?: number
  decimalPlaces: number
}) {
  if (!active || !payload || payload.length === 0 || label === undefined) return null

  return (
    <div className="rounded-md border border-border bg-surface-2 px-3 py-2 text-xs shadow-lg">
      <p className="mb-1 text-ink-faint">{new Date(label).toLocaleString('es-ES')}</p>
      {payload.map((entry) => (
        <p key={entry.name} className="flex items-center gap-1.5 text-ink">
          <span className="inline-block h-2 w-2 rounded-full" style={{ backgroundColor: entry.color }} />
          {entry.name}: <span className="font-medium">{entry.value?.toFixed(decimalPlaces)}</span>
        </p>
      ))}
    </div>
  )
}

export function OddsHistoryChart({ entries }: { entries: OddsHistoryEntryDto[] }) {
  const bookmakerSeries = useMemo(() => {
    const byBookmaker = new Map<string, Point[]>()
    for (const entry of entries) {
      const points = byBookmaker.get(entry.bookmakerSlug) ?? []
      points.push({ x: new Date(entry.timestamp).getTime(), y: entry.oddValue })
      byBookmaker.set(entry.bookmakerSlug, points)
    }
    for (const points of byBookmaker.values()) {
      points.sort((a, b) => a.x - b.x)
    }
    return [...byBookmaker.entries()]
      .sort((a, b) => b[1].length - a[1].length)
      .slice(0, MAX_SERIES)
  }, [entries])

  // Based on the visible series only (post-slice), not all of `entries` - a bookmaker cut off by
  // MAX_SERIES shouldn't be able to widen the axis range for the ones actually drawn.
  const decimalPlaces = useMemo(() => {
    const visibleValues = bookmakerSeries.flatMap(([, points]) => points.map((p) => p.y))
    if (visibleValues.length === 0) return 2
    const range = Math.max(...visibleValues) - Math.min(...visibleValues)
    return decimalPlacesForRange(range)
  }, [bookmakerSeries])

  if (entries.length === 0) {
    return <p className="text-sm text-ink-faint">Todavía no hay histórico de cuotas para este resultado.</p>
  }

  return (
    <div className="h-72 w-full">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="var(--color-grid)" vertical={false} />
          <XAxis
            dataKey="x"
            type="number"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(value: number) =>
              new Date(value).toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit' })
            }
            stroke="var(--color-axis)"
            tick={{ fill: 'var(--color-ink-faint)', fontSize: 12 }}
          />
          <YAxis
            type="number"
            domain={['auto', 'auto']}
            tickFormatter={(value: number) => value.toFixed(decimalPlaces)}
            stroke="var(--color-axis)"
            tick={{ fill: 'var(--color-ink-faint)', fontSize: 12 }}
            width={40}
          />
          <Tooltip content={<ChartTooltip decimalPlaces={decimalPlaces} />} />
          <Legend
            wrapperStyle={{ fontSize: 12, color: 'var(--color-ink-soft)' }}
            formatter={(value: string) => bookmakerLabel(value)}
          />
          {bookmakerSeries.map(([slug, points], index) => (
            <Line
              key={slug}
              data={points}
              dataKey="y"
              name={slug}
              type="stepAfter"
              stroke={SERIES_COLORS[index % MAX_SERIES]}
              strokeWidth={2}
              dot={{ r: 2.5 }}
              activeDot={{ r: 4 }}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}
