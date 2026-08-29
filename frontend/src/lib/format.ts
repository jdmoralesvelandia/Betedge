export function formatPercent(value: number, fractionDigits = 2): string {
  return `${value.toFixed(fractionDigits)}%`
}

export function formatOdd(value: number): string {
  return value.toFixed(2)
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('es-ES', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatRelativeToNow(iso: string): string {
  const then = new Date(iso).getTime()
  const diffMs = Date.now() - then
  const minutes = Math.round(diffMs / 60_000)

  if (minutes < 1) return 'hace unos segundos'
  if (minutes < 60) return `hace ${minutes} min`

  const hours = Math.round(minutes / 60)
  if (hours < 24) return `hace ${hours} h`

  const days = Math.round(hours / 24)
  return `hace ${days} d`
}

/**
 * The 24 bookmaker keys actually curated for ingestion - see odds-ingestion.bookmakers (3:
 * pinnacle, betano, betplay) and theoddsapi-ingestion.bookmakers (22, pinnacle shared with the
 * former) in .env/application.yml. Real recognizable brand names, not a capitalized slug.
 *
 * Country-suffixed slugs only keep that suffix when the same brand has more than one country
 * variant curated at once (winamax_de + winamax_fr; unibet_fr + unibet_nl + unibet_se) - there the
 * suffix is the only thing telling two chart series apart. A brand with exactly one curated
 * variant (betclic_fr, codere_it, leovegas_se, pmu_fr, tipico_de) drops it as redundant noise.
 */
const BOOKMAKER_NAMES: Record<string, string> = {
  pinnacle: 'Pinnacle',
  betano: 'Betano',
  betplay: 'BetPlay',
  betclic_fr: 'Betclic',
  betonlineag: 'BetOnline.ag',
  betsson: 'Betsson',
  codere_it: 'Codere',
  coolbet: 'Coolbet',
  everygame: 'Everygame',
  gtbets: 'GTbets',
  leovegas_se: 'LeoVegas',
  marathonbet: 'Marathonbet',
  mybookieag: 'MyBookie.ag',
  onexbet: '1xBet',
  pmu_fr: 'PMU',
  sport888: '888sport',
  suprabets: 'Suprabets',
  tipico_de: 'Tipico',
  unibet_fr: 'Unibet FR',
  unibet_nl: 'Unibet NL',
  unibet_se: 'Unibet SE',
  williamhill: 'William Hill',
  winamax_de: 'Winamax DE',
  winamax_fr: 'Winamax FR',
}

export function bookmakerLabel(slug: string): string {
  const known = BOOKMAKER_NAMES[slug]
  if (known) return known
  // Fallback for a slug not yet in the curated map above (e.g. a bookmaker added to
  // odds-ingestion.bookmakers/theoddsapi-ingestion.bookmakers without updating this list) -
  // splits on underscores too, not just hyphens/dots, so it never shows a raw "some_slug_de".
  return slug
    .split(/[-._]/)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ')
}

export function selectionLabel(selection: string): string {
  switch (selection) {
    case 'home':
      return 'Local'
    case 'draw':
      return 'Empate'
    case 'away':
      return 'Visitante'
    default:
      return selection
  }
}
