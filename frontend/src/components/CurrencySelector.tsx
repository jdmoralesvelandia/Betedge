import { CURRENCIES, useCurrency } from '../lib/currency'

export function CurrencySelector({ id }: { id?: string }) {
  const [currency, setCurrency] = useCurrency()

  return (
    <select
      id={id}
      value={currency}
      onChange={(e) => setCurrency(e.target.value as (typeof CURRENCIES)[number])}
      aria-label="Moneda"
      className="rounded-md border border-border bg-surface-2 px-2 py-1.5 text-xs text-ink-soft"
    >
      {CURRENCIES.map((code) => (
        <option key={code} value={code}>
          {code}
        </option>
      ))}
    </select>
  )
}
