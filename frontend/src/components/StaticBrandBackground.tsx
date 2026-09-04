/**
 * Static (never-animated) dark variant of HomePage/LoginPage's own atmospheric brand glow -
 * reuses the exact same mechanism (two blurred radial-gradients, fixed full-viewport, -z-10) but
 * deliberately toned down on two axes at once:
 *  - color: --color-brand-dark instead of --color-brand/--color-brand-2 - the same dark, less
 *    saturated tint SegmentedControl's own container already uses (see its comment in index.css),
 *    one hue instead of two for a calmer, more monochrome wash.
 *  - motion: no --animate-breathe class at all, not even behind motion-safe: - this variant must
 *    never animate, full stop, so there is nothing to gate behind prefers-reduced-motion in the
 *    first place.
 * Peak intensity (the color-mix percentages below) is deliberately lower than HomePage/LoginPage's
 * own 38%/30% - checked against the app's actual text colors sitting directly on bare page
 * background (the only place this can matter; every card/table/pill elsewhere is fully opaque and
 * blocks it completely): text-ink-faint over this gradient's brightest point still clears 5:1,
 * above the 4.5:1 AA floor with real margin, and every other text token in the app is lighter than
 * text-ink-faint so clears by more.
 *
 * Used on Dashboard and Partidos only. MatchDetailPage and AdminPage deliberately keep the
 * original flat dark background instead of picking this up too - not an oversight, just scoped
 * that way: those two are dense/interactive-heavy pages (charts, forms, tables) where a wash
 * behind the content buys nothing, so they aren't wired up to this component at all.
 */
export function StaticBrandBackground() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none fixed inset-0 -z-10 blur-[80px]"
      style={{
        background:
          'radial-gradient(circle at 18% 12%, color-mix(in srgb, var(--color-brand-dark) 30%, transparent), transparent 55%),' +
          'radial-gradient(circle at 82% 70%, color-mix(in srgb, var(--color-brand-dark) 22%, transparent), transparent 60%)',
      }}
    />
  )
}
