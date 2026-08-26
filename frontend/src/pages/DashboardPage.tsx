import { useEffect, useState } from 'react'
import { Layout } from '../components/Layout'
import { EmptyState } from '../components/EmptyState'
import { LastUpdatedBadge } from '../components/LastUpdatedBadge'
import { ValueBetCard } from '../components/ValueBetCard'
import { SurebetCard } from '../components/SurebetCard'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import type { SurebetDto, ValueBetDto } from '../api/types'

function byStartTimeAscending(a: { startTime: string }, b: { startTime: string }): number {
  return new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
}

export function DashboardPage() {
  const { apiFetch } = useAuth()
  const [valueBets, setValueBets] = useState<ValueBetDto[] | null>(null)
  const [surebets, setSurebets] = useState<SurebetDto[] | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const [vb, sb, last] = await Promise.all([
          endpoints.activeValueBets(apiFetch),
          endpoints.activeSurebets(apiFetch),
          endpoints.lastUpdated(apiFetch),
        ])
        if (cancelled) return
        setValueBets(vb)
        setSurebets(sb)
        setLastUpdated(last.lastOddsTimestamp)
      } catch {
        if (!cancelled) setError('No se pudieron cargar las oportunidades. Intenta recargar la página.')
      }
    }

    void load()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const loading = valueBets === null || surebets === null
  const hasNothing = !loading && valueBets.length === 0 && surebets.length === 0

  const sortedValueBets = valueBets ? [...valueBets].sort(byStartTimeAscending) : []
  const sortedSurebets = surebets ? [...surebets].sort(byStartTimeAscending) : []

  return (
    <Layout>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-ink">Oportunidades activas</h1>
          <p className="text-sm text-ink-faint">Value bets y surebets detectados en el ciclo de ingesta más reciente.</p>
        </div>
        <LastUpdatedBadge timestamp={lastUpdated} />
      </div>

      {error && (
        <p className="mb-6 rounded-md bg-critical-soft px-3 py-2 text-sm text-critical" role="alert">
          {error}
        </p>
      )}

      {loading && !error && <p className="text-sm text-ink-faint">Cargando oportunidades…</p>}

      {hasNothing && (
        <EmptyState
          icon="calm"
          title="No hay oportunidades activas ahora mismo"
          description="Puede deberse a que las ligas seguidas están en descanso (sin partidos próximos) o a que, con los datos actuales, ninguna casa se aleja lo suficiente del consenso de mercado. Vuelve a revisar después del próximo ciclo de ingesta."
        />
      )}

      {!loading && valueBets.length > 0 && (
        <section className="mb-8">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-faint">
            Value bets ({valueBets.length})
          </h2>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {sortedValueBets.map((vb) => (
              <ValueBetCard key={vb.id} valueBet={vb} />
            ))}
          </div>
        </section>
      )}

      {!loading && surebets.length > 0 && (
        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-faint">
            Surebets ({surebets.length})
          </h2>
          <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
            {sortedSurebets.map((sb) => (
              <SurebetCard key={sb.id} surebet={sb} />
            ))}
          </div>
        </section>
      )}
    </Layout>
  )
}
