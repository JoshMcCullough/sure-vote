import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { api, messageOf } from '../api'

type Found = {
  ballot: {
    electionId: string
  }
}

export function Home() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [passphrase, setPassphrase] = useState('')
  const [electionId, setElectionId] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function openBallot(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const found = await api<Found>('POST', '/lookup', { code, passphrase })
      navigate('/elections/' + found.ballot.electionId, { state: { code, passphrase } })
    } catch (caught) {
      setError(messageOf(caught))
    }
  }

  function openLookup(event: FormEvent) {
    event.preventDefault()
    const id = electionId.trim()
    if (id) {
      navigate('/elections/' + id)
    }
  }

  return (
    <>
      <h1>Open a ballot, or configure an election</h1>
      <p>
        SureVote keeps an election authority's record and a public ballot ledger.
        This app has a public lookup and a workspace for the parts of an election.
      </p>
      <section className="card">
        <h2>Public lookup</h2>
        <p>
          Open one ballot with its access code. A mail-in ballot also needs the viewing passphrase.
          The page does not change the ballot. Checking a published chain does not require this app.
        </p>
        {error ? <p className="banner" role="alert">{error}</p> : null}
        <form onSubmit={(event) => void openBallot(event)}>
          <label>
            Access code
            <input value={code} autoComplete="off" onChange={(event) => setCode(event.target.value)} />
          </label>
          <label>
            Viewing passphrase
            <input
              value={passphrase}
              type="password"
              autoComplete="off"
              onChange={(event) => setPassphrase(event.target.value)}
            />
          </label>
          <div className="actions">
            <button type="submit">Open my ballot</button>
          </div>
        </form>
        <form onSubmit={openLookup}>
          <label>
            Or open an election by id
            <input value={electionId} onChange={(event) => setElectionId(event.target.value)} />
          </label>
          <div className="actions">
            <button type="submit">Open lookup</button>
          </div>
        </form>
      </section>
      <section className="card">
        <h2>Workspace</h2>
        <p>
          Configure a tenant, geography, contests, and recording places.
          On a showcase tenant, activate a demonstration, cast one ballot, and close it.
          The access code appears once and is not saved in this browser.
        </p>
        <p>
          This browser does not hold a production operator, import, viewing, or device key.
          On a production tenant, activate and close take a signature made outside this app.
        </p>
        <div className="actions">
          <Link className="button" to="/workspace">Open the workspace</Link>
        </div>
      </section>
    </>
  )
}
