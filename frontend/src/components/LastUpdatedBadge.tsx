import { formatRelativeToNow } from '../lib/format'

export function LastUpdatedBadge({ timestamp }: { timestamp: string | null }) {
  return (
    <div className="flex items-center gap-2 rounded-full border border-border bg-surface px-3 py-1.5 text-xs text-ink-faint">
      <span
        className={`inline-block h-1.5 w-1.5 rounded-full ${timestamp ? 'bg-good' : 'bg-ink-faint'}`}
        aria-hidden="true"
      />
      {timestamp ? (
        <span>
          Datos actualizados <span className="text-ink-soft">{formatRelativeToNow(timestamp)}</span>
        </span>
      ) : (
        <span>Sin datos de cuotas todavía</span>
      )}
    </div>
  )
}
