import { FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { api, messageOf } from '@surevote/shared/api'
import { TextField } from '@surevote/shared/components/Fields'
import { ErrorAlert, PageHeading, Panel } from '@surevote/shared/components/Page'

type Found = {
  ballot: {
    electionId: string
  }
}

export function Home() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [passphrase, setPassphrase] = useState('')
  const [electionId, setElectionId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [codeError, setCodeError] = useState<string | null>(null)
  const [electionError, setElectionError] = useState<string | null>(null)

  async function openBallot(event: FormEvent) {
    event.preventDefault()
    setError(null)
    if (!code.trim()) {
      setCodeError(t('form.required', { field: t('fields.code') }))
      document.getElementById('home-code')?.focus()
      return
    }
    setCodeError(null)
    try {
      const found = await api<Found>('POST', '/lookup', { code, passphrase })
      navigate('/elections/' + found.ballot.electionId, { state: { code, passphrase } })
    } catch (caught) {
      setError(messageOf(caught, t))
    }
  }

  function openLookup(event: FormEvent) {
    event.preventDefault()
    const id = electionId.trim()
    if (!id) {
      setElectionError(t('form.required', { field: t('fields.electionLookup') }))
      document.getElementById('home-election')?.focus()
      return
    }
    setElectionError(null)
    navigate('/elections/' + id)
  }

  return (
    <>
      <PageHeading>{t('voter.title')}</PageHeading>
      <p className="usa-intro">{t('voter.intro')}</p>
      <div className="grid-row grid-gap">
        <div className="grid-col-12 desktop:grid-col-8">
          <Panel title={t('home.lookupTitle')}>
            <p>{t('home.lookupBody')}</p>
            <ErrorAlert message={error} />
            <form className="usa-form maxw-none" noValidate onSubmit={(event) => void openBallot(event)}>
              <TextField
                id="home-code"
                label={t('fields.code')}
                value={code}
                required
                error={codeError}
                autoComplete="off"
                spellCheck={false}
                onChange={setCode}
              />
              <TextField
                id="home-passphrase"
                label={t('fields.passphrase')}
                hint={t('home.passphraseHint')}
                value={passphrase}
                type="password"
                autoComplete="off"
                onChange={setPassphrase}
              />
              <button type="submit" className="usa-button margin-top-3">{t('home.openBallot')}</button>
            </form>
            <form className="usa-form maxw-none margin-top-4" noValidate onSubmit={openLookup}>
              <TextField
                id="home-election"
                label={t('fields.electionLookup')}
                value={electionId}
                error={electionError}
                spellCheck={false}
                onChange={setElectionId}
              />
              <button type="submit" className="usa-button usa-button--outline margin-top-3">
                {t('home.openLookup')}
              </button>
            </form>
          </Panel>
        </div>
      </div>
    </>
  )
}
