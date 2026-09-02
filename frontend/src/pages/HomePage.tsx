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
 * reads as "a preview of what those real cards show", not a separate landing-page aesthetic.
 */
export function HomePage() {
  return (
    <Layout>
      <section className="mb-10 flex flex-col items-center px-2 pt-4 text-center sm:pt-8">
        <div className="mb-3 flex items-center gap-2">
          <span className="inline-block h-3 w-3 rounded-full bg-series-1" aria-hidden="true" />
          <span className="text-2xl font-semibold text-ink sm:text-3xl">BetEdge</span>
        </div>
        <p className="max-w-xl text-sm text-ink-soft sm:text-base">
          Encuentra ventajas estadísticas reales en las cuotas de fútbol, antes de que el mercado se corrija.
        </p>
      </section>

      <section className="mb-8 rounded-xl border border-border bg-surface p-5 sm:p-6">
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
              className="rounded-full border border-border bg-surface-2 px-3 py-1 text-xs font-medium text-ink-soft"
            >
              {pill}
            </span>
          ))}
        </div>
      </section>

      <section className="mb-10 grid grid-cols-1 gap-4 lg:grid-cols-2">
        <article className="flex flex-col rounded-xl border border-border bg-surface p-5 sm:p-6">
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

        <article className="flex flex-col rounded-xl border border-border bg-surface p-5 sm:p-6">
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

      <section className="flex flex-wrap items-center justify-center gap-3 pb-4">
        <Link
          to="/dashboard"
          className="rounded-md bg-series-1 px-5 py-2.5 text-sm font-semibold text-white transition-opacity hover:opacity-90"
        >
          Ver oportunidades activas
        </Link>
        <Link
          to="/matches"
          className="rounded-md border border-border px-5 py-2.5 text-sm font-semibold text-ink-soft transition-colors hover:bg-surface-2 hover:text-ink"
        >
          Ver partidos
        </Link>
      </section>
    </Layout>
  )
}
