import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'

const DEMO_EMAIL = 'demo@betedge.com'
const DEMO_PASSWORD = 'Demo1234!'

// Not a secret - Google's own docs say the OAuth client id is safe in client-side code. Same
// value the backend requires as its `aud` claim (see application.yml's google.oauth.client-id).
const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID as string

/**
 * The slice of window.google Google Identity Services actually attaches - typed by hand (no
 * @types package for this) rather than reaching for `any`. See index.html for where the script
 * itself is loaded from - the official Google-hosted one, never bundled/reimplemented.
 */
interface GoogleIdentityServices {
  accounts: {
    id: {
      initialize(config: { client_id: string; callback: (response: { credential: string }) => void }): void
      renderButton(
        parent: HTMLElement,
        options: { theme?: string; size?: string; width?: number; text?: string },
      ): void
    }
  }
}

function getGoogleIdentityServices(): GoogleIdentityServices | undefined {
  return (window as unknown as { google?: GoogleIdentityServices }).google
}

export function LoginPage() {
  const { login, loginWithGoogle } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const googleButtonRef = useRef<HTMLDivElement>(null)
  // See the Google-button useEffect's own comment below for what these two guard against.
  const googleInitStartedRef = useRef(false)
  const isMountedRef = useRef(true)

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

  // Renders the official Google button into googleButtonRef once the Identity Services script
  // (loaded in index.html) has actually finished loading - it's tagged `async`, so it can finish
  // before or after this effect runs; a short poll (200 tries x 100ms = 20s ceiling) covers both
  // orders without assuming either one.
  //
  // Guards against React StrictMode's dev-only double-invoke of mount effects - same pattern as
  // AuthContext.tsx's own initStartedRef/isMountedRef there, for /auth/refresh (see its own
  // comment). Without googleInitStartedRef, the second invocation would start a SECOND polling
  // chain and call initialize()/renderButton() a second time. isMountedRef is revived (set true
  // again) at the top of EVERY invocation - including the second, real one - rather than being a
  // one-way "cancelled" flag scoped to the first invocation's own closure: if Identity Services
  // wasn't loaded yet when the first invocation's poll got scheduled, that pending retry must
  // still complete once the second (real, lasting) mount is the one left standing - a plain
  // per-closure `cancelled` boolean would permanently kill it instead, and the button would never
  // render at all.
  useEffect(() => {
    isMountedRef.current = true
    let attempts = 0

    function tryRender() {
      if (!isMountedRef.current) return
      const google = getGoogleIdentityServices()
      if (google) {
        google.accounts.id.initialize({ client_id: GOOGLE_CLIENT_ID, callback: handleGoogleCredential })
        if (googleButtonRef.current) {
          google.accounts.id.renderButton(googleButtonRef.current, {
            theme: 'outline',
            size: 'large',
            width: 320,
            text: 'continue_with',
          })
        }
        return
      }
      if (attempts++ < 200) {
        setTimeout(tryRender, 100)
      }
    }

    if (!googleInitStartedRef.current) {
      googleInitStartedRef.current = true
      tryRender()
    }

    return () => {
      isMountedRef.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleGoogleCredential(response: { credential: string }) {
    setError(null)
    setSubmitting(true)
    try {
      await loginWithGoogle(response.credential)
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setError('Esta cuenta usa otro método de acceso.')
      } else {
        setError('No se pudo iniciar sesión con Google.')
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

        <div className="rounded-xl border border-border bg-surface p-6">
          <h1 className="mb-4 text-lg font-semibold text-ink">Iniciar sesión</h1>

          <div ref={googleButtonRef} className="mb-4 flex justify-center" />

          <div className="mb-4 flex items-center gap-3">
            <span className="h-px flex-1 bg-border" />
            <span className="text-xs text-ink-faint">o con tu contraseña</span>
            <span className="h-px flex-1 bg-border" />
          </div>

          <form onSubmit={handleSubmit}>
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
          </form>
        </div>
      </div>
    </div>
  )
}
