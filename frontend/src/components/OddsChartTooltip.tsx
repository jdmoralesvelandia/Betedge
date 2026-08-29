import { type MouseEvent as ReactMouseEvent, useState } from 'react'
import { usePlotArea, useXAxisScale, useYAxisScale } from 'recharts'
import { bookmakerLabel } from '../lib/format'
import { formatOddValue } from '../lib/oddsChartFormat'

/**
 * Shared by OddsHistoryChart (comparison view, many series) and SingleBookmakerChart (individual
 * view, one series) - the grouping/distance logic below works unchanged for either: with a single
 * series there's simply never another point to group with, so it degrades to exactly the plain
 * one-row tooltip SingleBookmakerChart needs, with no special-casing required.
 */
export interface FlatPoint {
  slug: string
  x: number
  y: number
}

/** How close (in real screen pixels) the cursor has to be to a point before it counts as "hovering" it. */
export const HOVER_HIT_RADIUS_PX = 25

/**
 * How close (in real screen pixels) another point has to be to the WINNING point before it counts
 * as "the same spot" and gets grouped into the same tooltip - e.g. several bookmakers reporting in
 * the same real ingestion cycle, at (or extremely near) the same timestamp and price, which render
 * on top of or immediately next to each other regardless of how far apart their actual values are.
 */
export const GROUP_HIT_RADIUS_PX = 4

export interface NearestPointGroup {
  /** The single closest point to the cursor - the one that actually qualified this spot as "hovered" (see maxDistance). */
  winner: FlatPoint
  /** Every point within GROUP_HIT_RADIUS_PX of the winner's pixel position, alphabetically by real name - always includes the winner itself, so length is always >= 1. */
  points: FlatPoint[]
  pixelX: number
  pixelY: number
  /** The winner's own distance to the cursor. */
  distance: number
}

/**
 * Pure and React-free on purpose, so it's directly unit-testable without a DOM: given the cursor's
 * pixel position and a scale function per axis (data value -> pixel, exactly what Recharts'
 * useXAxisScale/useYAxisScale return), finds the single closest point across ALL points by real 2D
 * Euclidean distance - never just "closest in time" (X only). That X-only nearness is effectively
 * what Recharts' own shared Tooltip falls back to once its exact-timestamp match fails (see
 * OddsHistoryChart's PROBLEMA 3 history - the original diagnosis, before this file existed as its
 * own module) - replacing it entirely is the point of this function existing.
 *
 * Once that single winner is found, a second pass collects every OTHER point FROM A DIFFERENT
 * SERIES within GROUP_HIT_RADIUS_PX of the winner's own pixel position - not of the cursor - so
 * multiple bookmakers stacked at (or effectively at) the same spot all show up in one tooltip
 * instead of only whichever one happened to be marginally closer to the cursor. "From a different
 * series" is deliberate: a single bookmaker can have two of its OWN real points close enough in
 * time to both land inside groupRadius (confirmed against real data - Pinnacle on match 139 has
 * two such points ~0.1px apart at this chart's zoom level), and showing "Pinnacle" twice in one
 * tooltip would be redundant, not a second house agreeing on the price - only the single point per
 * slug closest to the winner is kept.
 *
 * Returns null when nothing is within maxDistance, so the cursor never "snaps" to a point that's
 * actually far away just because it happened to be the least-bad option in the whole dataset.
 */
export function findNearestPoint(
  points: FlatPoint[],
  cursor: { x: number; y: number },
  xScale: (value: number) => number | undefined,
  yScale: (value: number) => number | undefined,
  maxDistance: number = HOVER_HIT_RADIUS_PX,
  groupRadius: number = GROUP_HIT_RADIUS_PX,
): NearestPointGroup | null {
  const projected: { point: FlatPoint; pixelX: number; pixelY: number }[] = []
  let best: { point: FlatPoint; pixelX: number; pixelY: number; distance: number } | null = null
  for (const point of points) {
    const pixelX = xScale(point.x)
    const pixelY = yScale(point.y)
    if (pixelX == null || pixelY == null) continue
    projected.push({ point, pixelX, pixelY })
    const dx = pixelX - cursor.x
    const dy = pixelY - cursor.y
    const distance = Math.sqrt(dx * dx + dy * dy)
    if (best === null || distance < best.distance) {
      best = { point, pixelX, pixelY, distance }
    }
  }
  if (best === null || best.distance > maxDistance) return null

  const winner = best
  const withinGroupRadius = projected
    .map((p) => {
      const dx = p.pixelX - winner.pixelX
      const dy = p.pixelY - winner.pixelY
      return { point: p.point, distanceToWinner: Math.sqrt(dx * dx + dy * dy) }
    })
    .filter((p) => p.distanceToWinner <= groupRadius)
    .sort((a, b) => a.distanceToWinner - b.distanceToWinner)

  // One row per bookmaker, max - keep only the point closest to the winner for any slug that
  // shows up more than once (see the Javadoc above for why).
  const seenSlugs = new Set<string>()
  const groupedPoints: FlatPoint[] = []
  for (const p of withinGroupRadius) {
    if (seenSlugs.has(p.point.slug)) continue
    seenSlugs.add(p.point.slug)
    groupedPoints.push(p.point)
  }
  groupedPoints.sort((a, b) => bookmakerLabel(a.slug).localeCompare(bookmakerLabel(b.slug), 'es'))

  return {
    winner: winner.point,
    points: groupedPoints,
    pixelX: winner.pixelX,
    pixelY: winner.pixelY,
    distance: winner.distance,
  }
}

