export type Role = 'USER' | 'ADMIN'

export type MatchStatus = 'SCHEDULED' | 'LIVE' | 'FINISHED'

export interface AuthResponseDto {
  accessToken: string
  tokenType: string
  email: string
  role: Role
}

export interface UserResponseDto {
  id: number
  email: string
  role: Role
}

export interface ValueBetDto {
  id: number
  matchId: number
  homeTeam: string
  awayTeam: string
  competitionName: string
  startTime: string
  bookmakerSlug: string
  selection: string
  impliedProbability: number
  estimatedTrueProbability: number
  edgePercentage: number
  bookmakerProbabilities: Record<string, number>
  detectedAt: string
}

export interface SurebetLegDto {
  selection: string
  bookmakerSlug: string
  oddValue: number
  stakePercentage: number
}

export interface SurebetDto {
  id: number
  matchId: number
  homeTeam: string
  awayTeam: string
  competitionName: string
  startTime: string
  legs: SurebetLegDto[]
  totalImpliedProbability: number
  profitPercentage: number
  detectedAt: string
}

export interface LastIngestionResponseDto {
  lastOddsTimestamp: string | null
}

export interface MatchDto {
  id: number
  homeTeam: string
  awayTeam: string
  competitionName: string
  startTime: string
  status: MatchStatus
  hasOdds: boolean
}

export interface CompetitionDto {
  id: number
  name: string
}

export type DataSource = 'ODDSPAPI' | 'THEODDSAPI'

export interface OddsHistoryEntryDto {
  bookmakerSlug: string
  selection: string
  oddValue: number
  timestamp: string
  dataSource: DataSource
}

/**
 * lastOddsPapiRunAt/lastTheOddsApiRunAt are each that provider's last COMPLETED ingestion run -
 * used to visually extend a series' line to "still confirmed as of then" instead of leaving it
 * dangling at its own last real point (see lib/oddsWindow.ts's withTrailingConfirmation). Either
 * can be null if that provider has never completed a run yet.
 *
 * truncated (2026-09-02): true when the backend capped `entries` at its own row limit and real
 * history for this match goes back further than what's included (always the most recent rows in
 * that case, never the oldest) - see MatchDetailPage's own truncation notice for how this surfaces.
 */
export interface OddsHistoryResponseDto {
  entries: OddsHistoryEntryDto[]
  lastOddsPapiRunAt: string | null
  lastTheOddsApiRunAt: string | null
  truncated: boolean
}

export type TriggeredBy = 'SCHEDULED' | 'MANUAL'

export interface CompetitionBreakdownEntryDto {
  competitionName: string
  eventsReceived: number
  newMatches: number
  newOdds: number
}

export interface IngestionRunDto {
  id: number
  triggeredBy: TriggeredBy
  startedAt: string
  finishedAt: string
  totalEventsReceived: number
  totalNewMatches: number
  totalNewOdds: number
  valueBetsDetected: number
  surebetsDetected: number
  competitionBreakdown: CompetitionBreakdownEntryDto[]
}
