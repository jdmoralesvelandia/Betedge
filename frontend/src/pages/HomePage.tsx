import { Link } from 'react-router-dom'
import { Layout } from '../components/Layout'

const FEATURE_PILLS = [
  '2 proveedores de cuotas',
  '6 ligas de fútbol',
  'Consenso de mercado en tiempo real',
  'Gráficas de evolución de cuotas',
]

/**
 * The "Inicio" landing page - what a session sees right after logging in, before Dashboard's own
 * live opportunity feed. Explains the two concepts everything else in the app assumes the reader
 * already knows (value bet, surebet) with a small worked example each, using invented-but-
 * internally-consistent numbers (never real match data) purely for illustration - same visual
 * language as ValueBetCard/SurebetCard (bg-good-soft edge badge, surface-2 rows) so the example
 * reads as "a preview of what those real cards show", not a separate landing-page aesthetic - the
 * edge-badge shape below is deliberately NOT part of this page's own redesign, for that reason.
 *
 * Redesign pilot (2026-09-03): violet/purple brand accent + atmospheric glow + staggered entrance,
 * scoped to THIS page and LoginPage only - see index.css's own comment on --color-brand/
 * --animate-fade-up/--animate-breathe for why those are additive tokens, never a replacement for
 * what Layout/Dashboard/MatchesPage/MatchDetailPage/AdminPage already use. All motion is gated
 * behind Tailwind's motion-safe: variant (prefers-reduced-motion: no-preference) - under reduced
 * motion, every motion-safe:-prefixed class simply never applies, so content renders at its final
 * position/opacity immediately, animation-free.
 */
