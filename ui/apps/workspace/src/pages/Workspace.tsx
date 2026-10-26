import { FormEvent, useEffect, useRef, useState } from 'react'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { api, Created, messageOf, votingWindow } from '@surevote/shared/api'
import { AccessCode } from '../components/AccessCode'
import { ErrorAlert, PageHeading, Panel } from '@surevote/shared/components/Page'
import { Field, Step } from '@surevote/shared/forms'
import { Principal, workspaceSession } from '../session'
import { Reconciliation } from './Reconciliation'

type Ids = {
  tenantId: string
  localityId: string
  addressId: string
  precinctId: string
  districtId: string
  seatId: string
  termId: string
  electionId: string
  raceId: string
  candidatePersonId: string
  voterId: string
  candidateId: string
  slotId: string
  placeId: string
  deviceId: string
}

type DemoCast = {
  code: string
}

type DemoTenant = {
  id: string
  name: string
}

const blank: Ids = {
  tenantId: '',
  localityId: '',
  addressId: '',
  precinctId: '',
  districtId: '',
  seatId: '',
  termId: '',
  electionId: '',
  raceId: '',
  candidatePersonId: '',
  voterId: '',
  candidateId: '',
  slotId: '',
  placeId: '',
  deviceId: '',
}

const draftKey = 'surevote.workspace'

type Draft = Ids & {
  styled: boolean
  assigned: boolean
  active: boolean
  closed: string
  castDone: boolean
}

function loadDraft(): Draft {
  const base: Draft = {
    ...blank,
    styled: false,
    assigned: false,
    active: false,
    closed: '',
    castDone: false,
  }
  try {
    const text = sessionStorage.getItem(draftKey)
    if (!text) {
      return base
    }
    const parsed = JSON.parse(text) as Partial<Draft>
    return { ...base, ...parsed }
  } catch {
    return base
  }
}

