import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, rawRequest } from '../api/client'
import type { RequestOptions } from '../api/client'
import type { AuthResponseDto, Role } from '../api/types'

interface AuthUser {
  email: string
  role: Role
}

interface AuthContextValue {
  user: AuthUser | null
  /** True while the initial silent-refresh-on-load check is running. */
  initializing: boolean
  /** Set when the initial silent refresh couldn't complete at all (e.g. timed out) - distinct from "not logged in". */
  initError: string | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Authenticated fetch: attaches the access token and retries once through /auth/refresh on 401. */
  apiFetch: <T>(path: string, options?: RequestOptions) => Promise<T>
}

const AuthContext = createContext<AuthContextValue | null>(null)

const INITIAL_REFRESH_TIMEOUT_MS = 10_000

function isAbortError(err: unknown): boolean {
  return err instanceof DOMException && err.name === 'AbortError'
}

function toUser(dto: AuthResponseDto): AuthUser {
  return { email: dto.email, role: dto.role }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [initializing, setInitializing] = useState(true)
  const [initError, setInitError] = useState<string | null>(null)
  const accessTokenRef = useRef<string | null>(null)
  const isMountedRef = useRef(true)
  const initStartedRef = useRef(false)
  const navigate = useNavigate()

  const setSession = useCallback((dto: AuthResponseDto) => {
    accessTokenRef.current = dto.accessToken
    setUser(toUser(dto))
  }, [])

  const clearSession = useCallback(() => {
    accessTokenRef.current = null
    setUser(null)
  }, [])

  const apiFetch = useCallback(
    async <T,>(path: string, options: RequestOptions = {}): Promise<T> => {
      try {
        return await rawRequest<T>(path, accessTokenRef.current, options)
      } catch (err) {
        if (!(err instanceof ApiError) || err.status !== 401) {
          throw err
        }

        try {
          const refreshed = await rawRequest<AuthResponseDto>('/auth/refresh', null, { method: 'POST' })
          setSession(refreshed)
          return await rawRequest<T>(path, refreshed.accessToken, options)
        } catch {
          clearSession()
          navigate('/login', { replace: true })
          throw err
        }
      }
    },
    [clearSession, navigate, setSession],
  )

  useEffect(() => {
    isMountedRef.current = true

    // Guards against React StrictMode's dev-only double-invoke of mount effects: without
    // this, two real /auth/refresh calls fire concurrently, racing to rotate the same
    // refresh token cookie. Refs survive the synthetic mount->cleanup->mount cycle, so this
    // still lets exactly one real request through.
    if (!initStartedRef.current) {
      initStartedRef.current = true

      const controller = new AbortController()
      const timeoutId = setTimeout(() => controller.abort(), INITIAL_REFRESH_TIMEOUT_MS)

      rawRequest<AuthResponseDto>('/auth/refresh', null, { method: 'POST', signal: controller.signal })
        .then((dto) => {
          if (isMountedRef.current) setSession(dto)
        })
        .catch((err: unknown) => {
          if (!isMountedRef.current) return
          if (isAbortError(err)) {
            setInitError('No se pudo conectar con el servidor. Recarga la página para volver a intentarlo.')
          }
          // otherwise: no valid session cookie yet - that's fine, user just isn't logged in
        })
        .finally(() => {
          clearTimeout(timeoutId)
          if (isMountedRef.current) setInitializing(false)
        })
    }

    return () => {
      isMountedRef.current = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      const dto = await rawRequest<AuthResponseDto>('/auth/login', null, {
        method: 'POST',
        body: { email, password },
      })
      setSession(dto)
    },
    [setSession],
  )

  const register = useCallback(async (email: string, password: string) => {
    await rawRequest('/auth/register', null, { method: 'POST', body: { email, password } })
  }, [])

  const logout = useCallback(async () => {
    try {
      await rawRequest('/auth/logout', null, { method: 'POST' })
    } finally {
      clearSession()
      navigate('/login', { replace: true })
    }
  }, [clearSession, navigate])

  return (
    <AuthContext.Provider value={{ user, initializing, initError, login, register, logout, apiFetch }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
