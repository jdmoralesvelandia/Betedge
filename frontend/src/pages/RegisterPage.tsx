import { useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

export function RegisterPage() {
  const { register, login } = useAuth()
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
      await register(email, password)
      await login(email, password)
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('Ya existe una cuenta con ese email.')
      } else if (err instanceof ApiError && err.status === 400) {
        setError('Revisa el email y que la contraseña tenga al menos 8 caracteres.')
      } else {
        setError('No se pudo completar el registro.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex items-center justify-center gap-2">
          <span className="inline-block h-2.5 w-2.5 rounded-full bg-series-1" aria-hidden="true" />
          <span className="text-lg font-semibold text-ink">BetEdge</span>
        </div>

        <form onSubmit={handleSubmit} className="rounded-xl border border-border bg-surface p-6">
          <h1 className="mb-4 text-lg font-semibold text-ink">Crear cuenta</h1>

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
              minLength={8}
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full rounded-md border border-border bg-surface-2 px-3 py-2 text-sm text-ink outline-none focus:border-series-1"
            />
            <span className="mt-1 block text-xs text-ink-faint">Mínimo 8 caracteres.</span>
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
            {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
          </button>

          <p className="mt-4 text-center text-sm text-ink-faint">
            ¿Ya tienes cuenta?{' '}
            <Link to="/login" className="text-series-1 hover:underline">
              Inicia sesión
            </Link>
          </p>
        </form>
      </div>
    </div>
  )
}
