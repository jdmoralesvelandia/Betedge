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

export function bookmakerLabel(slug: string): string {
  return slug
    .split(/[-.]/)
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
