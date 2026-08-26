import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import type { SurebetDto } from '../api/types'
import { CurrencySelector } from './CurrencySelector'
import { useCurrency, formatCurrency } from '../lib/currency'
import { bookmakerLabel, formatDateTime, formatOdd, formatPercent, selectionLabel } from '../lib/format'

export function SurebetCard({
  surebet,
  showDetectedAt = false,
}: {
  surebet: SurebetDto
  /** MatchDetailPage's history section shows when each was detected, over time; everywhere else (e.g. the Dashboard) shows the match's own kickoff date instead. */
  showDetectedAt?: boolean
}) {
  const inputId = useId()
  const [amountInput, setAmountInput] = useState('')
  const [currency] = useCurrency()

  const amount = Number(amountInput)
  const hasAmount = amountInput.trim() !== '' && Number.isFinite(amount) && amount > 0
  const guaranteedProfit = (amount * surebet.profitPercentage) / 100

  return (
    <div className="flex flex-col rounded-xl border border-border bg-surface transition-colors hover:border-series-1/60">
      <Link to={`/matches/${surebet.matchId}`} className="group flex flex-col gap-3 p-4">
        <div className="flex items-center justify-between text-xs text-ink-faint">
          <span className="truncate">{surebet.competitionName}</span>
          <span>{formatDateTime(showDetectedAt ? surebet.detectedAt : surebet.startTime)}</span>
        </div>

        <div className="flex items-start justify-between gap-3">
          <p className="text-sm font-medium text-ink">
            {surebet.homeTeam} <span className="text-ink-faint">vs</span> {surebet.awayTeam}
          </p>
          <div className="flex flex-col items-end shrink-0">
            <span className="text-xs text-ink-faint">profit</span>
            <span className="rounded-md bg-good-soft px-2 py-0.5 text-lg font-semibold text-good">
              +{formatPercent(surebet.profitPercentage)}
            </span>
          </div>
        </div>

        <ul className="grid grid-cols-1 gap-1.5 sm:grid-cols-3">
          {surebet.legs.map((leg) => (
            <li key={leg.selection} className="flex flex-col gap-1 rounded-md bg-surface-2 px-2.5 py-1.5 text-xs">
              <div className="flex items-center justify-between">
                <span className="text-ink-soft">{selectionLabel(leg.selection)}</span>
                <span className="text-ink-faint">{bookmakerLabel(leg.bookmakerSlug)}</span>
                <span className="font-medium text-ink">{formatOdd(leg.oddValue)}</span>
              </div>
              {hasAmount && (
                <div className="text-right text-[11px] text-ink-faint">
                  Apostar {formatCurrency((amount * leg.stakePercentage) / 100, currency)}
                </div>
              )}
            </li>
          ))}
        </ul>
      </Link>

      <div className="border-t border-border p-4">
        <div className="flex flex-wrap items-end gap-2">
          <div className="flex flex-col gap-1">
            <label htmlFor={inputId} className="text-xs text-ink-faint">
              ¿Cuánto apostarías?
            </label>
            <input
              id={inputId}
              type="number"
              inputMode="decimal"
              min="0"
              step="any"
              placeholder="0"
              value={amountInput}
              onChange={(e) => setAmountInput(e.target.value)}
              className="w-32 rounded-md border border-border bg-surface-2 px-2 py-1.5 text-sm text-ink"
            />
          </div>
          <CurrencySelector />
        </div>

        {hasAmount && (
          <div className="mt-3 flex items-center justify-between text-sm">
            <span className="text-ink-faint">Ganancia garantizada total</span>
            <span className="font-medium text-good">{formatCurrency(guaranteedProfit, currency)}</span>
          </div>
        )}
      </div>
    </div>
  )
}
