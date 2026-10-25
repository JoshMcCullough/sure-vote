import { FormEvent, useEffect, useState } from 'react'
import { useLocation, useParams } from 'react-router-dom'
import { api, messageOf } from '../api'

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
          setError(messageOf(caught))
        }
      }
    }
    void load()
    return () => {
      gone = true
    }
  }, [electionId])

  async function lookup(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const found = await api<LookupResult>('POST', '/elections/' + electionId + '/lookup', {
        code,
        passphrase,
      })
      setResult(found)
    } catch (caught) {
      setResult(null)
      setError(messageOf(caught))
    }
  }

  async function fileClaim(event: FormEvent) {
    event.preventDefault()
    setError(null)
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
    } catch (caught) {
      setError(messageOf(caught))
    }
  }

  function clearSecret() {
    setCode('')
    setPassphrase('')
    setResult(null)
    setClaims([])
  }

  return (
    <>
      <h1>{election ? election.name : 'Ballot lookup'}</h1>
      {election ? <p>Status {election.status}</p> : null}
      {election?.demonstration ? (
        <p className="demo">This is a demonstration. It has no legal effect.</p>
      ) : null}
      {count !== null ? <p>Disputes filed: {count}</p> : null}
      {error ? <p className="banner" role="alert">{error}</p> : null}
      <form className="card" onSubmit={(event) => void lookup(event)}>
        <h2>Open your ballot</h2>
        <p>The code stays on this page until you leave or clear it. It is not put in the address.</p>
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
          <button type="submit">Look up</button>
          <button type="button" className="secondary" onClick={clearSecret}>Clear</button>
        </div>
      </form>
      {result ? (
        <section className="card">
          <h2>Recorded ballot</h2>
          <p>Type {result.ballot.type}. Sealed {result.ballot.timestamp}. There is no edit.</p>
          <ul>
            {result.votes.map((vote) => (
              <li key={vote.raceCandidateId}>{vote.value}</li>
            ))}
          </ul>
          <form onSubmit={(event) => void fileClaim(event)}>
            <label>
              What looks wrong
              <textarea value={statement} onChange={(event) => setStatement(event.target.value)} />
            </label>
            <div className="actions">
              <button type="submit">File a dispute</button>
            </div>
          </form>
        </section>
      ) : null}
      {claims.length > 0 ? (
        <section className="card">
          <h2>Your disputes</h2>
          <ul>
            {claims.map((claim) => (
              <li key={claim.id}>{claim.status}{claim.statement ? ': ' + claim.statement : ''}</li>
            ))}
          </ul>
        </section>
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
