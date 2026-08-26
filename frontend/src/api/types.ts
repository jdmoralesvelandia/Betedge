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

export interface OddsHistoryEntryDto {
  bookmakerSlug: string
  selection: string
  oddValue: number
  timestamp: string
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