export function HomePage() {
  return (
    <Layout>
      {/* Fixed so it covers the viewport regardless of scroll/nesting inside <main>, -z-10 so it
          paints behind all normal-flow content (including Layout's own opaque header) without
          needing z-index on anything else. aria-hidden - purely decorative. */}
      <div
        aria-hidden="true"
        className="pointer-events-none fixed inset-0 -z-10 blur-[80px] motion-safe:animate-breathe"
        style={{
          background:
            'radial-gradient(circle at 18% 12%, color-mix(in srgb, var(--color-brand) 38%, transparent), transparent 55%),' +
            'radial-gradient(circle at 82% 70%, color-mix(in srgb, var(--color-brand-2) 30%, transparent), transparent 60%)',
        }}
      />

      <section className="mb-10 flex flex-col items-center px-2 pt-4 text-center sm:pt-8">
        <div
          className="mb-3 flex items-center gap-2 motion-safe:opacity-0 motion-safe:animate-fade-up"
          style={{ animationDelay: '0ms' }}
        >
          <span
            className="inline-block h-3 w-3 rounded-full bg-brand shadow-[0_0_16px_var(--color-brand)]"
            aria-hidden="true"
          />
          {/* pb-[0.15em]: background-clip: text paints the gradient only inside the element's own
              box, sized by line-height - text-4xl/text-6xl's line-height:1 is tighter than this
              bold weight's real glyph extent, so without this the "g"'s descender falls outside
              that box and renders with no gradient color (looks visually cut off). An inline
              element's own vertical padding doesn't affect line layout/height, so this only grows
              the background-painting area downward - confirmed it doesn't shift the dot's
              alignment (both sit inside the same items-center row, sized by line-height, not by
              this padding). em-based so it scales with sm:text-6xl too. */}
          <span className="bg-gradient-to-r from-brand to-brand-2 bg-clip-text pb-[0.15em] text-4xl font-extrabold tracking-tight text-transparent sm:text-6xl">
            BetEdge
          </span>
        </div>
        <p
          className="max-w-xl text-sm text-ink-soft motion-safe:opacity-0 motion-safe:animate-fade-up sm:text-base"
          style={{ animationDelay: '80ms' }}
        >
          Encuentra ventajas estadísticas reales en las cuotas de fútbol, antes de que el mercado se corrija.
        </p>
      </section>

      <section
        className="mb-8 rounded-2xl border border-border bg-surface p-5 motion-safe:opacity-0 motion-safe:animate-fade-up sm:p-6"
        style={{ animationDelay: '160ms' }}
      >
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-faint">¿Qué es BetEdge?</h2>
        <p className="text-sm leading-relaxed text-ink-soft sm:text-base">
          Un sistema que agrega cuotas de dos proveedores (OddsPapi y The Odds API) para 6 ligas de fútbol -
          Premier League, La Liga, Serie A, Bundesliga, Ligue 1 y Champions League - calcula el consenso del
          mercado en tiempo real, y detecta automáticamente <span className="text-ink">value bets</span> y{' '}
          <span className="text-ink">surebets</span>. Cada partido tiene además su propia gráfica de evolución
          de cuotas por casa de apuestas, para ver cómo se movió el mercado antes del pitazo inicial.
        </p>
        <div className="mt-5 flex flex-wrap gap-2">
          {FEATURE_PILLS.map((pill) => (
            <span
              key={pill}
              className="rounded-full border border-brand/30 bg-brand/10 px-3 py-1 text-xs font-medium text-ink-soft transition-colors duration-300 hover:border-brand/60 hover:text-ink"
            >
              {pill}
            </span>
          ))}
        </div>
      </section>

      <section className="mb-10 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <article
          className="flex flex-col rounded-2xl border border-border bg-surface p-5 transition-all duration-300 motion-safe:opacity-0 motion-safe:animate-fade-up motion-safe:hover:-translate-y-1 hover:border-brand/40 hover:shadow-[0_0_40px_-12px_var(--color-brand)] sm:p-6"
          style={{ animationDelay: '240ms' }}
        >
          <h3 className="mb-2 flex items-center gap-2 text-base font-semibold text-ink">
            <span className="inline-block h-2 w-2 rounded-full bg-good" aria-hidden="true" />
            ¿Qué es un Value Bet?
          </h3>
          <p className="mb-4 flex-1 text-sm leading-relaxed text-ink-soft">
            El porcentaje verde es el <span className="text-ink">edge</span>: cuánto mejor es la cuota que ofrece
            una casa, comparado con lo que el consenso del mercado dice que debería valer. A cada casa le
            quitamos su margen de ganancia y promediamos esas probabilidades limpias entre todas para estimar
            la probabilidad real. Si una casa específica se despega de ese consenso hacia arriba, esa diferencia
            es el edge - la ventaja estadística teórica de apostar ahí. No es una ganancia garantizada: es una
            apuesta con expectativa matemática a favor, asumiendo que el consenso tiene razón.
          </p>
          <div className="rounded-lg border border-border bg-surface-2 p-4">
            <p className="mb-3 text-xs uppercase tracking-wide text-ink-faint">Ejemplo</p>
            <div className="flex items-center justify-between gap-3 text-sm">
              <div>
                <p className="text-ink-faint">Consenso del mercado</p>
                <p className="font-medium text-ink">
                  45.0% <span className="font-normal text-ink-faint">(cuota justa ≈ 2.22)</span>
                </p>
              </div>
              <span className="text-ink-faint">vs</span>
              <div className="text-right">
                <p className="text-ink-faint">Betano ofrece</p>
                <p className="font-medium text-ink">2.40</p>
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between border-t border-border pt-3">
              <span className="text-xs text-ink-faint">edge</span>
              <span className="rounded-md bg-good-soft px-2 py-0.5 text-base font-semibold text-good">+8.0%</span>
            </div>
          </div>
        </article>

        <article
          className="flex flex-col rounded-2xl border border-border bg-surface p-5 transition-all duration-300 motion-safe:opacity-0 motion-safe:animate-fade-up motion-safe:hover:-translate-y-1 hover:border-brand/40 hover:shadow-[0_0_40px_-12px_var(--color-brand)] sm:p-6"
          style={{ animationDelay: '320ms' }}
        >
          <h3 className="mb-2 flex items-center gap-2 text-base font-semibold text-ink">
            <span className="inline-block h-2 w-2 rounded-full bg-good" aria-hidden="true" />
            ¿Qué es un Surebet?
          </h3>
          <p className="mb-4 flex-1 text-sm leading-relaxed text-ink-soft">
            Un surebet es distinto - ahí sí es <span className="text-ink">ganancia garantizada</span>, sin
            importar el resultado del partido. Pasa cuando, combinando las mejores cuotas de distintas casas
            para cada resultado posible (local, empate, visitante), lo que necesitas apostar en cada una suma
            menos del 100%. Repartiendo tu dinero entre las tres, ganas sin importar quién gane.
          </p>
          <div className="rounded-lg border border-border bg-surface-2 p-4">
            <p className="mb-3 text-xs uppercase tracking-wide text-ink-faint">Ejemplo</p>
            <div className="grid grid-cols-1 gap-1.5 sm:grid-cols-3">
              <div className="rounded-md bg-surface px-2.5 py-1.5 text-xs">
                <p className="text-ink-soft">
                  Local <span className="text-ink-faint">· Casa A</span>
                </p>
                <p className="font-medium text-ink">2.20</p>
                <p className="text-ink-faint">Apostar 48%</p>
              </div>
              <div className="rounded-md bg-surface px-2.5 py-1.5 text-xs">
                <p className="text-ink-soft">
                  Empate <span className="text-ink-faint">· Casa B</span>
                </p>
                <p className="font-medium text-ink">3.75</p>
                <p className="text-ink-faint">Apostar 28%</p>
              </div>
              <div className="rounded-md bg-surface px-2.5 py-1.5 text-xs">
                <p className="text-ink-soft">
                  Visitante <span className="text-ink-faint">· Casa C</span>
                </p>
                <p className="font-medium text-ink">4.60</p>
                <p className="text-ink-faint">Apostar 23%</p>
              </div>
            </div>
            <div className="mt-3 flex items-center justify-between border-t border-border pt-3">
              <span className="text-xs text-ink-faint">ganancia garantizada</span>
              <span className="rounded-md bg-good-soft px-2 py-0.5 text-base font-semibold text-good">+6.5%</span>
            </div>
          </div>
        </article>
      </section>

      <section
        className="flex flex-wrap items-center justify-center gap-3 pb-4 motion-safe:opacity-0 motion-safe:animate-fade-up"
        style={{ animationDelay: '400ms' }}
      >
        <Link
          to="/dashboard"
          className="rounded-full bg-gradient-to-r from-brand to-brand-2 px-6 py-2.5 text-sm font-semibold text-white transition-all duration-300 motion-safe:hover:-translate-y-0.5 hover:shadow-[0_0_30px_-6px_var(--color-brand)]"
        >
          Ver oportunidades activas
        </Link>
        <Link
          to="/matches"
          className="rounded-full border border-border px-6 py-2.5 text-sm font-semibold text-ink-soft transition-all duration-300 motion-safe:hover:-translate-y-0.5 hover:border-brand/50 hover:text-ink"
        >
          Ver partidos
        </Link>
      </section>
    </Layout>
  )
}
