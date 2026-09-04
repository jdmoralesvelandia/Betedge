import { useEffect, useState } from 'react'
import { EmptyState } from './EmptyState'
import { useAuth } from '../auth/AuthContext'
import type { Fetcher } from '../api/endpoints'
import type { IngestionRunDto } from '../api/types'
import { formatRelativeToNow } from '../lib/format'

interface IngestionRunPanelProps {
  title: string
  description: string
  buttonLabel: string
  fetchLastRun: (fetcher: Fetcher) => Promise<IngestionRunDto | undefined>
  trigger: (fetcher: Fetcher) => Promise<IngestionRunDto>
}

/** One provider's manual-trigger control + run summary - reused for OddsPapi and The Odds API on the Admin page. */
export function IngestionRunPanel({ title, description, buttonLabel, fetchLastRun, trigger }: IngestionRunPanelProps) {
  const { apiFetch } = useAuth()
  const [lastRun, setLastRun] = useState<IngestionRunDto | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [triggering, setTriggering] = useState(false)

  useEffect(() => {
    let cancelled = false
    fetchLastRun(apiFetch)
      .then((run) => {
        if (!cancelled) setLastRun(run ?? null)
      })
      .catch(() => {
        if (!cancelled) setError('No se pudo cargar el resumen de la última corrida.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleTrigger() {
    setTriggering(true)
    setError(null)
    try {
      const run = await trigger(apiFetch)
      setLastRun(run)
    } catch {
      setError('La ingesta falló. Revisa los logs del backend.')
    } finally {
      setTriggering(false)
    }
  }

  return (
    <section className="mb-10">
      <h2 className="mb-1 text-lg font-semibold text-ink">{title}</h2>
      <p className="mb-4 text-sm text-ink-faint">{description}</p>

      {error && (
        <p className="mb-4 rounded-md bg-critical-soft px-3 py-2 text-sm text-critical" role="alert">
          {error}
        </p>
      )}

      <div className="mb-4">
        {loading ? (
          <p className="text-sm text-ink-faint">Cargando resumen…</p>
        ) : lastRun ? (
          <div className="rounded-xl border border-border bg-surface p-5">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              <h3 className="text-sm font-semibold text-ink">Resumen de la corrida</h3>
              <div className="flex items-center gap-2 text-xs text-ink-faint">
                <span className="rounded-full bg-surface-2 px-2 py-0.5 font-medium text-ink-soft">
                  {lastRun.triggeredBy === 'MANUAL' ? 'Manual' : 'Programada'}
                </span>
                <span>
                  Última actualización: <span className="text-ink-soft">{formatRelativeToNow(lastRun.finishedAt)}</span>
                </span>
              </div>
            </div>

            <div className="mb-5 grid grid-cols-2 gap-3 sm:grid-cols-4">
              <Stat label="Value bets" value={lastRun.valueBetsDetected} />
              <Stat label="Surebets" value={lastRun.surebetsDetected} />
              <Stat label="Eventos recibidos" value={lastRun.totalEventsReceived} />
              <Stat label="Matches nuevos" value={lastRun.totalNewMatches} />
            </div>

            <table className="w-full border-collapse text-left text-sm">
              <thead>
                <tr className="border-b border-border text-xs uppercase tracking-wide text-ink-faint">
                  <th className="py-2 pr-2 font-medium">Competición</th>
                  <th className="py-2 px-2 font-medium">Eventos</th>
                  <th className="py-2 px-2 font-medium">Matches nuevos</th>
                  <th className="py-2 pl-2 font-medium">Odds nuevas</th>
                </tr>
              </thead>
              <tbody>
                {lastRun.competitionBreakdown.map((result) => (
                  <tr key={result.competitionName} className="border-b border-border last:border-0">
                    <td className="py-2 pr-2 text-ink">{result.competitionName}</td>
                    <td className="py-2 px-2 text-ink-soft">{result.eventsReceived}</td>
                    <td className="py-2 px-2 text-ink-soft">{result.newMatches}</td>
                    <td className="py-2 pl-2 text-ink-soft">{result.newOdds}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : !error ? (
          <EmptyState
            icon="calm"
            title="Todavía no hay ninguna corrida de ingesta"
            description="Dispara la primera manualmente con el botón de abajo, o espera al próximo ciclo programado."
          />
        ) : null}
      </div>

      <button
        type="button"
        onClick={() => void handleTrigger()}
        disabled={triggering}
        className="rounded-md bg-brand px-4 py-2 text-sm font-semibold text-white transition-colors hover:bg-brand-2 disabled:opacity-50"
      >
        {triggering ? 'Ejecutando ingesta…' : buttonLabel}
      </button>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-surface-2 px-3 py-2.5">
      <p className="text-xs text-ink-faint">{label}</p>
      <p className="text-xl font-semibold text-ink">{value}</p>
    </div>
  )
}
