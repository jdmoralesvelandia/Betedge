import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

function navLinkClass({ isActive }: { isActive: boolean }): string {
  return [
    'rounded-md px-3 py-1.5 text-sm font-medium transition-colors',
    isActive ? 'bg-surface-2 text-ink' : 'text-ink-soft hover:bg-surface-2 hover:text-ink',
  ].join(' ')
}

export function Layout({ children }: { children: ReactNode }) {
  const { user, logout } = useAuth()

  return (
    <div className="flex min-h-full flex-col">
      <header className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-6">
            <NavLink to="/" className="flex items-center gap-2 text-base font-semibold text-ink">
              <span className="inline-block h-2.5 w-2.5 rounded-full bg-brand" aria-hidden="true" />
              BetEdge
            </NavLink>
            <nav className="flex items-center gap-1">
              <NavLink to="/" end className={navLinkClass}>
                Inicio
              </NavLink>
              <NavLink to="/dashboard" className={navLinkClass}>
                Dashboard
              </NavLink>
              <NavLink to="/matches" className={navLinkClass}>
                Partidos
              </NavLink>
              {user?.role === 'ADMIN' && (
                <NavLink to="/admin" className={navLinkClass}>
                  Admin
                </NavLink>
              )}
            </nav>
          </div>
          {user && (
            <div className="flex items-center gap-3">
              <span className="hidden text-sm text-ink-faint sm:inline">{user.email}</span>
              <span className="rounded-full border border-border px-2 py-0.5 text-xs font-medium text-ink-soft">
                {user.role}
              </span>
              <button
                type="button"
                onClick={() => void logout()}
                className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-ink-soft transition-colors hover:border-critical hover:text-critical"
              >
                Salir
              </button>
            </div>
          )}
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 sm:px-6">{children}</main>
    </div>
  )
}
