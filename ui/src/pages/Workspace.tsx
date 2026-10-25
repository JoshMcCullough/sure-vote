import { FormEvent, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, Created, messageOf, votingWindow } from '../api'
import { Field, Step } from '../forms'
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
  const draft = useState(loadDraft)[0]
  const [span] = useState(votingWindow)
  const [ids, setIds] = useState<Ids>({ ...blank, ...draft })
  const [error, setError] = useState<string | null>(null)
  const [code, setCode] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
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
          setError(messageOf(caught))
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
      setCopied(false)
    } catch (caught) {
      setError(messageOf(caught))
    }
  }

  async function copyCode() {
    if (!code) {
      return
    }
    try {
      await navigator.clipboard.writeText(code)
      setCopied(true)
    } catch (caught) {
      setCopied(false)
      setError(messageOf(caught))
    }
  }

  async function closeElection() {
    setError(null)
    try {
      const row = await api<Created>('POST', '/showcase/elections/' + ids.electionId + '/close', {})
      setClosed(row.status ?? 'closed')
    } catch (caught) {
      setError(messageOf(caught))
    }
  }

  const ready = Boolean(
    ids.electionId && ids.precinctId && ids.deviceId && ids.placeId && ids.slotId,
  )

  return (
    <>
      <h1>Configure an election</h1>
      <p>
        Create each part, then run a showcase demonstration.
        A production activate or close still needs a signature made outside this app.
        This browser does not hold a production private key.
      </p>
      {error ? <p className="banner" role="alert">{error}</p> : null}

      <section className="card">
        <h2>{principal?.kind === 'official' ? 'Your tenant' : 'Demo tenant'}</h2>
        <p>{tenantText(principal, demo)}</p>
      </section>
      <Step
        key={'locality-' + ids.tenantId}
        title="Locality"
        detail="The city or town the election belongs to."
        path="/localities"
        submitLabel="Create locality"
        fields={localityFields()}
        disabled={!ids.tenantId}
        onCreated={save('localityId')}
        onError={setError}
      />
      <Step
        key={'address-' + ids.localityId}
        title="Address"
        detail="The address used by the precinct and the polling place."
        path="/addresses"
        submitLabel="Create address"
        fields={addressFields(ids)}
        disabled={!ids.localityId}
        onCreated={save('addressId')}
        onError={setError}
      />
      <Step
        key={'precinct-' + ids.addressId}
        title="Precinct"
        detail="Where the voter's ballot style is drawn from."
        path="/precincts"
        submitLabel="Create precinct"
        fields={precinctFields(ids)}
        disabled={!ids.addressId}
        onCreated={save('precinctId')}
        onError={setError}
      />
      <Step
        key={'district-' + ids.localityId}
        title="District"
        detail="Optional. An at-large seat does not need one."
        path="/districts"
        submitLabel="Create district"
        fields={districtFields(ids)}
        disabled={!ids.localityId}
        onCreated={save('districtId')}
        onError={setError}
      />
      <Step
        key={'precinct-district-' + ids.districtId + '-' + ids.precinctId}
        title="Precinct district"
        detail="Optional. Ties the precinct to one district of that type."
        path="/precinct-districts"
        submitLabel="Assign district"
        fields={linkFields(ids)}
        disabled={!ids.districtId || !ids.precinctId}
        onCreated={() => undefined}
        onError={setError}
      />
      <Step
        key={'seat-' + ids.localityId}
        title="Seat"
        detail="The office on the ballot. Appointments is how many winners the seat has."
        path="/seats"
        submitLabel="Create seat"
        fields={seatFields(ids)}
        disabled={!ids.localityId}
        onCreated={save('seatId')}
        onError={setError}
      />
      <Step
        key={'term-' + ids.seatId}
        title="Term"
        detail="The term the race fills."
        path="/terms"
        submitLabel="Create term"
        fields={termFields(ids)}
        disabled={!ids.seatId}
        onCreated={save('termId')}
        onError={setError}
      />
      <Step
        key={'showcase-election-' + ids.localityId + '-' + span.start}
        title="Showcase election"
        detail="The host generates the operator, import, and viewing keys. The window must include now."
        path="/showcase/elections"
        submitLabel="Create demonstration"
        fields={electionFields(ids, span)}
        disabled={!ids.localityId}
        onCreated={save('electionId')}
        onError={setError}
      />
      <Step
        key={'production-election-' + ids.electionId}
        title="Production election"
        detail="Optional. Paste public keys generated outside this app. Leave this blank for a demonstration."
        path="/elections"
        submitLabel="Create election with outside keys"
        fields={productionElectionFields(ids, span)}
        disabled={!ids.localityId}
        onCreated={save('electionId')}
        onError={setError}
      />
      <Step
        key={'race-' + ids.electionId + '-' + ids.termId}
        title="Race"
        detail="One chain is created for this race."
        path="/races"
        submitLabel="Create race"
        fields={raceFields(ids)}
        disabled={!ids.electionId || !ids.termId}
        onCreated={save('raceId')}
        onError={setError}
      />
      <Step
        key={'candidate-person-' + ids.tenantId}
        title="Candidate person"
        detail="A fictional name is enough. A showcase tenant rejects an SSN and a voter id."
        path="/persons"
        submitLabel="Create candidate person"
        fields={personFields('Ada', 'One')}
        disabled={!ids.tenantId}
        onCreated={save('candidatePersonId')}
        onError={setError}
      />
      <Step
        key={'voter-' + ids.tenantId}
        title="Voter"
        detail="Accepted onto the pollbook when the demonstration ballot is cast. Not written onto the ballot."
        path="/persons"
        submitLabel="Create voter"
        fields={personFields('Cam', 'Voter')}
        disabled={!ids.tenantId}
        onCreated={save('voterId')}
        onError={setError}
      />
      <Step
        key={'candidate-' + ids.candidatePersonId}
        title="Candidate"
        detail="The candidate row points at the person."
        path="/candidates"
        submitLabel="Create candidate"
        fields={candidateFields(ids)}
        disabled={!ids.candidatePersonId}
        onCreated={save('candidateId')}
        onError={setError}
      />
      <Step
        key={'slot-' + ids.candidateId + '-' + ids.raceId}
        title="Race slot"
        detail="One choice on the race. Write-in stays false for this demonstration."
        path="/race-candidates"
        submitLabel="Add to the race"
        fields={slotFields(ids)}
        disabled={!ids.candidateId || !ids.raceId}
        onCreated={save('slotId')}
        onError={setError}
      />
      <Step
        key={'style-' + ids.raceId + '-' + ids.precinctId}
        title="Ballot style"
        detail="The precinct includes this race."
        path="/ballot-styles"
        submitLabel="Add ballot style"
        fields={styleFields(ids)}
        disabled={!ids.raceId || !ids.precinctId || !ids.electionId}
        onCreated={() => setStyled(true)}
        onError={setError}
      />
      <Step
        key={'place-' + ids.addressId}
        title="Recording place"
        detail="The polling place named on the ballot. This is not the voter's home."
        path="/places"
        submitLabel="Create place"
        fields={placeFields(ids)}
        disabled={!ids.localityId}
        onCreated={save('placeId')}
        onError={setError}
      />
      <Step
        key={'showcase-device-' + ids.tenantId}
        title="Showcase device"
        detail="Create this before activate. The host generates the device key."
        path="/showcase/devices"
        submitLabel="Create demonstration device"
        fields={showcaseDeviceFields()}
        disabled={!ids.tenantId}
        onCreated={save('deviceId')}
        onError={setError}
      />
      <Step
        key={'production-device-' + ids.tenantId}
        title="Production device"
        detail="Optional. Paste a device public key generated on the machine. Create it before activate."
        path="/devices"
        submitLabel="Register outside device"
        fields={deviceFields()}
        disabled={!ids.tenantId}
        onCreated={() => undefined}
        onError={setError}
      />
      <Step
        key={'assignment-' + ids.electionId + '-' + ids.precinctId}
        title="Precinct assignment"
        detail="The precinct is open for this election."
        path="/precinct-assignments"
        submitLabel="Assign precinct"
        fields={assignmentFields(ids)}
        disabled={!ids.electionId || !ids.precinctId}
        onCreated={() => setAssigned(true)}
        onError={setError}
      />
      <Step
        key={'activate-' + ids.electionId}
        title="Activate the demonstration"
        detail="The host signs the definition. Do this after the device, the style, and the assignment exist."
        path={'/showcase/elections/' + ids.electionId + '/activate'}
        submitLabel="Activate"
        fields={[]}
        disabled={!ids.electionId || !ids.deviceId || !styled || !assigned}
        onCreated={() => setActive(true)}
        onError={setError}
      />

      {code ? (
        <section className="card">
          <h2>Access code</h2>
          <p className="warn">
            This code is shown once. It is not saved in this browser.
            Copy it before you leave or clear this page.
          </p>
          <p className="code">{code}</p>
          <div className="actions">
            <button type="button" onClick={() => void copyCode()}>
              {copied ? 'Copied' : 'Copy'}
            </button>
            <button type="button" className="secondary" onClick={() => setCode(null)}>Clear the code</button>
            <Link to={'/elections/' + ids.electionId} state={{ code }}>Open the public lookup</Link>
          </div>
        </section>
      ) : castDone ? (
        <section className="card">
          <h2>Access code</h2>
          <p className="warn">The access code was shown once and is no longer on this page.</p>
          <Link to={'/elections/' + ids.electionId}>Open the public lookup</Link>
        </section>
      ) : (
        <form className="card" onSubmit={(event) => void cast(event)}>
          <h2>Cast a demonstration ballot</h2>
          <p>
            One first-past-the-post choice, value Y, for the slot above.
            The voter is accepted on the pollbook and is not stored on the ballot.
          </p>
          <div className="actions">
            <button type="submit" disabled={!ready || !ids.voterId || !active}>Cast ballot</button>
          </div>
        </form>
      )}

      <section className="card">
        <h2>Close the demonstration</h2>
        <p>The host signs each race chain and the canvass statement. Open disputes do not block close.</p>
        <div className="actions">
          <button type="button" disabled={!ids.electionId} onClick={() => void closeElection()}>Close</button>
          {closed ? <span className="saved">Status {closed}</span> : null}
        </div>
      </section>

      <Reconciliation electionId={ids.electionId} ownPlaceId={ids.placeId} />

      <Step
        key={'production-activate-' + ids.electionId}
        title="Production activate"
        detail="Paste the operator signature over the definition hash. The hash comes from outside this app."
        path={'/elections/' + ids.electionId + '/activate'}
        submitLabel="Activate with outside signature"
        fields={[{ name: 'signature', label: 'Operator signature', value: '' }]}
        disabled={!ids.electionId}
        onCreated={() => undefined}
        onError={setError}
      />
      <Step
        key={'production-close-' + ids.electionId + '-' + ids.raceId}
        title="Production close"
        detail="Paste one chain signature and the canvass signature. Both are made outside this app."
        path={'/elections/' + ids.electionId + '/close'}
        submitLabel="Close with outside signatures"
        fields={closeFields(ids)}
        disabled={!ids.electionId || !ids.raceId}
        build={closeBody}
        onCreated={() => undefined}
        onError={setError}
      />
    </>
  )
}

