import { FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, messageOf } from '../api'
import {
  addPasskey,
  currentSession,
  devSignIn,
  enrollPasskey,
  passkeySignIn,
  Principal,
  signOut,
} from '../session'

type AuthConfig = {
  devSignIn: boolean
}

export function SignIn() {
  const [principal, setPrincipal] = useState<Principal | null>(null)
  const [config, setConfig] = useState<AuthConfig>({ devSignIn: false })
  const [token, setToken] = useState('')
  const [kind, setKind] = useState('system-admin')
  const [id, setId] = useState('')
  const [tenantId, setTenantId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    let gone = false
    async function load() {
      const [session, settings] = await Promise.all([
        currentSession(),
        api<AuthConfig>('GET', '/auth/config').catch(() => ({ devSignIn: false })),
      ])
      if (!gone) {
        setPrincipal(session)
        setConfig(settings)
      }
    }
    void load()
    return () => {
      gone = true
    }
  }, [])

  async function run(work: () => Promise<Principal | null>, message: string) {
    setError(null)
    setNotice(null)
    try {
      setPrincipal(await work())
      setNotice(message)
    } catch (caught) {
      setError(messageOf(caught))
    }
  }

  function onEnroll(event: FormEvent) {
    event.preventDefault()
    void run(() => enrollPasskey(token.trim()), 'Passkey enrolled. You are signed in.')
  }

  function onDev(event: FormEvent) {
    event.preventDefault()
    void run(() => devSignIn(kind, id.trim(), tenantId.trim()), 'Signed in without a passkey.')
  }

  return (
    <>
      <h1>Sign in</h1>
      <p>Officials and system admins sign in with a passkey. A security key or a platform passkey both work.</p>
      {error ? <p className="banner" role="alert">{error}</p> : null}
      {notice ? <p className="notice">{notice}</p> : null}
      {principal ? (
        <section className="card">
          <h2>Signed in</h2>
          <p>
            {principal.name} ({principal.kind}){principal.tenantId ? ' in tenant ' + principal.tenantId : ''}.
          </p>
          {principal.kind === 'visitor' ? null : (
            <p>
              <Link to="/admin">Manage officials</Link>
            </p>
          )}
          <div className="actions">
            {principal.kind === 'visitor' ? null : (
              <button type="button" onClick={() => void run(addPasskey, 'Another passkey was added.')}>
                Add another passkey
              </button>
            )}
            <button type="button" onClick={() => void run(async () => { await signOut(); return null }, 'Signed out.')}>
              Sign out
            </button>
          </div>
        </section>
      ) : null}
      <section className="card">
        <h2>Passkey</h2>
        <p>Use a passkey you already enrolled.</p>
        <button type="button" onClick={() => void run(passkeySignIn, 'Signed in.')}>Sign in with a passkey</button>
      </section>
      <form className="card" onSubmit={onEnroll}>
        <h2>Enroll a passkey</h2>
        <p>Paste the one-time enrollment token you were given. It works once and expires after 24 hours.</p>
        <label>
          Enrollment token
          <input value={token} onChange={(event) => setToken(event.target.value)} autoComplete="off" />
        </label>
        <button type="submit" disabled={!token.trim()}>Enroll and sign in</button>
      </form>
      {config.devSignIn ? (
        <form className="card" onSubmit={onDev}>
          <h2>Development sign-in</h2>
          <p>This host allows sign-in without a passkey. A production host turns it off.</p>
          <label>
            Kind
            <select value={kind} onChange={(event) => setKind(event.target.value)}>
              <option value="system-admin">system-admin</option>
              <option value="official">official</option>
            </select>
          </label>
          <label>
            Id
            <input value={id} onChange={(event) => setId(event.target.value)} />
          </label>
          {kind === 'official' ? (
            <label>
              Tenant
              <input value={tenantId} onChange={(event) => setTenantId(event.target.value)} />
            </label>
          ) : null}
          <button type="submit" disabled={!id.trim()}>Sign in</button>
        </form>
      ) : null}
    </>
  )
}
