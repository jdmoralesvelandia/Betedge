interface EmptyStateProps {
  title: string
  description: string
  icon?: 'calm' | 'search'
}

function CalmIcon() {
  return (
    <svg viewBox="0 0 48 48" fill="none" className="h-10 w-10 text-ink-faint" aria-hidden="true">
      <circle cx="24" cy="24" r="18" stroke="currentColor" strokeWidth="2" />
      <path d="M17 26c1.8 2.4 4.2 3.6 7 3.6s5.2-1.2 7-3.6" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <circle cx="18.5" cy="19.5" r="1.6" fill="currentColor" />
      <circle cx="29.5" cy="19.5" r="1.6" fill="currentColor" />
    </svg>
  )
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 48 48" fill="none" className="h-10 w-10 text-ink-faint" aria-hidden="true">
      <circle cx="21" cy="21" r="12" stroke="currentColor" strokeWidth="2" />
      <path d="M30 30l8 8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
    </svg>
  )
}

export function EmptyState({ title, description, icon = 'calm' }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border bg-surface px-6 py-14 text-center">
      {icon === 'calm' ? <CalmIcon /> : <SearchIcon />}
      <p className="text-base font-medium text-ink">{title}</p>
      <p className="max-w-md text-sm text-ink-faint">{description}</p>
    </div>
  )
}
