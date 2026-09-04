import type { ReactNode } from 'react'

interface SegmentedControlOption<T extends string> {
  value: T
  label: ReactNode
}

/**
 * Shared segmented-tab control - one visual capsule (border + bg-brand-dark fill) with the active
 * option highlighted in bg-brand inside it. Originally MatchDetailPage's own Local/Empate/
 * Visitante selector; extracted here (2026-09-03) so DashboardPage's Value Bets/Surebets tabs
 * render the exact same pattern instead of a near-duplicate that read as two loose buttons rather
 * than one control. Both call sites pass their own option list/value/onChange - this component
 * owns none of that state, purely presentational.
 *
 * bg-brand-dark (not bg-surface-2, its original color) because on Dashboard the capsule floats
 * directly on the page background with no card behind it for contrast - a neutral grey container
 * barely read as one unified control there. See its own comment in index.css for why it's a
 * distinct opaque token rather than bg-brand at reduced opacity, and for the contrast numbers
 * behind it - both the active option's white text and the inactive options' text-ink-soft clear
 * AA against it with room to spare.
 */
export function SegmentedControl<T extends string>({
  options,
  value,
  onChange,
  size = 'md',
}: {
  options: SegmentedControlOption<T>[]
  value: T
  onChange: (value: T) => void
  /**
   * 'sm' matches MatchDetailPage's Local/Empate/Visitante (3 options - kept compact so it never
   * wraps at 375px); 'md' matches Dashboard's Value Bets/Surebets (only 2, longer labels - room
   * for the more generous padding those had before sharing this component).
   */
  size?: 'sm' | 'md'
}) {
  return (
    <div className="inline-flex gap-1 rounded-md border border-border bg-brand-dark p-1">
      {options.map((option) => {
        const active = option.value === value
        return (
          <button
            key={option.value}
            type="button"
            onClick={() => onChange(option.value)}
            className={`rounded font-medium transition-colors duration-200 ${
              size === 'sm' ? 'px-2.5 py-1 text-xs' : 'px-4 py-2 text-sm'
            } ${active ? 'bg-brand text-white' : 'text-ink-soft hover:text-ink'}`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
