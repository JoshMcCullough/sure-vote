import { FormEvent, useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { api, messageOf } from '@surevote/shared/api'
import { SelectField, TextField } from '@surevote/shared/components/Fields'
import { ErrorAlert, PageHeading, Panel } from '@surevote/shared/components/Page'
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
  const { t } = useTranslation()
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
    refresh(principal, tenantId).catch((caught) => setError(messageOf(caught, t)))
  }, [principal, tenantId, refresh, t])

  async function act(work: () => Promise<string | null>) {
    setError(null)
    try {
      setToken(await work())
      await refresh(principal, tenantId)
    } catch (caught) {
      setError(messageOf(caught, t))
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
    return (
      <>
        <PageHeading>{t('admin.title')}</PageHeading>
        <p role="status">{t('app.loading')}</p>
      </>
    )
  }
  if (!principal || principal.kind === 'visitor') {
    return (
      <>
        <PageHeading>{t('admin.title')}</PageHeading>
        <p>
          <Link to="/sign-in">{t('admin.signInLink')}</Link>
        </p>
        <p>{t('admin.signInBody')}</p>
      </>
    )
  }

  const ready = principal.kind === 'official' || Boolean(tenantId)
  const tenantOptions = [
    { value: '', label: t('admin.chooseTenant') },
    ...tenants.map((tenant) => ({
      value: tenant.id,
      label: t('admin.tenantOption', {
        name: tenant.name,
        kind: t('values.' + tenant.kind, { defaultValue: tenant.kind }),
        status: t('values.' + tenant.status, { defaultValue: tenant.status }),
      }),
    })),
  ]

  return (
    <>
      <PageHeading>{t('admin.title')}</PageHeading>
      <p className="usa-intro">{t('admin.intro')}</p>
      <ErrorAlert message={error} />
      {token ? (
        <div className="usa-alert usa-alert--success margin-y-2" role="status">
          <div className="usa-alert__body">
            <h2 className="usa-alert__heading">{t('admin.tokenTitle')}</h2>
            <p className="usa-alert__text">{t('admin.tokenBody')}</p>
            <p className="sv-access-code" translate="no">{token}</p>
          </div>
        </div>
      ) : null}
      {principal.kind === 'system-admin' ? (
        <Panel title={t('admin.tenantTitle')}>
          <SelectField
            id="admin-tenant"
            label={t('fields.tenant')}
            value={tenantId}
            options={tenantOptions}
            onChange={setTenantId}
          />
        </Panel>
      ) : null}
      <Panel title={t('admin.addTitle')}>
        <form className="usa-form maxw-none" onSubmit={onCreate}>
          <TextField id="admin-name" label={t('fields.name')} value={name} required onChange={setName} />
          <button type="submit" className="usa-button margin-top-3" disabled={!ready || !name.trim()}>
            {t('admin.add')}
          </button>
        </form>
      </Panel>
      <Panel title={t('admin.listTitle')}>
        {officials.length === 0 ? <p>{t('admin.none')}</p> : (
          <table className="usa-table usa-table--borderless usa-table--stacked-header width-full">
            <caption className="usa-sr-only">{t('admin.listTitle')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('fields.name')}</th>
                <th scope="col">{t('fields.status')}</th>
                <th scope="col">{t('admin.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {officials.map((official) => (
                <tr key={official.id}>
                  <th scope="row" data-label={t('fields.name')}>{official.name}</th>
                  <td data-label={t('fields.status')}>
                    {t('values.' + official.status, { defaultValue: official.status })}
                  </td>
                  <td data-label={t('admin.actions')}>
                    <div className="usa-button-group">
                      {official.id === principal.id ? null : (
                        <button
                          type="button"
                          className="usa-button usa-button--outline"
                          onClick={() => setStatus(official, official.status === 'active' ? 'disabled' : 'active')}
                        >
                          {official.status === 'active'
                            ? t('admin.disable', { name: official.name })
                            : t('admin.enable', { name: official.name })}
                        </button>
                      )}
                      <button type="button" className="usa-button usa-button--outline" onClick={() => reenroll(official)}>
                        {t('admin.reenroll', { name: official.name })}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Panel>
    </>
  )
}
