import type { RequestOptions } from './client'
import type {
  CompetitionDto,
  IngestionRunDto,
  LastIngestionResponseDto,
  MatchDto,
  OddsHistoryEntryDto,
  SurebetDto,
  ValueBetDto,
} from './types'

export type Fetcher = <T>(path: string, options?: RequestOptions) => Promise<T>

export const endpoints = {
  activeValueBets: (fetcher: Fetcher) => fetcher<ValueBetDto[]>('/value-bets'),
  activeValueBetsForMatch: (fetcher: Fetcher, matchId: number) =>
    fetcher<ValueBetDto[]>(`/value-bets/active?matchId=${matchId}`),

  activeSurebets: (fetcher: Fetcher) => fetcher<SurebetDto[]>('/surebets'),
  activeSurebetsForMatch: (fetcher: Fetcher, matchId: number) =>
    fetcher<SurebetDto[]>(`/surebets/active?matchId=${matchId}`),

  lastUpdated: (fetcher: Fetcher) => fetcher<LastIngestionResponseDto>('/odds/last-updated'),
  oddsHistory: (fetcher: Fetcher, matchId: number) =>
    fetcher<OddsHistoryEntryDto[]>(`/odds/history?matchId=${matchId}`),

  matches: (
    fetcher: Fetcher,
    filters?: { competitionId?: number; search?: string; finishedWithinDays?: number },
  ) => {
    const params = new URLSearchParams()
    if (filters?.competitionId != null) params.set('competitionId', String(filters.competitionId))
    if (filters?.search) params.set('search', filters.search)
    if (filters?.finishedWithinDays != null) params.set('finishedWithinDays', String(filters.finishedWithinDays))
    const query = params.toString()
    return fetcher<MatchDto[]>(`/matches${query ? `?${query}` : ''}`)
  },
  match: (fetcher: Fetcher, id: number) => fetcher<MatchDto>(`/matches/${id}`),
  competitions: (fetcher: Fetcher) => fetcher<CompetitionDto[]>('/competitions'),

  triggerIngestion: (fetcher: Fetcher) =>
    fetcher<IngestionRunDto>('/admin/ingestion/trigger', { method: 'POST' }),
  lastIngestionRun: (fetcher: Fetcher) =>
    fetcher<IngestionRunDto | undefined>('/admin/ingestion/last-run'),

  triggerTheOddsApiIngestion: (fetcher: Fetcher) =>
    fetcher<IngestionRunDto>('/admin/ingestion/trigger-theoddsapi', { method: 'POST' }),
  lastTheOddsApiIngestionRun: (fetcher: Fetcher) =>
    fetcher<IngestionRunDto | undefined>('/admin/ingestion/last-run-theoddsapi'),
}
