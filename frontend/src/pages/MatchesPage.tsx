import { useEffect, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router-dom'
import { Layout } from '../components/Layout'
import { StaticBrandBackground } from '../components/StaticBrandBackground'
import { EmptyState } from '../components/EmptyState'
import { LastUpdatedBadge } from '../components/LastUpdatedBadge'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import type { CompetitionDto, MatchDto, MatchStatus } from '../api/types'
import { formatDateTime } from '../lib/format'

const SEARCH_DEBOUNCE_MS = 300

// FINISHED was previously bg-surface-2 text-ink-faint - same solid grey fill as SCHEDULED's
// bg-surface-2 text-ink-soft, differing only by text shade. Given a "already happened, less
// relevant now" pill deserves to read as visually receded, not just a paler copy of "upcoming" -
// so it drops the fill entirely (border-only) instead of getting yet another solid color.
// The border itself is --color-critical (the design system's one red, already reserved for real
// errors/alerts - see the role="alert" banners throughout the app) at 40% opacity, same "existing
// token, dimmed" technique already used elsewhere (e.g. border-series-1/40 in LoginPage) rather
// than a one-off red invented just for this badge - full-strength critical would read as an error
// state right next to this exact page's own error banner, which is exactly what dimming it avoids.
const STATUS_BADGE: Record<MatchStatus, { label: string; className: string }> = {
  SCHEDULED: { label: 'Próximo', className: 'bg-surface-2 text-ink-soft' },
  LIVE: { label: 'En vivo', className: 'bg-warning-soft text-warning' },
  FINISHED: { label: 'Finalizado', className: 'border border-critical/40 text-ink-faint' },
}

// Mirrors MatchQueryService's own sentinel on the backend - see its Javadoc.
const SHOW_ALL_FINISHED_SENTINEL = '0'
// Distinct from the rolling-day options below: "today" is a fixed America/Bogota calendar-day
// cutoff (see MatchQueryService.findAll's Javadoc for finishedToday), not "last N days". This is
// the page's own default on load - not one of MatchQueryService's DEFAULT_FINISHED_WITHIN_DAYS.
const TODAY_FILTER_VALUE = 'today'

const FINISHED_WINDOW_OPTIONS = [
  { value: TODAY_FILTER_VALUE, label: 'Hoy' },
  { value: '7', label: 'Últimos 7 días' },
  { value: '30', label: 'Últimos 30 días' },
  { value: '90', label: 'Últimos 3 meses' },
  { value: SHOW_ALL_FINISHED_SENTINEL, label: 'Todos' },
] as const

export function MatchesPage() {
  const { apiFetch } = useAuth()
  const location = useLocation()
  // liga/finalizados live in the URL so a "volver" link from a match's detail page can restore
  // this exact view, not just "Partidos with whatever filters happened to be default" - see
  // updateCompetitionId/updateFinishedFilter below for how they stay in sync, and
  // MatchDetailPage's own back-link for the other end of this.
  const [searchParams, setSearchParams] = useSearchParams()
  const [matches, setMatches] = useState<MatchDto[] | null>(null)
  const [competitions, setCompetitions] = useState<CompetitionDto[]>([])
  const [lastUpdated, setLastUpdated] = useState<string | null>(null)
  const [competitionId, setCompetitionId] = useState<number | null>(() => {
    const raw = searchParams.get('liga')
    return raw ? Number(raw) : null
  })
  const [searchInput, setSearchInput] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const [finishedFilter, setFinishedFilter] = useState<string>(() => searchParams.get('finalizados') ?? TODAY_FILTER_VALUE)
  const [error, setError] = useState<string | null>(null)

  function updateCompetitionId(id: number | null) {
    setCompetitionId(id)
    const next = new URLSearchParams(searchParams)
    if (id === null) next.delete('liga')
    else next.set('liga', String(id))
    setSearchParams(next, { replace: true })
  }

  function updateFinishedFilter(value: string) {
    setFinishedFilter(value)
    const next = new URLSearchParams(searchParams)
    if (value === TODAY_FILTER_VALUE) next.delete('finalizados')
    else next.set('finalizados', value)
    setSearchParams(next, { replace: true })
  }

  const hasActiveFilters = competitionId !== null || debouncedSearch.trim() !== ''
  const isShowingAllFinished = finishedFilter === SHOW_ALL_FINISHED_SENTINEL

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
    const isToday = finishedFilter === TODAY_FILTER_VALUE
    endpoints
      .matches(apiFetch, {
        competitionId: competitionId ?? undefined,
        search: debouncedSearch.trim() || undefined,
        finishedWithinDays: isToday ? undefined : Number(finishedFilter),
        finishedToday: isToday ? true : undefined,
      })
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
  }, [competitionId, debouncedSearch, finishedFilter])

  return (
    <Layout>
      <StaticBrandBackground />

      <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-ink">Partidos</h1>
          <p className="text-sm text-ink-faint">
            Todos los partidos ingeridos, tengan o no una oportunidad activa en este momento.
          </p>
        </div>
        <LastUpdatedBadge timestamp={lastUpdated} />
      </div>

      <div className="mb-2 flex flex-wrap items-center gap-3">
        <select
          value={competitionId ?? ''}
          onChange={(e) => updateCompetitionId(e.target.value ? Number(e.target.value) : null)}
          aria-label="Liga"
          className="rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink-soft outline-none transition-colors focus:border-brand"
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
          className="min-w-0 flex-1 rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink placeholder:text-ink-faint outline-none transition-colors focus:border-brand sm:max-w-xs"
        />
        <label className="flex items-center gap-2 text-sm text-ink-soft">
          <span className="whitespace-nowrap">Finalizados:</span>
          <select
            value={finishedFilter}
            onChange={(e) => updateFinishedFilter(e.target.value)}
            aria-label="Finalizados"
            className="rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink-soft outline-none transition-colors focus:border-brand"
          >
            {FINISHED_WINDOW_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
      </div>
      <p className="mb-6 text-xs text-ink-faint">
        Este filtro solo esconde partidos ya finalizados hace tiempo - los próximos y los que están en vivo siempre se
        muestran.
      </p>

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
          description="Prueba con otra liga, otro nombre de equipo, o ampliando el filtro de Finalizados."
        />
      )}

      {matches !== null && matches.length === 0 && !hasActiveFilters && !isShowingAllFinished && (
        <EmptyState
          icon="calm"
          title="No hay partidos dentro de esta ventana"
          description="Puede que solo haya partidos finalizados hace más tiempo del que muestra el filtro actual. Prueba cambiando Finalizados a Todos."
        />
      )}

      {matches !== null && matches.length === 0 && !hasActiveFilters && isShowingAllFinished && (
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
                state={{ from: location.pathname + location.search }}
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
