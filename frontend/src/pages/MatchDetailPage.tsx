import { useEffect, useMemo, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Layout } from '../components/Layout'
import { EmptyState } from '../components/EmptyState'
import { ChevronIcon } from '../components/ChevronIcon'
import { OddsHistoryChart } from '../components/OddsHistoryChart'
import { SingleBookmakerChart } from '../components/SingleBookmakerChart'
import { ValueBetCard } from '../components/ValueBetCard'
import { SurebetCard } from '../components/SurebetCard'
import { useAuth } from '../auth/AuthContext'
import { endpoints } from '../api/endpoints'
import type { MatchDto, OddsHistoryEntryDto, SurebetDto, ValueBetDto } from '../api/types'
import { toEpochMs } from '../lib/oddsWindow'
import { formatDateTime, selectionLabel } from '../lib/format'

const SELECTIONS = ['home', 'draw', 'away'] as const

export function MatchDetailPage() {
  const { id } = useParams<{ id: string }>()
  const matchId = Number(id)
  const { apiFetch } = useAuth()
  const location = useLocation()
  // Where the user came from (Dashboard with its tab/liga, or Partidos with its own filters) -
  // carried as router state by whichever card/row linked here (see ValueBetCard/SurebetCard's
  // fromLocation prop and MatchesPage's own <Link state>). Absent on a direct visit (shared link,
  // page reload) - falls back to /dashboard below, same as if the origin was the Dashboard itself.
  const from = (location.state as { from?: string } | null)?.from
  const backTo = from ?? '/dashboard'
  // Only Partidos and the Dashboard ever set "from", and always to their own root path - "/matches"
  // (optionally with query params) for Partidos, "/dashboard" (optionally with query params) for
  // the Dashboard. No third value is possible: MatchDetailPage's own cards below pass this same
  // "from" straight through rather than pointing at themselves, so it never becomes "/matches/123".
  const backLabel = from?.startsWith('/matches') ? '← Volver a partidos' : '← Volver al dashboard'

  const [match, setMatch] = useState<MatchDto | null>(null)
  const [odds, setOdds] = useState<OddsHistoryEntryDto[] | null>(null)
  // Each provider's last completed ingestion run (ms since epoch, converted once here from the
  // API's ISO strings) - passed down to both charts so they can extend a series' line to "still
  // confirmed as of then" - see lib/oddsWindow.ts's withTrailingConfirmation.
  const [lastOddsPapiRunAt, setLastOddsPapiRunAt] = useState<number | null>(null)
  const [lastTheOddsApiRunAt, setLastTheOddsApiRunAt] = useState<number | null>(null)
  const [valueBets, setValueBets] = useState<ValueBetDto[]>([])
  const [surebets, setSurebets] = useState<SurebetDto[]>([])
  const [selection, setSelection] = useState<(typeof SELECTIONS)[number]>('home')
  const [error, setError] = useState<string | null>(null)
  // Shared by SingleBookmakerChart (always visible) and OddsHistoryChart (behind "Comparación de
  // casas" below) so switching the time window in either one keeps both in sync - lifted here
  // rather than each tracking its own copy.
  const [showFullHistory, setShowFullHistory] = useState(false)
  const [showComparison, setShowComparison] = useState(false)

  useEffect(() => {
    let cancelled = false

    async function load() {
      try {
        const [matchDto, oddsHistory, vbActive, sbActive] = await Promise.all([
          endpoints.match(apiFetch, matchId),
          endpoints.oddsHistory(apiFetch, matchId),
          endpoints.activeValueBetsForMatch(apiFetch, matchId),
          endpoints.activeSurebetsForMatch(apiFetch, matchId),
        ])
        if (cancelled) return
        setMatch(matchDto)
        setOdds(oddsHistory.entries)
        setLastOddsPapiRunAt(toEpochMs(oddsHistory.lastOddsPapiRunAt))
        setLastTheOddsApiRunAt(toEpochMs(oddsHistory.lastTheOddsApiRunAt))
        setValueBets(vbActive)
        setSurebets(sbActive)
      } catch {
        if (!cancelled) setError('No se pudo cargar la información de este partido.')
      }
    }

    void load()
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [matchId])

  const filteredOdds = useMemo(
    () => (odds ?? []).filter((entry) => entry.selection === selection),
    [odds, selection],
  )

  // Highest edge first - every active value bet for this match (unlike the Dashboard's own
  // one-card-per-match summary, this section deliberately shows all of them, one per bookmaker+
  // selection), sorted client-side since the list is already fully loaded and small; no reason to
  // push an ORDER BY onto ValueBetRepository.findActiveByMatch's native query for this.
  const sortedValueBets = useMemo(
    () => [...valueBets].sort((a, b) => b.edgePercentage - a.edgePercentage),
    [valueBets],
  )

  if (error) {
    return (
      <Layout>
        <p className="rounded-md bg-critical-soft px-3 py-2 text-sm text-critical" role="alert">
          {error}
        </p>
      </Layout>
    )
  }

  if (!match || odds === null) {
    return (
      <Layout>
        <p className="text-sm text-ink-faint">Cargando partido…</p>
      </Layout>
    )
  }

  return (
    <Layout>
      <Link to={backTo} className="mb-4 inline-block text-sm text-ink-faint hover:text-ink">
        {backLabel}
      </Link>

      <div className="mb-6">
        <p className="text-xs uppercase tracking-wide text-ink-faint">{match.competitionName}</p>
        <h1 className="text-xl font-semibold text-ink">
          {match.homeTeam} <span className="text-ink-faint">vs</span> {match.awayTeam}
        </h1>
        <p className="text-sm text-ink-faint">{formatDateTime(match.startTime)}</p>
      </div>

      <section className="mb-8 rounded-xl border border-border bg-surface p-5">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 className="text-sm font-semibold text-ink">Evolución de cuotas por casa de apuestas</h2>
          <div className="flex gap-1 rounded-md bg-surface-2 p-1">
            {SELECTIONS.map((s) => (
              <button
                key={s}
                type="button"
                onClick={() => setSelection(s)}
                className={`rounded px-2.5 py-1 text-xs font-medium transition-colors ${
                  selection === s ? 'bg-series-1 text-white' : 'text-ink-soft hover:text-ink'
                }`}
              >
                {selectionLabel(s)}
              </button>
            ))}
          </div>
        </div>
        <SingleBookmakerChart
          entries={filteredOdds}
          showFullHistory={showFullHistory}
          onToggleFullHistory={() => setShowFullHistory((prev) => !prev)}
          lastOddsPapiRunAt={lastOddsPapiRunAt}
          lastTheOddsApiRunAt={lastTheOddsApiRunAt}
        />

        <button
          type="button"
          onClick={() => setShowComparison((prev) => !prev)}
          aria-expanded={showComparison}
          className="mt-6 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-ink-faint transition-colors hover:text-ink-soft"
        >
          <ChevronIcon open={showComparison} />
          Comparación de casas
        </button>

        {showComparison && (
          <div className="mt-3">
            <OddsHistoryChart
              entries={filteredOdds}
              showFullHistory={showFullHistory}
              onToggleFullHistory={() => setShowFullHistory((prev) => !prev)}
              lastOddsPapiRunAt={lastOddsPapiRunAt}
              lastTheOddsApiRunAt={lastTheOddsApiRunAt}
            />
          </div>
        )}
      </section>

      <section className="mb-8">
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-faint">
          Value bets activos ({valueBets.length})
        </h2>
        {valueBets.length === 0 ? (
          <EmptyState
            icon="search"
            title="Sin value bets activos para este partido"
            description="No hay oportunidades activas para este partido en este momento."
          />
        ) : (
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {sortedValueBets.map((vb) => (
              <ValueBetCard key={vb.id} valueBet={vb} showDetectedAt fromLocation={from} />
            ))}
          </div>
        )}
      </section>

      <section>
        <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-ink-faint">
          Surebets activos ({surebets.length})
        </h2>
        {surebets.length === 0 ? (
          <EmptyState
            icon="search"
            title="Sin surebets activos para este partido"
            description="No hay oportunidades activas para este partido en este momento."
          />
        ) : (
          <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
            {surebets.map((sb) => (
              <SurebetCard key={sb.id} surebet={sb} showDetectedAt fromLocation={from} />
            ))}
          </div>
        )}
      </section>
    </Layout>
  )
}
