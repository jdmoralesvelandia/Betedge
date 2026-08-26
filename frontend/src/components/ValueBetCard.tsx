import { useId, useState } from 'react'
import { Link } from 'react-router-dom'
import type { ValueBetDto } from '../api/types'
import { CurrencySelector } from './CurrencySelector'
import { useCurrency, formatCurrency } from '../lib/currency'
import { bookmakerLabel, formatDateTime, formatOdd, formatPercent, selectionLabel } from '../lib/format'

export function ValueBetCard({
  valueBet,
  showDetectedAt = false,
}: {
  valueBet: ValueBetDto
  /** MatchDetailPage's history section shows when each was detected, over time; everywhere else (e.g. the Dashboard) shows the match's own kickoff date instead. */
  showDetectedAt?: boolean
}) {
  const odd = 1 / valueBet.impliedProbability
  const inputId = useId()
  const [amountInput, setAmountInput] = useState('')
  const [currency] = useCurrency()

  const amount = Number(amountInput)
  const hasAmount = amountInput.trim() !== '' && Number.isFinite(amount) && amount > 0
  const payout = amount * odd
  const expectedValue = amount * (valueBet.estimatedTrueProbability * odd - 1)

  return (
    <div className="flex flex-col rounded-xl border border-border bg-surface transition-colors hover:border-series-1/60">
      <Link to={`/matches/${valueBet.matchId}`} className="group flex flex-col gap-3 p-4">
        <div className="flex items-center justify-between text-xs text-ink-faint">
          <span className="truncate">{valueBet.competitionName}</span>
          <span>{formatDateTime(showDetectedAt ? valueBet.detectedAt : valueBet.startTime)}</span>
        </div>

        <p className="text-sm font-medium text-ink">
          {valueBet.homeTeam} <span className="text-ink-faint">vs</span> {valueBet.awayTeam}
        </p>

        <div className="flex items-end justify-between gap-3">
          <div className="flex flex-col gap-1">
            <span className="text-xs uppercase tracking-wide text-ink-faint">{bookmakerLabel(valueBet.bookmakerSlug)}</span>
            <span className="text-sm text-ink-soft">
              {selectionLabel(valueBet.selection)} <span className="text-ink-faint">@</span> {formatOdd(odd)}
            </span>
          </div>
          <div className="flex flex-col items-end">
            <span className="text-xs text-ink-faint">edge</span>
            <span className="rounded-md bg-good-soft px-2 py-0.5 text-lg font-semibold text-good">
              +{formatPercent(valueBet.edgePercentage)}
            </span>
          </div>
        </div>
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
          <div className="mt-3 flex flex-col gap-1.5 text-sm">
            <div className="flex items-center justify-between">
              <span className="text-ink-faint">Pago potencial si acierta</span>
              <span className="font-medium text-ink">{formatCurrency(payout, currency)}</span>
            </div>
            <div className="flex items-center justify-between">
              <span className="text-ink-faint">Valor esperado</span>
              <span className={`font-medium ${expectedValue >= 0 ? 'text-good' : 'text-critical'}`}>
                {formatCurrency(expectedValue, currency)}
              </span>
            </div>
            <p className="text-[11px] leading-snug text-ink-faint">
              El valor esperado es la ganancia promedio a largo plazo si repitieras esta apuesta muchas veces, no una
              garantía en esta apuesta puntual.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
