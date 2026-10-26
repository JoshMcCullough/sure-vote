import type { TFunction } from 'i18next'

export async function api<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  }
  let payload: string | undefined
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
    payload = JSON.stringify(body)
  }
  const response = await fetch(path, { method, headers, body: payload, credentials: 'same-origin' })
  const text = await response.text()
  const parsed = text === '' ? null : JSON.parse(text) as unknown
  if (!response.ok) {
    throw new ApiError(errorText(parsed))
  }
  return parsed as T
}

export class ApiError extends Error {}

export class KeyedError extends Error {
  readonly key: string

  constructor(key: string) {
    super(key)
    this.key = key
  }
}

export function messageOf(error: unknown, t: TFunction): string {
  if (error instanceof KeyedError) {
    return t(error.key)
  }
  if (error instanceof ApiError && error.message) {
    return t('errors.server', { message: error.message })
  }
  if (error instanceof Error && error.message) {
    return t('errors.browser', { message: error.message })
  }
  return t('errors.requestFailed')
}

function errorText(parsed: unknown): string {
  if (parsed && typeof parsed === 'object' && 'error' in parsed) {
    const value = (parsed as { error?: unknown }).error
    if (typeof value === 'string' && value) {
      return value
    }
  }
  return ''
}

export type Created = {
  id?: string
  status?: string
  demonstration?: boolean
}

export function votingWindow(): { start: string; end: string } {
  const start = new Date(Date.now() - 60 * 60 * 1000).toISOString()
  const end = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
  return { start, end }
}
