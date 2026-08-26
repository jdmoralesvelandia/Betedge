import { useSyncExternalStore } from 'react'

export const CURRENCIES = ['COP', 'USD', 'EUR', 'GBP', 'MXN', 'BRL'] as const
export type CurrencyCode = (typeof CURRENCIES)[number]

const STORAGE_KEY = 'betedge:currency'
const DEFAULT_CURRENCY: CurrencyCode = 'COP'

const CURRENCY_LOCALES: Record<CurrencyCode, string> = {
  COP: 'es-CO',
  USD: 'en-US',
  EUR: 'es-ES',
  GBP: 'en-GB',
  MXN: 'es-MX',
  BRL: 'pt-BR',
}

function isCurrencyCode(value: string | null): value is CurrencyCode {
  return value !== null && (CURRENCIES as readonly string[]).includes(value)
}

function readFromStorage(): CurrencyCode {
  const stored = window.localStorage.getItem(STORAGE_KEY)
  return isCurrencyCode(stored) ? stored : DEFAULT_CURRENCY
}

let currentCurrency: CurrencyCode = readFromStorage()
const listeners = new Set<() => void>()

function setCurrency(currency: CurrencyCode): void {
  currentCurrency = currency
  window.localStorage.setItem(STORAGE_KEY, currency)
  listeners.forEach((listener) => listener())
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot(): CurrencyCode {
  return currentCurrency
}

/** Live-synced across every component using it: changing the currency anywhere updates all of them. */
export function useCurrency(): [CurrencyCode, (currency: CurrencyCode) => void] {
  const currency = useSyncExternalStore(subscribe, getSnapshot)
  return [currency, setCurrency]
}

/** Display formatting only - the typed amount is never converted, just re-labelled/re-punctuated. */
export function formatCurrency(amount: number, currency: CurrencyCode): string {
  return new Intl.NumberFormat(CURRENCY_LOCALES[currency], {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)
}
