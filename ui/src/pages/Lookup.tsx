import { FormEvent, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useLocation, useParams } from 'react-router-dom'
import { api, messageOf } from '../api'
import { TextAreaField, TextField } from '../components/Fields'
import { ErrorAlert, PageHeading, Panel, SuccessAlert } from '../components/Page'
import { formatDateTime, formatNumber } from '../i18n'

type Election = {
  name: string
  status: string
  demonstration: boolean
}

type Vote = {
  raceCandidateId: string
  value: string
}

type LookupResult = {
  ballot: {
    type: string
    timestamp: string
  }
  votes: Vote[]
}

type Dispute = {
  id: string
  status: string
  statement: string | null
}

type DisputeList = {
  disputes: Dispute[]
}

export function Lookup() {
  const { t, i18n } = useTranslation()
  const params = useParams()
  const location = useLocation()
  const electionId = params.electionId ?? ''
  const [election, setElection] = useState<Election | null>(null)
  const [count, setCount] = useState<number | null>(null)
  const [code, setCode] = useState(secretFrom(location.state, 'code'))
  const [passphrase, setPassphrase] = useState(secretFrom(location.state, 'passphrase'))
  const [statement, setStatement] = useState('')
  const [result, setResult] = useState<LookupResult | null>(null)
  const [claims, setClaims] = useState<Dispute[]>([])
  const [error, setError] = useState<string | null>(null)
  const [codeError, setCodeError] = useState<string | null>(null)
  const [filed, setFiled] = useState<string | null>(null)

  useEffect(() => {
    let gone = false
    async function load() {
      try {
        const shown = await api<Election>('GET', '/elections/' + electionId)
        const counted = await api<{ count: number }>('GET', '/elections/' + electionId + '/disputes/count')
        if (!gone) {
          setElection(shown)
          setCount(counted.count)
          setError(null)
        }
      } catch (caught) {
        if (!gone) {
          setError(messageOf(caught, t))
        }
      }
    }
    void load()
    return () => {
      gone = true
    }
  }, [electionId, t])

  async function lookup(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!code.trim()) {
      setCodeError(t('form.required', { field: t('fields.code') }))
      document.getElementById('lookup-code')?.focus()
      return
    }
    setCodeError(null)
    try {
      const found = await api<LookupResult>('POST', '/elections/' + electionId + '/lookup', {
        code,
        passphrase,
      })
      setResult(found)
    } catch (caught) {
      setResult(null)
      setError(messageOf(caught, t))
    }
  }

  async function fileClaim(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setFiled(null)
    try {
      await api('POST', '/elections/' + electionId + '/disputes', {
        code,
        passphrase,
        statement,
      })
      const listed = await api<DisputeList>('POST', '/elections/' + electionId + '/disputes/lookup', {
        code,
        passphrase,
      })
      setClaims(listed.disputes)
      const counted = await api<{ count: number }>('GET', '/elections/' + electionId + '/disputes/count')
      setCount(counted.count)
      setFiled(t('lookup.filed'))
    } catch (caught) {
      setError(messageOf(caught, t))
    }
  }

  function clearSecret() {
    setCode('')
    setPassphrase('')
    setResult(null)
    setClaims([])
    setFiled(null)
  }

  return (
    <>
      <PageHeading>{election ? election.name : t('lookup.title')}</PageHeading>
      {election ? (
        <p>{t('lookup.status', { status: t('values.' + election.status, { defaultValue: election.status }) })}</p>
      ) : null}
      {election?.demonstration ? (
        <div className="usa-alert usa-alert--warning usa-alert--slim margin-y-2">
          <div className="usa-alert__body">
            <p className="usa-alert__text">{t('lookup.demonstration')}</p>
          </div>
        </div>
      ) : null}
      {count !== null ? <p>{t('lookup.disputes', { count, formatted: formatNumber(count, i18n.language) })}</p> : null}
      <ErrorAlert message={error} />
      <Panel title={t('lookup.openTitle')}>
        <form className="usa-form maxw-none" noValidate onSubmit={(event) => void lookup(event)}>
          <p>{t('lookup.openBody')}</p>
          <TextField
            id="lookup-code"
            label={t('fields.code')}
            value={code}
            required
            error={codeError}
            autoComplete="off"
            spellCheck={false}
            onChange={setCode}
          />
          <TextField
            id="lookup-passphrase"
            label={t('fields.passphrase')}
            hint={t('home.passphraseHint')}
            value={passphrase}
            type="password"
            autoComplete="off"
            onChange={setPassphrase}
          />
          <div className="usa-button-group margin-top-3">
            <button type="submit" className="usa-button">{t('lookup.lookUp')}</button>
            <button type="button" className="usa-button usa-button--outline" onClick={clearSecret}>
              {t('lookup.clear')}
            </button>
          </div>
        </form>
      </Panel>
      {result ? (
        <Panel title={t('lookup.recordedTitle')}>
          <p>
            {t('lookup.recorded', {
              type: t('values.' + result.ballot.type, { defaultValue: result.ballot.type }),
              time: formatDateTime(result.ballot.timestamp, i18n.language),
            })}
          </p>
          <h3>{t('lookup.votesTitle', { count: result.votes.length })}</h3>
          <ul className="usa-list">
            {result.votes.map((vote) => (
              <li key={vote.raceCandidateId}>{vote.value}</li>
            ))}
          </ul>
          <form className="usa-form maxw-none" onSubmit={(event) => void fileClaim(event)}>
            <TextAreaField
              id="lookup-statement"
              label={t('fields.statement')}
              hint={t('lookup.statementHint')}
              value={statement}
              onChange={setStatement}
            />
            <button type="submit" className="usa-button margin-top-3">{t('lookup.file')}</button>
          </form>
          <SuccessAlert message={filed} />
        </Panel>
      ) : null}
      {claims.length > 0 ? (
        <Panel title={t('lookup.claimsTitle')}>
          <ul className="usa-list">
            {claims.map((claim) => (
              <li key={claim.id}>
                {claim.statement
                  ? t('lookup.claimWithStatement', {
                    status: t('values.' + claim.status, { defaultValue: claim.status }),
                    statement: claim.statement,
                  })
                  : t('values.' + claim.status, { defaultValue: claim.status })}
              </li>
            ))}
          </ul>
        </Panel>
      ) : null}
    </>
  )
}

function secretFrom(state: unknown, name: 'code' | 'passphrase'): string {
  if (state && typeof state === 'object' && name in state) {
    const value = (state as Record<string, unknown>)[name]
    if (typeof value === 'string') {
      return value
    }
  }
  return ''
}
