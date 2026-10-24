import { FormEvent, useState } from 'react'
import { api, Created, messageOf } from './api'

export type Field = {
  name: string
  label: string
  value: string
  kind?: 'text' | 'number' | 'boolean'
  options?: string[]
}

type StepProps = {
  title: string
  detail: string
  path: string
  submitLabel: string
  fields: Field[]
  disabled?: boolean
  build?: (values: Record<string, unknown>) => unknown
  onCreated: (row: Created) => void
  onError: (message: string | null) => void
}

export function Step(props: StepProps) {
  const [fields, setFields] = useState(props.fields)
  const [saved, setSaved] = useState('')
  const [pending, setPending] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (props.disabled || pending) {
      return
    }
    const values = record(fields)
    const body = props.build ? props.build(values) : values
    setPending(true)
    props.onError(null)
    try {
      const row = await api<Created>('POST', props.path, body)
      setSaved(row.id ?? row.status ?? 'saved')
      props.onCreated(row)
    } catch (error) {
      props.onError(messageOf(error))
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="card" onSubmit={onSubmit}>
      <h2>{props.title}</h2>
      <p>{props.detail}</p>
      {fields.map((field, index) => (
        <label key={field.name}>
          {field.label}
          {field.options || field.kind === 'boolean' ? (
            <select
              value={field.value}
              disabled={props.disabled}
              onChange={(event) => setFields(update(fields, index, event.target.value))}
            >
              {(field.options ?? ['false', 'true']).map((option) => (
                <option key={option} value={option}>{option}</option>
              ))}
            </select>
          ) : (
            <input
              value={field.value}
              disabled={props.disabled}
              inputMode={field.kind === 'number' ? 'numeric' : 'text'}
              onChange={(event) => setFields(update(fields, index, event.target.value))}
            />
          )}
        </label>
      ))}
      <div className="actions">
        <button type="submit" disabled={props.disabled || pending}>{props.submitLabel}</button>
        {saved ? <span className="saved">Saved {saved}</span> : null}
      </div>
    </form>
  )
}

function update(fields: Field[], index: number, value: string): Field[] {
  return fields.map((field, current) => {
    if (current === index) {
      return { ...field, value }
    }
    return field
  })
}

function record(fields: Field[]): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  for (const field of fields) {
    body[field.name] = coerce(field)
  }
  return body
}

function coerce(field: Field): unknown {
  if (field.kind === 'number') {
    return Number(field.value)
  }
  if (field.kind === 'boolean') {
    return field.value === 'true'
  }
  if (field.value.trim() === '') {
    return null
  }
  return field.value
}
