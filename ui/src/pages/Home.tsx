import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'

export function Home() {
  const navigate = useNavigate()
  const [electionId, setElectionId] = useState('')

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
        <form onSubmit={openLookup}>
          <label>
            Election id
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