function tenantText(principal: Principal | null, demo: DemoTenant | null): string {
  if (!principal) {
    return 'Starting a session.'
  }
  if (principal.kind === 'official') {
    return 'Signed in as ' + principal.name + '. Everything here is created in your tenant.'
  }
  const name = demo ? demo.name : 'the demo tenant'
  return 'Everything here is created in the shared demo tenant ' + name
    + '. It has no legal effect. A system admin creates every other tenant.'
}

function localityFields(): Field[] {
  return [
    { name: 'name', label: 'Name', value: 'Town' },
    { name: 'type', label: 'Type', value: 'city' },
  ]
}

function addressFields(ids: Ids): Field[] {
  return [
    { name: 'localityId', label: 'Locality', value: ids.localityId },
    { name: 'street1', label: 'Street', value: '1 Main' },
    { name: 'postalCode', label: 'Postal code', value: '00000' },
  ]
}

function precinctFields(ids: Ids): Field[] {
  return [
    { name: 'name', label: 'Name', value: 'Precinct 1' },
    { name: 'addressId', label: 'Address', value: ids.addressId },
  ]
}

function districtFields(ids: Ids): Field[] {
  return [
    { name: 'localityId', label: 'Locality', value: ids.localityId },
    { name: 'name', label: 'Name', value: 'Ward 1' },
    {
      name: 'type',
      label: 'Type',
      value: 'city-council',
      options: ['congressional', 'state-senate', 'state-house', 'county-commission', 'city-council', 'school-board', 'other'],
    },
  ]
}

