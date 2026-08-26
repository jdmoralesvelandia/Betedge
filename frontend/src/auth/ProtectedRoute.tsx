import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './AuthContext'

function SessionGate({ initializing, initError }: { initializing: boolean; initError: string | null }) {
  if (initializing) {
    return (
      <div className="flex h-full items-center justify-center text-ink-faint">
        Cargando sesión…
      </div>
    )
  }

  return (
    <div className="flex h-full items-center justify-center p-6">
      <p className="rounded-md bg-critical-soft px-3 py-2 text-sm text-critical" role="alert">
        {initError}
      </p>
    </div>
  )
}

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, initializing, initError } = useAuth()

  if (initializing || initError) {
    return <SessionGate initializing={initializing} initError={initError} />
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user, initializing, initError } = useAuth()

  if (initializing || initError) {
    return <SessionGate initializing={initializing} initError={initError} />
  }

  if (!user) {
    return <Navigate to="/login" replace />
  }

  if (user.role !== 'ADMIN') {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
