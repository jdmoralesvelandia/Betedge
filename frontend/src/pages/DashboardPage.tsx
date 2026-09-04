import { useEffect, useMemo, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { Layout } from '../components/Layout'
import { StaticBrandBackground } from '../components/StaticBrandBackground'
import { EmptyState } from '../components/EmptyState'
import { LastUpdatedBadge } from '../components/LastUpdatedBadge'
import { SegmentedControl } from '../components/SegmentedControl'
import { ValueBetCard } from '../components/ValueBetCard'
import { SurebetCard } from '../components/SurebetCard'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import type { SurebetDto, ValueBetDto } from '../api/types'

function byStartTimeAscending(a: { startTime: string }, b: { startTime: string }): number {
  return new Date(a.startTime).getTime() - new Date(b.startTime).getTime()
}

/**
 * One card per match on the Dashboard, not one per opportunity - keeps only the highest-edge item
 * for each matchId. A match can legitimately have several active value bets (one per bookmaker+
 * selection - see ValueBetRepository.findActive's own DISTINCT ON (match_id, selection), which
 * intentionally allows up to 3 per match, one per selection) or, in principle, several surebets
 * (SurebetRepository.findActive collapses to one per match already via its own DISTINCT ON
 * (match_id) - this can never actually trigger for surebets today, but costs nothing to apply
 * uniformly as a safeguard against that changing later). This is the Dashboard's own summary view;
 * MatchDetailPage's "Value bets activos" section deliberately shows every one, uncollapsed.
 */
function bestPerMatch<T extends { matchId: number }>(items: T[], edgeOf: (item: T) => number): T[] {
  const bestByMatch = new Map<number, T>()
  for (const item of items) {
    const current = bestByMatch.get(item.matchId)
    if (!current || edgeOf(item) > edgeOf(current)) {
      bestByMatch.set(item.matchId, item)
    }
  }
  return [...bestByMatch.values()]
}

const ALL_COMPETITIONS = ''
type Tab = 'valueBets' | 'surebets'

export function DashboardPage() {
  const { apiFetch } = useAuth()
  const location = useLocation()
  // tab/liga live in the URL so a "volver" link from a match's detail page can restore this exact
  // view (which tab, which league) instead of always landing back on the tab/filter defaults -
  // see updateActiveTab/updateCompetitionFilter below for how they stay in sync, and
  // MatchDetailPage's own back-link for the other end of this.
  const [searchParams, setSearchParams] = useSearchParams()
  const [valueBets, setValueBets] = useState<ValueBetDto[] | null>(null)
  const [surebets, setSurebets] = useState<SurebetDto[] | null>(null)
  const [lastUpdated, setLastUpdated] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  // Shared between both tabs on purpose - it's the same filter bar visible above either one, not
  // two independent filters that happen to look alike. Switching tabs never re-fetches anything;
  // both lists are already loaded, this only ever changes which of the two arrays gets rendered.
  const [competitionFilter, setCompetitionFilter] = useState<string>(() => searchParams.get('liga') ?? ALL_COMPETITIONS)
  const [activeTab, setActiveTab] = useState<Tab>(() => (searchParams.get('tab') === 'surebets' ? 'surebets' : 'valueBets'))

  function updateActiveTab(tab: Tab) {
    setActiveTab(tab)
    const next = new URLSearchParams(searchParams)
    if (tab === 'valueBets') next.delete('tab')
    else next.set('tab', tab)
    setSearchParams(next, { replace: true })
  }

  function updateCompetitionFilter(value: string) {
    setCompetitionFilter(value)
    const next = new URLSearchParams(searchParams)
    if (value === ALL_COMPETITIONS) next.delete('liga')
    else next.set('liga', value)
    setSearchParams(next, { replace: true })
  }

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

  // Built from whatever's already loaded, not a fixed list - a league gaining or losing coverage
  // is reflected here automatically, no code change needed.
  const availableCompetitions = useMemo(() => {
    const names = new Set<string>()
    for (const vb of sortedValueBets) names.add(vb.competitionName)
    for (const sb of sortedSurebets) names.add(sb.competitionName)
    return [...names].sort((a, b) => a.localeCompare(b, 'es'))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [valueBets, surebets])

  const filteredValueBets =
    competitionFilter === ALL_COMPETITIONS
      ? sortedValueBets
      : sortedValueBets.filter((vb) => vb.competitionName === competitionFilter)
  const filteredSurebets =
    competitionFilter === ALL_COMPETITIONS
      ? sortedSurebets
      : sortedSurebets.filter((sb) => sb.competitionName === competitionFilter)

  // One card per match, highest edge/profit wins - see bestPerMatch's own comment. Always after
  // the league filter above, never before: collapsing first could hide a match's best opportunity
  // if it happened to belong to a different league filter than the one currently selected (it
  // can't, in practice, since all of a match's own value bets/surebets share its one league - but
  // filter-then-collapse is the correct order regardless, and is also simply what the task asked for).
  const collapsedValueBets = useMemo(
    () => bestPerMatch(filteredValueBets, (vb) => vb.edgePercentage),
    [filteredValueBets],
  )
  const collapsedSurebets = useMemo(() => bestPerMatch(filteredSurebets, (sb) => sb.profitPercentage), [filteredSurebets])

  return (
    <Layout>
      <StaticBrandBackground />

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

      {!loading && !hasNothing && (
        <>
          <div className="mb-6 flex flex-wrap items-center gap-3">
            <SegmentedControl
              options={[
                { value: 'valueBets', label: `Value Bets (${collapsedValueBets.length})` },
                { value: 'surebets', label: `Surebets (${collapsedSurebets.length})` },
              ]}
              value={activeTab}
              onChange={updateActiveTab}
            />

            {availableCompetitions.length > 0 && (
              <select
                value={competitionFilter}
                onChange={(e) => updateCompetitionFilter(e.target.value)}
                aria-label="Liga"
                className="rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink-soft outline-none transition-colors focus:border-brand"
              >
                <option value={ALL_COMPETITIONS}>Todas las ligas</option>
                {availableCompetitions.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            )}
          </div>

          {activeTab === 'valueBets' ? (
            <section>
              {collapsedValueBets.length === 0 ? (
                <p className="text-sm text-ink-faint">No hay value bets activos para esta liga ahora mismo.</p>
              ) : (
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                  {collapsedValueBets.map((vb, index) => (
                    <div
                      key={vb.id}
                      className="flex motion-safe:animate-fade-up motion-safe:opacity-0"
                      style={{ animationDelay: `${Math.min(index * 40, 320)}ms` }}
                    >
                      <ValueBetCard valueBet={vb} fromLocation={location.pathname + location.search} />
                    </div>
                  ))}
                </div>
              )}
            </section>
          ) : (
            <section>
              {collapsedSurebets.length === 0 ? (
                <p className="text-sm text-ink-faint">No hay surebets activos para esta liga ahora mismo.</p>
              ) : (
                <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                  {collapsedSurebets.map((sb, index) => (
                    <div
                      key={sb.id}
                      className="flex motion-safe:animate-fade-up motion-safe:opacity-0"
                      style={{ animationDelay: `${Math.min(index * 40, 320)}ms` }}
                    >
                      <SurebetCard surebet={sb} fromLocation={location.pathname + location.search} />
                    </div>
                  ))}
                </div>
              )}
            </section>
          )}
        </>
      )}
    </Layout>
  )
}