function linkFields(ids: Ids): Field[] {
  return [
    { name: 'votingPrecinctId', label: 'Precinct', value: ids.precinctId },
    { name: 'votingDistrictId', label: 'District', value: ids.districtId },
    {
      name: 'districtType',
      label: 'District type',
      value: 'city-council',
      options: ['congressional', 'state-senate', 'state-house', 'county-commission', 'city-council', 'school-board', 'other'],
    },
  ]
}

function seatFields(ids: Ids): Field[] {
  return [
    { name: 'localityId', label: 'Locality', value: ids.localityId },
    { name: 'name', label: 'Name', value: 'Mayor' },
    { name: 'jurisdiction', label: 'Jurisdiction', value: 'local', options: ['federal', 'state', 'local'] },
    {
      name: 'type',
      label: 'Type',
      value: 'mayor',
      options: ['president', 'vice-president', 'senator', 'representative', 'governor', 'mayor', 'council', 'other'],
    },
    { name: 'appointments', label: 'Appointments', value: '1', kind: 'number' },
    { name: 'termLength', label: 'Term length', value: '4', kind: 'number' },
  ]
}

function termFields(ids: Ids): Field[] {
  return [
    { name: 'seatId', label: 'Seat', value: ids.seatId },
  ]
}

function electionFields(ids: Ids, span: { start: string; end: string }): Field[] {
  return [
    { name: 'localityId', label: 'Locality', value: ids.localityId },
    { name: 'name', label: 'Name', value: 'General' },
    { name: 'votingStart', label: 'Voting start', value: span.start },
    { name: 'votingEnd', label: 'Voting end', value: span.end },
  ]
}

