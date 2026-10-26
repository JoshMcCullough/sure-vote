import { FormEvent, useId, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, Created, messageOf } from './api'
import { ErrorSummary, FieldError, SelectField, TextField } from './components/Fields'
import { ErrorAlert, Panel, SuccessAlert } from './components/Page'

export type Field = {
  name: string
  value: string
  label?: string
  kind?: 'text' | 'number' | 'boolean'
  options?: string[]
  optional?: boolean
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
}

export function Step(props: StepProps) {
  const { t } = useTranslation()
  const formId = useId()
  const [fields, setFields] = useState(props.fields)
  const [saved, setSaved] = useState('')
  const [pending, setPending] = useState(false)
  const [errors, setErrors] = useState<FieldError[]>([])
  const [failure, setFailure] = useState<string | null>(null)
  const summary = useRef<HTMLDivElement>(null)

  function idOf(field: Field): string {
    return formId + '-' + field.name
  }

  function labelOf(field: Field): string {
    return field.label ?? t('fields.' + field.name)
  }

  function validate(): FieldError[] {
    const found: FieldError[] = []
    for (const field of fields) {
      const label = labelOf(field)
      if (!field.optional && field.value.trim() === '') {
        found.push({ id: idOf(field), message: t('form.required', { field: label }) })
      } else if (field.kind === 'number' && field.value.trim() !== '' && !Number.isFinite(Number(field.value))) {
        found.push({ id: idOf(field), message: t('form.number', { field: label }) })
      }
    }
    return found
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (props.disabled || pending) {
      return
    }
    const found = validate()
    setErrors(found)
    setFailure(null)
    if (found.length > 0) {
      requestAnimationFrame(() => summary.current?.focus())
      return
    }
    const values = record(fields)
    const body = props.build ? props.build(values) : values
    setPending(true)
    try {
      const row = await api<Created>('POST', props.path, body)
      setSaved(row.id ?? row.status ?? '')
      props.onCreated(row)
    } catch (error) {
      setFailure(messageOf(error, t))
    } finally {
      setPending(false)
    }
  }

  function errorFor(field: Field): string | null {
    const found = errors.find((error) => error.id === idOf(field))
    return found ? found.message : null
  }

  return (
    <Panel title={props.title}>
      <form className="usa-form maxw-none" noValidate onSubmit={(event) => void onSubmit(event)}>
        <p>{props.detail}</p>
        {props.disabled ? <p className="usa-hint">{t('form.waiting')}</p> : null}
        <ErrorSummary errors={errors} heading={t('form.summary', { count: errors.length })} summaryRef={summary} />
        <ErrorAlert message={failure} />
        {fields.map((field, index) => {
          const common = {
            id: idOf(field),
            label: labelOf(field),
            value: field.value,
            disabled: props.disabled,
            required: !field.optional,
            error: errorFor(field),
            onChange: (value: string) => setFields(update(fields, index, value)),
          }
          if (field.options || field.kind === 'boolean') {
            const options = (field.options ?? ['false', 'true']).map((option) => ({
              value: option,
              label: t('values.' + option, { defaultValue: option }),
            }))
            return <SelectField key={field.name} {...common} options={options} />
          }
          return (
            <TextField
              key={field.name}
              {...common}
              inputMode={field.kind === 'number' ? 'numeric' : 'text'}
              spellCheck={false}
            />
          )
        })}
        <div className="margin-top-3">
          <button type="submit" className="usa-button" disabled={props.disabled || pending}>
            {props.submitLabel}
          </button>
        </div>
        <SuccessAlert message={saved ? t('form.saved', { id: saved }) : null} />
      </form>
    </Panel>
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
