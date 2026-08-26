const API_BASE_URL = import.meta.env.VITE_API_BASE_URL as string

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function readErrorMessage(response: Response): Promise<string> {
  try {
    const data: unknown = await response.clone().json()
    if (data && typeof data === 'object') {
      const withDetail = data as { detail?: string; message?: string }
      if (withDetail.detail) return withDetail.detail
      if (withDetail.message) return withDetail.message
    }
  } catch {
    // response body wasn't JSON - fall through to statusText
  }
  return response.statusText || `Request failed with status ${response.status}`
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/** Token-agnostic request helper - the caller decides which access token (if any) to send. */
export async function rawRequest<T>(
  path: string,
  accessToken: string | null,
  options: RequestOptions = {},
): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? 'GET',
    credentials: 'include', // let the httpOnly refreshToken cookie travel automatically
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
    signal: options.signal,
  })

  if (response.status === 204) {
    return undefined as T
  }

  if (!response.ok) {
    throw new ApiError(response.status, await readErrorMessage(response))
  }

  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
