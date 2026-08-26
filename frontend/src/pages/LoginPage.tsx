import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

const DEMO_EMAIL = 'demo@betedge.com'
const DEMO_PASSWORD = 'Demo1234!'

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? 'Email o contraseña incorrectos.' : 'No se pudo conectar con el servidor.')
    } finally {
      setSubmitting(false)
    }
  }

  function fillDemoAccount() {
    setEmail(DEMO_EMAIL)
    setPassword(DEMO_PASSWORD)
    setError(null)
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2">
          <span className="inline-block h-2.5 w-2.5 rounded-full bg-series-1" aria-hidden="true" />
          <span className="text-lg font-semibold text-ink">BetEdge</span>
        </div>

        <button
          type="button"
          onClick={fillDemoAccount}
          className="mb-4 flex w-full items-center justify-between gap-2 rounded-lg border border-series-1/40 bg-series-1/10 px-4 py-3 text-left text-sm text-ink transition-colors hover:border-series-1/70"
        >
          <span>
            <span className="font-medium">Prueba con la cuenta demo</span>
            <span className="block text-xs text-ink-faint">{DEMO_EMAIL}</span>
          </span>
          <span className="text-series-1">→</span>
        </button>

        <form onSubmit={handleSubmit} className="rounded-xl border border-border bg-surface p-6">
          <h1 className="mb-4 text-lg font-semibold text-ink">Iniciar sesión</h1>

          <label className="mb-3 block">
            <span className="mb-1 block text-xs font-medium text-ink-soft">Email</span>
            <input
              type="email"
              required
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink outline-none focus:border-series-1"
            />
          </label>

          <label className="mb-4 block">
            <span className="mb-1 block text-xs font-medium text-ink-soft">Contraseña</span>
            <input
              type="password"
              required
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink outline-none focus:border-series-1"
            />
          </label>

          {error && (
            <p className="mb-4 rounded-md bg-critical-soft px-3 py-2 text-sm text-critical" role="alert">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="w-full rounded-md bg-series-1 px-3 py-2 text-sm font-semibold text-white transition-opacity hover:opacity-90 disabled:opacity-50"
          >
            {submitting ? 'Entrando…' : 'Entrar'}
          </button>

          <p className="mt-4 text-center text-sm text-ink-faint">
            ¿No tienes cuenta?{' '}
            <Link to="/register" className="text-series-1 hover:underline">
              Regístrate
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}
