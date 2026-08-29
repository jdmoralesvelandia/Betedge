import { DAY_MS } from './oddsWindow'

/**
 * Shared between OddsHistoryChart (comparison view) and SingleBookmakerChart (individual view) so
 * both read exactly the same decimal-precision and axis-tick rules, rather than two copies of the
 * same formatting logic drifting apart.
 */

/** Odds ranges as narrow as 1.16-1.18 round identically at 2 decimals - show more precision the narrower the range gets. */
export function decimalPlacesForRange(range: number): number {
  if (range >= 1) return 2
  if (range >= 0.1) return 3
  return 4
}

/**
 * Trims redundant trailing zeros (and a dangling decimal point) from an already-rounded fixed
 * string - "1.3000" -> "1.3", "2.0000" -> "2", "1.2050" -> "1.205". decimalPlacesForRange still
 * decides the real rounding ceiling needed to keep neighboring ticks/values distinguishable (a
 * narrow range still rounds to 4 decimals before this ever runs) - trimming only removes zero
 * characters that were never carrying information in the first place, so it can never make two
 * already-distinct values collide. A tick that genuinely needs every one of those decimals to
 * differ from its neighbor (e.g. "1.2050" next to "1.2045") simply has no trailing zero to trim.
 */
export function trimTrailingZeros(fixed: string): string {
  return fixed.replace(/\.?0+$/, '')
}

export function formatOddValue(value: number, decimalPlaces: number): string {
  return trimTrailingZeros(value.toFixed(decimalPlaces))
}

/**
 * Same "adapt to what's actually visible" idea as decimalPlacesForRange, applied to the X axis:
 * a fixed day/month tickFormatter repeats the same label ("27/8, 27/8, 27/8...") once the visible
 * range collapses to a day or two, since every tick lands on the same calendar date.
 */
export function xAxisTickFormatterForRange(rangeMs: number): (value: number) => string {
  if (rangeMs >= 7 * DAY_MS) {
    return (value) => new Date(value).toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit' })
  }
  if (rangeMs >= DAY_MS) {
    return (value) => {
      const date = new Date(value)
      const dayMonth = date.toLocaleDateString('es-ES', { day: 'numeric', month: 'numeric' })
      const time = date.toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })
      return `${dayMonth} ${time}`
    }
  }
  return (value) => new Date(value).toLocaleTimeString('es-ES', { hour: '2-digit', minute: '2-digit' })
}

/**
 * Dot radius/stroke shared by both chart views (bumped up from Recharts' default-ish 2.5px) so
 * every real data point reads clearly on its own, not just as a bend in the line - and so the two
 * views feel visually consistent with each other rather than one looking like an afterthought.
 */
export const CHART_DOT_STYLE = { r: 4, strokeWidth: 2 }

/**
 * Y-axis domain for whatever values are currently visible. Recharts' own `domain={['auto','auto']}`
 * degenerates when every visible value is identical (one real point, or several that all happen to
 * agree) - min equals max, and Recharts falls back to some unrelated padded range instead (e.g. a
 * flat 0-4 span around a single real ~2.65 point - confirmed 2026-08-28 against TSG Hoffenheim vs
 * Borussia Dortmund / Tipico, a genuinely single-point series). `['auto','auto']` is kept for the
 * normal case (real variance) since that already works well across this whole project's testing -
 * this only steps in for the degenerate one, with a small proportional pad (never zero, so a flat
 * line doesn't render as a single pixel-thin strip) and a floor at 0 (an odd value is never negative).
 */
export function yAxisDomain(values: number[]): [number | 'auto', number | 'auto'] {
  if (values.length === 0) return ['auto', 'auto']
  const min = Math.min(...values)
  const max = Math.max(...values)
  if (min !== max) return ['auto', 'auto']
  const padding = Math.max(min * 0.05, 0.05)
  return [Math.max(0, min - padding), max + padding]
}