function productionElectionFields(ids: Ids, span: { start: string; end: string }): Field[] {
  return [
    { name: 'localityId', label: 'Locality', value: ids.localityId },
    { name: 'name', label: 'Name', value: 'General' },
    { name: 'votingStart', label: 'Voting start', value: span.start },
    { name: 'votingEnd', label: 'Voting end', value: span.end },
    { name: 'operatorPublicKey', label: 'Operator public key', value: '' },
    { name: 'importPublicKey', label: 'Import public key', value: '' },
    { name: 'viewingPublicKey', label: 'Viewing public key', value: '' },
  ]
}

function raceFields(ids: Ids): Field[] {
  return [
    { name: 'electionId', label: 'Election', value: ids.electionId },
    { name: 'termId', label: 'Term', value: ids.termId },
    { name: 'name', label: 'Name', value: 'Mayor' },
    { name: 'type', label: 'Type', value: 'general', options: ['general', 'primary', 'special'] },
    {
      name: 'method',
      label: 'Method',
      value: 'first-past-the-post',
      options: ['first-past-the-post', 'referendum', 'ranked-choice'],
    },
  ]
}

function personFields(first: string, last: string): Field[] {
  return [
    { name: 'firstName', label: 'First name', value: first },
    { name: 'lastName', label: 'Last name', value: last },
  ]
}

function candidateFields(ids: Ids): Field[] {
  return [
    { name: 'personId', label: 'Person', value: ids.candidatePersonId },
    { name: 'partyAffiliation', label: 'Party', value: '' },
  ]
}

function slotFields(ids: Ids): Field[] {
  return [
    { name: 'raceId', label: 'Race', value: ids.raceId },
    { name: 'candidateId', label: 'Candidate', value: ids.candidateId },
    { name: 'writeIn', label: 'Write-in', value: 'false', kind: 'boolean' },
    { name: 'referendumChoice', label: 'Referendum choice', value: '' },
  ]
}

function styleFields(ids: Ids): Field[] {
  return [
    { name: 'electionId', label: 'Election', value: ids.electionId },
    { name: 'votingPrecinctId', label: 'Precinct', value: ids.precinctId },
    { name: 'raceId', label: 'Race', value: ids.raceId },
  ]
}

function placeFields(ids: Ids): Field[] {
  return [
    { name: 'localityId', label: 'Locality', value: ids.localityId },
    { name: 'addressId', label: 'Address', value: ids.addressId },
    { name: 'name', label: 'Name', value: 'Town Hall' },
    { name: 'kind', label: 'Kind', value: 'polling-place', options: ['polling-place', 'central-office'] },
  ]
}

function showcaseDeviceFields(): Field[] {
  return [
    { name: 'name', label: 'Name', value: 'Machine 1' },
    {
      name: 'kind',
      label: 'Kind',
      value: 'polling-place-machine',
      options: ['polling-place-machine', 'central-workstation'],
    },
  ]
}

function deviceFields(): Field[] {
  return [
    { name: 'name', label: 'Name', value: 'Machine 2' },
    {
      name: 'kind',
      label: 'Kind',
      value: 'polling-place-machine',
      options: ['polling-place-machine', 'central-workstation'],
    },
    { name: 'publicKey', label: 'Public key', value: '' },
    { name: 'status', label: 'Status', value: 'active', options: ['active', 'retired'] },
  ]
}

function assignmentFields(ids: Ids): Field[] {
  return [
    { name: 'electionId', label: 'Election', value: ids.electionId },
    { name: 'votingPrecinctId', label: 'Precinct', value: ids.precinctId },
    { name: 'assignmentStatus', label: 'Status', value: 'current', options: ['current', 'historical'] },
  ]
}

function closeFields(ids: Ids): Field[] {
  return [
    { name: 'raceId', label: 'Race', value: ids.raceId },
    { name: 'signature', label: 'Chain signature', value: '' },
    { name: 'canvassSignature', label: 'Canvass signature', value: '' },
  ]
}

function closeBody(values: Record<string, unknown>): unknown {
  return {
    canvassSignature: values.canvassSignature,
    chains: [{ raceId: values.raceId, signature: values.signature }],
  }
}
