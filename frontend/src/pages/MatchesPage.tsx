import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Layout } from '../components/Layout'
import { EmptyState } from '../components/EmptyState'
import { LastUpdatedBadge } from '../components/LastUpdatedBadge'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import type { CompetitionDto, MatchDto, MatchStatus } from '../api/types'
import { formatDateTime } from '../lib/format'

const SEARCH_DEBOUNCE_MS = 300

const STATUS_BADGE: Record<MatchStatus, { label: string; className: string }> = {
  SCHEDULED: { label: 'Próximo', className: 'bg-surface-2 text-ink-soft' },
  LIVE: { label: 'En vivo', className: 'bg-warning-soft text-warning' },
  FINISHED: { label: 'Finalizado', className: 'bg-surface-2 text-ink-faint' },
}

export function MatchesPage() {
  const { apiFetch } = useAuth()
  const [matches, setMatches] = useState<MatchDto[] | null>(null)
  const [competitions, setCompetitions] = useState<CompetitionDto[]>([])
  const [lastUpdated, setLastUpdated] = useState<string | null>(null)
  const [competitionId, setCompetitionId] = useState<number | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [error, setError] = useState<string | null>(null)

  const hasActiveFilters = competitionId !== null || debouncedSearch.trim() !== ''

  useEffect(() => {
    let cancelled = false
    Promise.all([endpoints.competitions(apiFetch), endpoints.lastUpdated(apiFetch)])
      .then(([comps, last]) => {
        if (cancelled) return
        setCompetitions(comps)
        setLastUpdated(last.lastOddsTimestamp)
      })
      .catch(() => {
        if (!cancelled) setError('No se pudo cargar la información de partidos.')
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const timeout = setTimeout(() => setDebouncedSearch(searchInput), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timeout)
  }, [searchInput])

  useEffect(() => {
    let cancelled = false
    endpoints
      .matches(apiFetch, { competitionId: competitionId ?? undefined, search: debouncedSearch.trim() || undefined })
      .then((data) => {
        if (!cancelled) setMatches(data)
      })
      .catch(() => {
        if (!cancelled) setError('No se pudieron cargar los partidos. Intenta recargar la página.')
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [competitionId, debouncedSearch])

  return (
    <Layout>
      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-ink">Partidos</h1>
          <p className="text-sm text-ink-faint">
            Todos los partidos ingeridos, tengan o no una oportunidad activa en este momento.
          </p>
        </div>
        <LastUpdatedBadge timestamp={lastUpdated} />
      </div>

      <div className="mb-6 flex flex-wrap items-center gap-3">
        <select
          value={competitionId ?? ''}
          onChange={(e) => setCompetitionId(e.target.value ? Number(e.target.value) : null)}
          aria-label="Liga"
          className="rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink-soft"
        >
          <option value="">Todas las ligas</option>
          {competitions.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
        <input
          type="text"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Buscar equipo…"
          aria-label="Buscar equipo"
          className="min-w-0 flex-1 rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink placeholder:text-ink-faint sm:max-w-xs"
        />
      </div>

      {error && (
        <p className="mb-6 rounded-md bg-critical-soft px-3 py-2 text-sm text-critical" role="alert">
          {error}
        </p>
      )}

      {!error && matches === null && <p className="text-sm text-ink-faint">Cargando partidos…</p>}

      {matches !== null && matches.length === 0 && hasActiveFilters && (
        <EmptyState
          icon="search"
          title="No se encontraron partidos con esos filtros"
          description="Prueba con otra liga o con otro nombre de equipo."
        />
      )}

      {matches !== null && matches.length === 0 && !hasActiveFilters && (
        <EmptyState
          icon="calm"
          title="Todavía no se ha ingerido ningún partido"
          description="En cuanto corra el primer ciclo de ingesta (programado o manual desde Admin), los partidos aparecerán aquí."
        />
      )}

      {matches !== null && matches.length > 0 && (
        <div className="overflow-x-auto rounded-xl border border-border bg-surface">
          <div className="min-w-[640px]">
            <div className="grid grid-cols-[1fr_2fr_auto_auto_auto] gap-4 border-b border-border px-4 py-2 text-xs uppercase tracking-wide text-ink-faint">
              <span>Liga</span>
              <span>Partido</span>
              <span>Fecha</span>
              <span>Estado</span>
              <span>Cuotas</span>
            </div>
            {matches.map((match) => (
              <Link
                key={match.id}
                to={`/matches/${match.id}`}
                className="grid grid-cols-[1fr_2fr_auto_auto_auto] items-center gap-4 border-b border-border px-4 py-3 text-sm transition-colors last:border-0 hover:bg-surface-2"
              >
                <span className="truncate text-ink-soft">{match.competitionName}</span>
                <span className="truncate text-ink">
                  {match.homeTeam} <span className="text-ink-faint">vs</span> {match.awayTeam}
                </span>
                <span className="whitespace-nowrap text-xs text-ink-faint">{formatDateTime(match.startTime)}</span>
                <span
                  className={`justify-self-start rounded-full px-2 py-0.5 text-xs font-medium ${STATUS_BADGE[match.status].className}`}
                >
                  {STATUS_BADGE[match.status].label}
                </span>
                {match.hasOdds ? (
                  <span className="justify-self-start rounded-full bg-good-soft px-2 py-0.5 text-xs font-medium text-good">
                    Con cuotas
                  </span>
                ) : (
                  <span className="justify-self-start rounded-full bg-surface-2 px-2 py-0.5 text-xs font-medium text-ink-faint">
                    Sin cuotas todavía
                  </span>
                )}
              </Link>
            ))}
          </div>
        </div>
      )}
    </Layout>
  )
}