/**
 * Own hover tooltip, replacing Recharts' <Tooltip> entirely (see findNearestPoint's own Javadoc
 * above for why). Rendered as a plain child of <LineChart> - Recharts 3.x supports this directly
 * ("all charts are able to render arbitrary elements anywhere", <Customized> is deprecated for
 * exactly this reason), so useXAxisScale/useYAxisScale/usePlotArea all work here via the same
 * chart context <LineChart> provides to XAxis/YAxis/Line.
 *
 * Tracks the cursor itself (onMouseMove on a transparent hit-target <rect> covering the plot
 * area, converted to the SVG's own coordinate space via getScreenCTM - the standard, viewBox/
 * transform-safe way to do this, rather than assuming any particular CSS pixel <-> SVG unit
 * ratio) instead of reading Recharts' own activeIndex/activeLabel, which is exactly the
 * mechanism that was producing wrong values in the first place.
 */
export function CursorTooltip({
  points,
  colorBySlug,
  decimalPlaces,
}: {
  points: FlatPoint[]
  colorBySlug: Map<string, string>
  decimalPlaces: number
}) {
  const xScale = useXAxisScale()
  const yScale = useYAxisScale()
  const plotArea = usePlotArea()
  const [cursor, setCursor] = useState<{ x: number; y: number } | null>(null)

  if (!xScale || !yScale || !plotArea) return null

  const nearest = cursor ? findNearestPoint(points, cursor, xScale, yScale) : null

  function handleMouseMove(e: ReactMouseEvent<SVGRectElement>) {
    const target = e.currentTarget
    const svg = target.ownerSVGElement
    const ctm = target.getScreenCTM()
    if (!svg || !ctm) return
    const svgPoint = svg.createSVGPoint()
    svgPoint.x = e.clientX
    svgPoint.y = e.clientY
    const local = svgPoint.matrixTransform(ctm.inverse())
    setCursor({ x: local.x, y: local.y })
  }

  const BOX_WIDTH = 190
  const ROW_HEIGHT = 16
  const TOP_PADDING = 10
  const TIMESTAMP_ROW_HEIGHT = 18
  const BOX_MARGIN = 10
  let boxX = 0
  let boxY = 0
  let boxHeight = 0
  let anchorColor = 'var(--color-ink)'
  if (nearest) {
    boxHeight = TOP_PADDING + nearest.points.length * ROW_HEIGHT + TIMESTAMP_ROW_HEIGHT
    anchorColor = colorBySlug.get(nearest.winner.slug) ?? anchorColor
    boxX = nearest.pixelX + BOX_MARGIN
    if (boxX + BOX_WIDTH > plotArea.x + plotArea.width) {
      boxX = nearest.pixelX - BOX_MARGIN - BOX_WIDTH
    }
    boxY = nearest.pixelY - boxHeight - BOX_MARGIN
    if (boxY < plotArea.y) {
      boxY = Math.min(nearest.pixelY + BOX_MARGIN, plotArea.y + plotArea.height - boxHeight)
    }
  }

  return (
    <>
      {nearest && (
        <g pointerEvents="none">
          <circle cx={nearest.pixelX} cy={nearest.pixelY} r={5} fill="none" stroke={anchorColor} strokeWidth={1.5} />
          <rect
            x={boxX}
            y={boxY}
            width={BOX_WIDTH}
            height={boxHeight}
            rx={6}
            fill="var(--color-surface-2)"
            stroke="var(--color-border)"
          />
          {nearest.points.map((point, index) => {
            const rowColor = colorBySlug.get(point.slug) ?? 'var(--color-ink)'
            const rowBaseline = boxY + TOP_PADDING + index * ROW_HEIGHT + 10
            return (
              <g key={point.slug}>
                <circle cx={boxX + 14} cy={rowBaseline - 4} r={4} fill={rowColor} />
                <text x={boxX + 24} y={rowBaseline} fontSize={11} fontWeight={600} fill="var(--color-ink)">
                  {bookmakerLabel(point.slug)}
                </text>
                <text
                  x={boxX + BOX_WIDTH - 12}
                  y={rowBaseline}
                  fontSize={11}
                  fontWeight={600}
                  fill="var(--color-ink)"
                  textAnchor="end"
                >
                  {formatOddValue(point.y, decimalPlaces)}
                </text>
              </g>
            )
          })}
          {/* One shared timestamp for the whole group - the winner's own moment (the point that
              actually qualified this spot as "hovered", not just array position after the
              alphabetical sort above). In the overwhelming real case (a group exists at all)
              every grouped point shares that exact same real ingestion-cycle timestamp; at very
              compressed zoom levels (full multi-week history) two genuinely different moments
              could in principle render within GROUP_HIT_RADIUS_PX of each other, in which case
              this is the closest one's time, not a claim that all of them are literally
              simultaneous. */}
          <text
            x={boxX + 12}
            y={boxY + TOP_PADDING + nearest.points.length * ROW_HEIGHT + 13}
            fontSize={10}
            fill="var(--color-ink-faint)"
          >
            {new Date(nearest.winner.x).toLocaleString('es-ES')}
          </text>
        </g>
      )}
      {/* Rendered last so it paints on top and reliably captures the mouse regardless of what's underneath. */}
      <rect
        x={plotArea.x}
        y={plotArea.y}
        width={plotArea.width}
        height={plotArea.height}
        fill="transparent"
        onMouseMove={handleMouseMove}
        onMouseLeave={() => setCursor(null)}
      />
    </>
  )
}
