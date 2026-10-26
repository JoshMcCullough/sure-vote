import { FormEvent, useEffect, useState } from 'react'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { api, messageOf } from '@surevote/shared/api'
import { SelectField, TextField } from '@surevote/shared/components/Fields'
import { ErrorAlert, PageHeading, Panel, SuccessAlert } from '@surevote/shared/components/Page'
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
  const { t } = useTranslation()
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
      setError(messageOf(caught, t))
    }
  }

  function onEnroll(event: FormEvent) {
    event.preventDefault()
    void run(() => enrollPasskey(token.trim()), t('signIn.enrolled'))
  }

  function onDev(event: FormEvent) {
    event.preventDefault()
    void run(() => devSignIn(kind, id.trim(), tenantId.trim()), t('signIn.devDone'))
  }

  return (
    <>
      <PageHeading>{t('signIn.title')}</PageHeading>
      <p className="usa-intro">{t('signIn.intro')}</p>
      <ErrorAlert message={error} />
      <SuccessAlert message={notice} />
      {principal ? (
        <Panel title={t('signIn.signedInTitle')}>
          <p>{signedInText(principal, t)}</p>
          {principal.kind === 'visitor' ? null : (
            <p><Link to="/admin">{t('signIn.manageOfficials')}</Link></p>
          )}
          <div className="usa-button-group">
            {principal.kind === 'visitor' ? null : (
              <button
                type="button"
                className="usa-button"
                onClick={() => void run(addPasskey, t('signIn.passkeyAdded'))}
              >
                {t('signIn.addPasskey')}
              </button>
            )}
            <button
              type="button"
              className="usa-button usa-button--outline"
              onClick={() => void run(async () => {
                await signOut()
                return null
              }, t('signIn.signedOut'))}
            >
              {t('signIn.signOut')}
            </button>
          </div>
        </Panel>
      ) : null}
      <Panel title={t('signIn.passkeyTitle')}>
        <p>{t('signIn.passkeyBody')}</p>
        <button type="button" className="usa-button" onClick={() => void run(passkeySignIn, t('signIn.done'))}>
          {t('signIn.passkeyButton')}
        </button>
      </Panel>
      <Panel title={t('signIn.enrollTitle')}>
        <form className="usa-form maxw-none" onSubmit={onEnroll}>
          <p>{t('signIn.enrollBody')}</p>
          <TextField
            id="enroll-token"
            label={t('fields.enrollmentToken')}
            value={token}
            required
            autoComplete="off"
            spellCheck={false}
            onChange={setToken}
          />
          <button type="submit" className="usa-button margin-top-3" disabled={!token.trim()}>
            {t('signIn.enrollButton')}
          </button>
        </form>
      </Panel>
      {config.devSignIn ? (
        <Panel title={t('signIn.devTitle')}>
          <form className="usa-form maxw-none" onSubmit={onDev}>
            <p>{t('signIn.devBody')}</p>
            <SelectField
              id="dev-kind"
              label={t('fields.kind')}
              value={kind}
              options={['system-admin', 'official'].map((value) => ({ value, label: t('values.' + value) }))}
              onChange={setKind}
            />
            <TextField id="dev-id" label={t('fields.id')} value={id} required spellCheck={false} onChange={setId} />
            {kind === 'official' ? (
              <TextField
                id="dev-tenant"
                label={t('fields.tenant')}
                value={tenantId}
                spellCheck={false}
                onChange={setTenantId}
              />
            ) : null}
            <button type="submit" className="usa-button margin-top-3" disabled={!id.trim()}>
              {t('signIn.devButton')}
            </button>
          </form>
        </Panel>
      ) : null}
    </>
  )
}

function signedInText(principal: Principal, t: TFunction): string {
  const kind = t('values.' + principal.kind)
  if (principal.tenantId) {
    return t('signIn.signedInTenant', { name: principal.name, kind, tenant: principal.tenantId })
  }
  return t('signIn.signedIn', { name: principal.name, kind })
}
