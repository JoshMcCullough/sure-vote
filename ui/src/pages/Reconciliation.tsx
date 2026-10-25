import { useState } from 'react'
import { api, messageOf } from '../api'

export type PlaceReconciliation = {
  recordingPlaceId: string
  mail: boolean
  issued: number
  received: number
  ballots: number
  matched: boolean
}

type Props = {
  electionId: string
  ownPlaceId: string
}

export function Reconciliation({ electionId, ownPlaceId }: Props) {
  const [rows, setRows] = useState<PlaceReconciliation[] | null>(null)
  const [loadedFor, setLoadedFor] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const shown = loadedFor === electionId ? rows : null

  async function load() {
    setError(null)
    setLoading(true)
    try {
      const report = await api<PlaceReconciliation[]>('GET', '/elections/' + electionId + '/reconciliation')
      setRows(report)
      setLoadedFor(electionId)
    } catch (caught) {
      setError(messageOf(caught))
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="card" aria-labelledby="reconciliation-title">
      <h2 id="reconciliation-title">Reconciliation</h2>
      <p>
        Counts per recording place. A polling place matches when issued plus received equals its ballots.
        A central office handles mail: unreturned mailed ballots stay issued, so it matches when received equals
        ballots. Received marks are written in batches, so a recent ballot can show as not matched yet.
      </p>
      {error ? <p className="banner" role="alert">{error}</p> : null}
      <div className="actions">
        <button type="button" disabled={!electionId || loading} onClick={() => void load()}>
          {shown ? 'Refresh' : 'Load report'}
        </button>
      </div>
      {shown && shown.length === 0 ? <p>No ballots or issuances yet.</p> : null}
      {shown && shown.length > 0 ? (
        <table className="report">
          <thead>
            <tr>
              <th scope="col">Place</th>
              <th scope="col">Kind</th>
              <th scope="col">Issued</th>
              <th scope="col">Received</th>
              <th scope="col">Ballots</th>
              <th scope="col">Matched</th>
            </tr>
          </thead>
          <tbody>
            {shown.map((row) => (
              <tr key={row.recordingPlaceId}>
                <td title={row.recordingPlaceId}>{placeLabel(row.recordingPlaceId, ownPlaceId)}</td>
                <td>{row.mail ? 'Mail (central office)' : 'Polling place'}</td>
                <td>{row.issued}</td>
                <td>{row.received}</td>
                <td>{row.ballots}</td>
                <td className={row.matched ? 'saved' : 'warn'}>{row.matched ? 'Yes' : 'No'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}
    </section>
  )
}

function placeLabel(id: string, ownPlaceId: string): string {
  const short = id.slice(0, 8)
  return id === ownPlaceId ? 'This place (' + short + ')' : short
}
