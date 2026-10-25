import { FormEvent, useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, messageOf } from '../api'
import { currentSession, Principal } from '../session'

type Tenant = {
  id: string
  name: string
  kind: string
  status: string
}

type Official = {
  id: string
  tenantId: string
  name: string
  status: string
}

type Enrollment = {
  official?: Official
  enrollmentToken: string
}

export function Admin() {
  const [principal, setPrincipal] = useState<Principal | null>(null)
  const [loaded, setLoaded] = useState(false)
  const [tenants, setTenants] = useState<Tenant[]>([])
  const [tenantId, setTenantId] = useState('')
  const [officials, setOfficials] = useState<Official[]>([])
  const [name, setName] = useState('')
  const [token, setToken] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const base = principal?.kind === 'system-admin' ? '/system/tenants/' + tenantId + '/officials' : '/officials'

  const refresh = useCallback(async (who: Principal | null, tenant: string) => {
    if (!who || who.kind === 'visitor') {
      return
    }
    if (who.kind === 'system-admin') {
      const listed = await api<{ tenants: Tenant[] }>('GET', '/system/tenants')
      setTenants(listed.tenants)
      if (!tenant) {
        setOfficials([])
        return
      }
      const rows = await api<{ officials: Official[] }>('GET', '/system/tenants/' + tenant + '/officials')
      setOfficials(rows.officials)
      return
    }
    const rows = await api<{ officials: Official[] }>('GET', '/officials')
    setOfficials(rows.officials)
  }, [])

  useEffect(() => {
    let gone = false
    async function load() {
      const who = await currentSession()
      if (!gone) {
        setPrincipal(who)
        setLoaded(true)
      }
    }
    void load()
    return () => {
      gone = true
    }
  }, [])

  useEffect(() => {
    refresh(principal, tenantId).catch((caught) => setError(messageOf(caught)))
  }, [principal, tenantId, refresh])

  async function act(work: () => Promise<string | null>) {
    setError(null)
    try {
      setToken(await work())
      await refresh(principal, tenantId)
    } catch (caught) {
      setError(messageOf(caught))
    }
  }

  function onCreate(event: FormEvent) {
    event.preventDefault()
    void act(async () => {
      const created = await api<Enrollment>('POST', base, { name })
      setName('')
      return created.enrollmentToken
    })
  }

  function setStatus(official: Official, status: string) {
    const path = base + '/' + official.id
    void act(async () => {
      await api<Official>('PATCH', path, { status })
      return null
    })
  }

  function reenroll(official: Official) {
    const path = base + '/' + official.id + '/enrollments'
    void act(async () => (await api<Enrollment>('POST', path)).enrollmentToken)
  }

  if (!loaded) {
    return <p>Loading.</p>
  }
  if (!principal || principal.kind === 'visitor') {
    return (
      <>
        <h1>Officials</h1>
        <p>
          <Link to="/sign-in">Sign in</Link> as an official or a system admin to manage officials.
        </p>
      </>
    )
  }

  const ready = principal.kind === 'official' || Boolean(tenantId)

  return (
    <>
      <h1>Officials</h1>
      <p>
        An official gets a one-time enrollment token and enrolls a passkey on the sign-in page.
        A disabled official is signed out at once.
      </p>
      {error ? <p className="banner" role="alert">{error}</p> : null}
      {token ? (
        <p className="notice">
          Enrollment token, shown once: <code>{token}</code>
        </p>
      ) : null}
      {principal.kind === 'system-admin' ? (
        <section className="card">
          <h2>Tenant</h2>
          <label>
            Tenant
            <select value={tenantId} onChange={(event) => setTenantId(event.target.value)}>
              <option value="">Choose a tenant</option>
              {tenants.map((tenant) => (
                <option key={tenant.id} value={tenant.id}>
                  {tenant.name} ({tenant.kind}, {tenant.status})
                </option>
              ))}
            </select>
          </label>
        </section>
      ) : null}
      <form className="card" onSubmit={onCreate}>
        <h2>Add an official</h2>
        <label>
          Name
          <input value={name} onChange={(event) => setName(event.target.value)} />
        </label>
        <button type="submit" disabled={!ready || !name.trim()}>Add</button>
      </form>
      <section className="card">
        <h2>Officials</h2>
        {officials.length === 0 ? <p>None yet.</p> : null}
        <ul>
          {officials.map((official) => (
            <li key={official.id}>
              {official.name} ({official.status}){' '}
              {official.id === principal.id ? null : (
                <button
                  type="button"
                  onClick={() => setStatus(official, official.status === 'active' ? 'disabled' : 'active')}
                >
                  {official.status === 'active' ? 'Disable' : 'Enable'}
                </button>
              )}{' '}
              <button type="button" onClick={() => reenroll(official)}>New enrollment token</button>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}
