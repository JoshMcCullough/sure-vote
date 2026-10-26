import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, messageOf } from '../api'
import { ErrorAlert, Panel } from '../components/Page'
import { formatNumber } from '../i18n'

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
  const { t, i18n } = useTranslation()
  const [rows, setRows] = useState<PlaceReconciliation[] | null>(null)
  const [loadedFor, setLoadedFor] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const shown = loadedFor === electionId ? rows : null
  const number = (value: number) => formatNumber(value, i18n.language)
  const columns = {
    place: t('reconciliation.place'),
    kind: t('reconciliation.kind'),
    issued: t('reconciliation.issued'),
    received: t('reconciliation.received'),
    ballots: t('reconciliation.ballots'),
    matched: t('reconciliation.matched'),
  }

  async function load() {
    setError(null)
    setLoading(true)
    try {
      const report = await api<PlaceReconciliation[]>('GET', '/elections/' + electionId + '/reconciliation')
      setRows(report)
      setLoadedFor(electionId)
    } catch (caught) {
      setError(messageOf(caught, t))
    } finally {
      setLoading(false)
    }
  }

  function placeLabel(id: string): string {
    const short = id.slice(0, 8)
    return id === ownPlaceId ? t('reconciliation.thisPlace', { id: short }) : short
  }

  return (
    <Panel title={t('reconciliation.title')} headingId="reconciliation-title">
      <p>{t('reconciliation.body')}</p>
      <ErrorAlert message={error} />
      <button type="button" className="usa-button" disabled={!electionId || loading} onClick={() => void load()}>
        {shown ? t('reconciliation.refresh') : t('reconciliation.load')}
      </button>
      <div aria-live="polite">
        {shown && shown.length === 0 ? <p>{t('reconciliation.empty')}</p> : null}
        {shown && shown.length > 0 ? (
          <table className="usa-table usa-table--borderless usa-table--stacked-header width-full" data-report="">
            <caption>{t('reconciliation.caption', { count: shown.length })}</caption>
            <thead>
              <tr>
                <th scope="col">{columns.place}</th>
                <th scope="col">{columns.kind}</th>
                <th scope="col">{columns.issued}</th>
                <th scope="col">{columns.received}</th>
                <th scope="col">{columns.ballots}</th>
                <th scope="col">{columns.matched}</th>
              </tr>
            </thead>
            <tbody>
              {shown.map((row) => (
                <tr key={row.recordingPlaceId}>
                  <th scope="row" data-label={columns.place} title={row.recordingPlaceId}>
                    {placeLabel(row.recordingPlaceId)}
                  </th>
                  <td data-label={columns.kind}>{row.mail ? t('reconciliation.mail') : t('reconciliation.polling')}</td>
                  <td data-label={columns.issued}>{number(row.issued)}</td>
                  <td data-label={columns.received}>{number(row.received)}</td>
                  <td data-label={columns.ballots}>{number(row.ballots)}</td>
                  <td data-label={columns.matched}>
                    <span className={row.matched ? 'usa-tag bg-success-dark' : 'usa-tag bg-error-dark'}>
                      {row.matched ? t('reconciliation.yes') : t('reconciliation.no')}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        ) : null}
      </div>
    </Panel>
  )
}