export function Workspace() {
  const { t } = useTranslation()
  const draft = useState(loadDraft)[0]
  const [span] = useState(votingWindow)
  const [ids, setIds] = useState<Ids>({ ...blank, ...draft })
  const [error, setError] = useState<string | null>(null)
  const [code, setCode] = useState<string | null>(null)
  const [closed, setClosed] = useState(draft.closed)
  const [styled, setStyled] = useState(draft.styled)
  const [assigned, setAssigned] = useState(draft.assigned)
  const [active, setActive] = useState(draft.active)
  const [castDone, setCastDone] = useState(draft.castDone)
  const [demo, setDemo] = useState<DemoTenant | null>(null)
  const [principal, setPrincipal] = useState<Principal | null>(null)
  const previousElection = useRef<string | null>(null)

  useEffect(() => {
    let gone = false
    async function load() {
      try {
        const tenant = await api<DemoTenant>('GET', '/showcase/tenant')
        const principal = await workspaceSession()
        if (!gone) {
          setDemo(tenant)
          setPrincipal(principal)
          const tenantId = principal.tenantId ?? ''
          setIds((current) => (current.tenantId === tenantId ? current : { ...blank, tenantId }))
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
  }, [])

  useEffect(() => {
    if (previousElection.current === null) {
      previousElection.current = ids.electionId
      return
    }
    if (previousElection.current === ids.electionId) {
      return
    }
    previousElection.current = ids.electionId
    setStyled(false)
    setAssigned(false)
    setActive(false)
    setCastDone(false)
    setCode(null)
    setClosed('')
  }, [ids.electionId])

  useEffect(() => {
    const stored: Draft = { ...ids, styled, assigned, active, closed, castDone }
    sessionStorage.setItem(draftKey, JSON.stringify(stored))
  }, [ids, styled, assigned, active, closed, castDone])

  function save(key: keyof Ids): (row: Created) => void {
    return (row) => {
      if (!row.id) {
        return
      }
      setIds((current) => ({ ...current, [key]: row.id }))
    }
  }

  async function cast(event: FormEvent) {
    event.preventDefault()
    setError(null)
    try {
      const row = await api<DemoCast>('POST', '/showcase/elections/' + ids.electionId + '/cast', {
        votingPrecinctId: ids.precinctId,
        recordingDeviceId: ids.deviceId,
        recordingPlaceId: ids.placeId,
        personId: ids.voterId || null,
        selections: [{ raceCandidateId: ids.slotId, value: 'Y' }],
      })
      setCode(row.code)
      setCastDone(true)
    } catch (caught) {
      setError(messageOf(caught, t))
    }
  }

  async function closeElection() {
    setError(null)
    try {
      const row = await api<Created>('POST', '/showcase/elections/' + ids.electionId + '/close', {})
      setClosed(row.status ?? 'closed')
    } catch (caught) {
      setError(messageOf(caught, t))
    }
  }

  const ready = Boolean(
    ids.electionId && ids.precinctId && ids.deviceId && ids.placeId && ids.slotId,
  )

  return (
    <>
      <PageHeading>{t('workspace.title')}</PageHeading>
      <p className="usa-intro">{t('workspace.intro')}</p>
      <ErrorAlert message={error} />
      <Panel title={principal?.kind === 'official' ? t('workspace.yourTenant') : t('workspace.demoTenant')}>
        <p>{tenantText(principal, demo, t)}</p>
      </Panel>
      <Step
        key={'locality-' + ids.tenantId}
        title={t('workspace.locality.title')}
        detail={t('workspace.locality.detail')}
        path="/localities"
        submitLabel={t('workspace.locality.submit')}
        fields={localityFields(t)}
        disabled={!ids.tenantId}
        onCreated={save('localityId')}
      />
      <Step
        key={'address-' + ids.localityId}
        title={t('workspace.address.title')}
        detail={t('workspace.address.detail')}
        path="/addresses"
        submitLabel={t('workspace.address.submit')}
        fields={addressFields(ids, t)}
        disabled={!ids.localityId}
        onCreated={save('addressId')}
      />
      <Step
        key={'precinct-' + ids.addressId}
        title={t('workspace.precinct.title')}
        detail={t('workspace.precinct.detail')}
        path="/precincts"
        submitLabel={t('workspace.precinct.submit')}
        fields={precinctFields(ids, t)}
        disabled={!ids.addressId}
        onCreated={save('precinctId')}
      />
      <Step
        key={'district-' + ids.localityId}
        title={t('workspace.district.title')}
        detail={t('workspace.district.detail')}
        path="/districts"
        submitLabel={t('workspace.district.submit')}
        fields={districtFields(ids, t)}
        disabled={!ids.localityId}
        onCreated={save('districtId')}
      />
      <Step
        key={'precinct-district-' + ids.districtId + '-' + ids.precinctId}
        title={t('workspace.precinctDistrict.title')}
        detail={t('workspace.precinctDistrict.detail')}
        path="/precinct-districts"
        submitLabel={t('workspace.precinctDistrict.submit')}
        fields={linkFields(ids)}
        disabled={!ids.districtId || !ids.precinctId}
        onCreated={() => undefined}
      />
      <Step
        key={'seat-' + ids.localityId}
        title={t('workspace.seat.title')}
        detail={t('workspace.seat.detail')}
        path="/seats"
        submitLabel={t('workspace.seat.submit')}
        fields={seatFields(ids, t)}
        disabled={!ids.localityId}
        onCreated={save('seatId')}
      />
      <Step
        key={'term-' + ids.seatId}
        title={t('workspace.term.title')}
        detail={t('workspace.term.detail')}
        path="/terms"
        submitLabel={t('workspace.term.submit')}
        fields={termFields(ids)}
        disabled={!ids.seatId}
        onCreated={save('termId')}
      />
      <Step
        key={'showcase-election-' + ids.localityId + '-' + span.start}
        title={t('workspace.showcaseElection.title')}
        detail={t('workspace.showcaseElection.detail')}
        path="/showcase/elections"
        submitLabel={t('workspace.showcaseElection.submit')}
        fields={electionFields(ids, span, t)}
        disabled={!ids.localityId}
        onCreated={save('electionId')}
      />
      <Step
        key={'production-election-' + ids.localityId + '-' + ids.electionId}
        title={t('workspace.productionElection.title')}
        detail={t('workspace.productionElection.detail')}
        path="/elections"
        submitLabel={t('workspace.productionElection.submit')}
        fields={productionElectionFields(ids, span, t)}
        disabled={!ids.localityId}
        onCreated={save('electionId')}
      />
      <Step
        key={'race-' + ids.electionId + '-' + ids.termId}
        title={t('workspace.race.title')}
        detail={t('workspace.race.detail')}
        path="/races"
        submitLabel={t('workspace.race.submit')}
        fields={raceFields(ids, t)}
        disabled={!ids.electionId || !ids.termId}
        onCreated={save('raceId')}
      />
      <Step
        key={'candidate-person-' + ids.tenantId}
        title={t('workspace.candidatePerson.title')}
        detail={t('workspace.candidatePerson.detail')}
        path="/persons"
        submitLabel={t('workspace.candidatePerson.submit')}
        fields={personFields(t('defaults.candidateFirst'), t('defaults.candidateLast'))}
        disabled={!ids.tenantId}
        onCreated={save('candidatePersonId')}
      />
      <Step
        key={'voter-' + ids.tenantId}
        title={t('workspace.voter.title')}
        detail={t('workspace.voter.detail')}
        path="/persons"
        submitLabel={t('workspace.voter.submit')}
        fields={personFields(t('defaults.voterFirst'), t('defaults.voterLast'))}
        disabled={!ids.tenantId}
        onCreated={save('voterId')}
      />
      <Step
        key={'candidate-' + ids.candidatePersonId}
        title={t('workspace.candidate.title')}
        detail={t('workspace.candidate.detail')}
        path="/candidates"
        submitLabel={t('workspace.candidate.submit')}
        fields={candidateFields(ids)}
        disabled={!ids.candidatePersonId}
        onCreated={save('candidateId')}
      />
      <Step
        key={'slot-' + ids.candidateId + '-' + ids.raceId}
        title={t('workspace.slot.title')}
        detail={t('workspace.slot.detail')}
        path="/race-candidates"
        submitLabel={t('workspace.slot.submit')}
        fields={slotFields(ids)}
        disabled={!ids.candidateId || !ids.raceId}
        onCreated={save('slotId')}
      />
      <Step
        key={'style-' + ids.raceId + '-' + ids.precinctId}
        title={t('workspace.style.title')}
        detail={t('workspace.style.detail')}
        path="/ballot-styles"
        submitLabel={t('workspace.style.submit')}
        fields={styleFields(ids)}
        disabled={!ids.raceId || !ids.precinctId || !ids.electionId}
        onCreated={() => setStyled(true)}
      />
      <Step
        key={'place-' + ids.addressId}
        title={t('workspace.place.title')}
        detail={t('workspace.place.detail')}
        path="/places"
        submitLabel={t('workspace.place.submit')}
        fields={placeFields(ids, t)}
        disabled={!ids.localityId}
        onCreated={save('placeId')}
      />
      <Step
        key={'showcase-device-' + ids.tenantId}
        title={t('workspace.showcaseDevice.title')}
        detail={t('workspace.showcaseDevice.detail')}
        path="/showcase/devices"
        submitLabel={t('workspace.showcaseDevice.submit')}
        fields={showcaseDeviceFields(t)}
        disabled={!ids.tenantId}
        onCreated={save('deviceId')}
      />
      <Step
        key={'production-device-' + ids.tenantId}
        title={t('workspace.productionDevice.title')}
        detail={t('workspace.productionDevice.detail')}
        path="/devices"
        submitLabel={t('workspace.productionDevice.submit')}
        fields={deviceFields(t)}
        disabled={!ids.tenantId}
        onCreated={() => undefined}
      />
      <Step
        key={'assignment-' + ids.electionId + '-' + ids.precinctId}
        title={t('workspace.assignment.title')}
        detail={t('workspace.assignment.detail')}
        path="/precinct-assignments"
        submitLabel={t('workspace.assignment.submit')}
        fields={assignmentFields(ids)}
        disabled={!ids.electionId || !ids.precinctId}
        onCreated={() => setAssigned(true)}
      />
      <Step
        key={'activate-' + ids.electionId}
        title={t('workspace.activate.title')}
        detail={t('workspace.activate.detail')}
        path={'/showcase/elections/' + ids.electionId + '/activate'}
        submitLabel={t('workspace.activate.submit')}
        fields={[]}
        disabled={!ids.electionId || !ids.deviceId || !styled || !assigned}
        onCreated={() => setActive(true)}
      />

      {code ? (
        <Panel title={t('workspace.code.title')}>
          <div className="usa-alert usa-alert--warning usa-alert--slim margin-bottom-2">
            <div className="usa-alert__body">
              <p className="usa-alert__text">{t('workspace.code.once')}</p>
            </div>
          </div>
          <AccessCode code={code} />
          <div className="usa-button-group margin-top-2">
            <button type="button" className="usa-button usa-button--outline" onClick={() => setCode(null)}>
              {t('workspace.code.clear')}
            </button>
            <Link className="usa-button usa-button--outline" to={'/elections/' + ids.electionId} state={{ code }}>
              {t('workspace.code.lookup')}
            </Link>
          </div>
        </Panel>
      ) : castDone ? (
        <Panel title={t('workspace.code.title')}>
          <p>{t('workspace.code.gone')}</p>
          <Link className="usa-button usa-button--outline" to={'/elections/' + ids.electionId}>
            {t('workspace.code.lookup')}
          </Link>
        </Panel>
      ) : (
        <Panel title={t('workspace.cast.title')}>
          <form onSubmit={(event) => void cast(event)}>
            <p>{t('workspace.cast.detail')}</p>
            {!ready || !ids.voterId || !active ? <p className="usa-hint">{t('form.waiting')}</p> : null}
            <button type="submit" className="usa-button" disabled={!ready || !ids.voterId || !active}>
              {t('workspace.cast.submit')}
            </button>
          </form>
        </Panel>
      )}

      <Panel title={t('workspace.close.title')}>
        <p>{t('workspace.close.detail')}</p>
        <button type="button" className="usa-button" disabled={!ids.electionId} onClick={() => void closeElection()}>
          {t('workspace.close.submit')}
        </button>
        <div role="status">
          {closed ? (
            <p className="margin-top-2">
              {t('workspace.close.status', { status: t('values.' + closed, { defaultValue: closed }) })}
            </p>
          ) : null}
        </div>
      </Panel>

      <Reconciliation electionId={ids.electionId} ownPlaceId={ids.placeId} />

      <Step
        key={'production-activate-' + ids.electionId}
        title={t('workspace.productionActivate.title')}
        detail={t('workspace.productionActivate.detail')}
        path={'/elections/' + ids.electionId + '/activate'}
        submitLabel={t('workspace.productionActivate.submit')}
        fields={[{ name: 'signature', label: t('fields.operatorSignature'), value: '' }]}
        disabled={!ids.electionId}
        onCreated={() => undefined}
      />
      <Step
        key={'production-close-' + ids.electionId + '-' + ids.raceId}
        title={t('workspace.productionClose.title')}
        detail={t('workspace.productionClose.detail')}
        path={'/elections/' + ids.electionId + '/close'}
        submitLabel={t('workspace.productionClose.submit')}
        fields={closeFields(ids, t)}
        disabled={!ids.electionId || !ids.raceId}
        build={closeBody}
        onCreated={() => undefined}
      />
    </>
  )
}

function tenantText(principal: Principal | null, demo: DemoTenant | null, t: TFunction): string {
  if (!principal) {
    return t('workspace.starting')
  }
  if (principal.kind === 'official') {
    return t('workspace.officialTenant', { name: principal.name })
  }
  if (demo) {
    return t('workspace.visitorTenant', { name: demo.name })
  }
  return t('workspace.visitorTenantUnnamed')
}

const districtTypes = [
  'congressional',
  'state-senate',
  'state-house',
  'county-commission',
  'city-council',
  'school-board',
  'other',
]

function localityFields(t: TFunction): Field[] {
  return [
    { name: 'name', value: t('defaults.locality') },
    { name: 'type', value: 'city' },
  ]
}

function addressFields(ids: Ids, t: TFunction): Field[] {
  return [
    { name: 'localityId', value: ids.localityId },
    { name: 'street1', value: t('defaults.street') },
    { name: 'postalCode', value: '00000' },
  ]
}

function precinctFields(ids: Ids, t: TFunction): Field[] {
  return [
    { name: 'name', value: t('defaults.precinct') },
    { name: 'addressId', value: ids.addressId },
  ]
}

function districtFields(ids: Ids, t: TFunction): Field[] {
  return [
    { name: 'localityId', value: ids.localityId },
    { name: 'name', value: t('defaults.district') },
    { name: 'type', value: 'city-council', options: districtTypes },
  ]
}

function linkFields(ids: Ids): Field[] {
  return [
    { name: 'votingPrecinctId', value: ids.precinctId },
    { name: 'votingDistrictId', value: ids.districtId },
    { name: 'districtType', value: 'city-council', options: districtTypes },
  ]
}

function seatFields(ids: Ids, t: TFunction): Field[] {
  return [
    { name: 'localityId', value: ids.localityId },
    { name: 'name', value: t('defaults.seat') },
    { name: 'jurisdiction', value: 'local', options: ['federal', 'state', 'local'] },
    {
      name: 'type',
      value: 'mayor',
      options: ['president', 'vice-president', 'senator', 'representative', 'governor', 'mayor', 'council', 'other'],
    },
    { name: 'appointments', value: '1', kind: 'number' },
    { name: 'termLength', value: '4', kind: 'number' },
  ]
}

function termFields(ids: Ids): Field[] {
  return [
    { name: 'seatId', value: ids.seatId },
  ]
}

function electionFields(ids: Ids, span: { start: string; end: string }, t: TFunction): Field[] {
  return [
    { name: 'localityId', value: ids.localityId },
    { name: 'name', value: t('defaults.election') },
    { name: 'votingStart', value: span.start },
    { name: 'votingEnd', value: span.end },
  ]
}

function productionElectionFields(ids: Ids, span: { start: string; end: string }, t: TFunction): Field[] {
  return [
    ...electionFields(ids, span, t),
    { name: 'operatorPublicKey', value: '' },
    { name: 'importPublicKey', value: '' },
    { name: 'viewingPublicKey', value: '' },
  ]
}

function raceFields(ids: Ids, t: TFunction): Field[] {
  return [
    { name: 'electionId', value: ids.electionId },
    { name: 'termId', value: ids.termId },
    { name: 'name', value: t('defaults.race') },
    { name: 'type', value: 'general', options: ['general', 'primary', 'special'] },
    { name: 'method', value: 'first-past-the-post', options: ['first-past-the-post', 'referendum', 'ranked-choice'] },
  ]
}

function personFields(first: string, last: string): Field[] {
  return [
    { name: 'firstName', value: first },
    { name: 'lastName', value: last },
  ]
}

function candidateFields(ids: Ids): Field[] {
  return [
    { name: 'personId', value: ids.candidatePersonId },
    { name: 'partyAffiliation', value: '', optional: true },
  ]
}

function slotFields(ids: Ids): Field[] {
  return [
    { name: 'raceId', value: ids.raceId },
    { name: 'candidateId', value: ids.candidateId },
    { name: 'writeIn', value: 'false', kind: 'boolean' },
    { name: 'referendumChoice', value: '', optional: true },
  ]
}

function styleFields(ids: Ids): Field[] {
  return [
    { name: 'electionId', value: ids.electionId },
    { name: 'votingPrecinctId', value: ids.precinctId },
    { name: 'raceId', value: ids.raceId },
  ]
}

function placeFields(ids: Ids, t: TFunction): Field[] {
  return [
    { name: 'localityId', value: ids.localityId },
    { name: 'addressId', value: ids.addressId },
    { name: 'name', value: t('defaults.place') },
    { name: 'kind', value: 'polling-place', options: ['polling-place', 'central-office'] },
  ]
}

function showcaseDeviceFields(t: TFunction): Field[] {
  return [
    { name: 'name', value: t('defaults.device') },
    { name: 'kind', value: 'polling-place-machine', options: ['polling-place-machine', 'central-workstation'] },
  ]
}

function deviceFields(t: TFunction): Field[] {
  return [
    { name: 'name', value: t('defaults.outsideDevice') },
    { name: 'kind', value: 'polling-place-machine', options: ['polling-place-machine', 'central-workstation'] },
    { name: 'publicKey', value: '' },
    { name: 'status', value: 'active', options: ['active', 'retired'] },
  ]
}

function assignmentFields(ids: Ids): Field[] {
  return [
    { name: 'electionId', value: ids.electionId },
    { name: 'votingPrecinctId', value: ids.precinctId },
    { name: 'assignmentStatus', value: 'current', options: ['current', 'historical'] },
  ]
}

function closeFields(ids: Ids, t: TFunction): Field[] {
  return [
    { name: 'raceId', value: ids.raceId },
    { name: 'signature', label: t('fields.chainSignature'), value: '' },
    { name: 'canvassSignature', value: '' },
  ]
}

function closeBody(values: Record<string, unknown>): unknown {
  return {
    canvassSignature: values.canvassSignature,
    chains: [{ raceId: values.raceId, signature: values.signature }],
  }
}
