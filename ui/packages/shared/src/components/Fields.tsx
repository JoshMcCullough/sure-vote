import { ReactNode } from 'react'

type Common = {
  id: string
  label: string
  error?: string | null
  hint?: ReactNode
  disabled?: boolean
  required?: boolean
}

type TextProps = Common & {
  value: string
  onChange: (value: string) => void
  type?: 'text' | 'password'
  autoComplete?: string
  inputMode?: 'text' | 'numeric'
  spellCheck?: boolean
}

type SelectProps = Common & {
  value: string
  onChange: (value: string) => void
  options: { value: string; label: string; lang?: string }[]
}

type AreaProps = Common & {
  value: string
  onChange: (value: string) => void
}

function describedBy(id: string, hint: ReactNode, error: string | null | undefined): string | undefined {
  const parts = []
  if (hint) {
    parts.push(id + '-hint')
  }
  if (error) {
    parts.push(id + '-error')
  }
  return parts.length > 0 ? parts.join(' ') : undefined
}

function Frame({ id, label, error, hint, required, children }: Common & { children: ReactNode }) {
  const group = error ? 'usa-form-group usa-form-group--error' : 'usa-form-group'
  const labelClass = error ? 'usa-label usa-label--error' : 'usa-label'
  return (
    <div className={group}>
      <label className={labelClass} htmlFor={id}>
        {label}
        {required ? <span className="usa-hint usa-hint--required" aria-hidden="true"> *</span> : null}
      </label>
      {hint ? <div className="usa-hint" id={id + '-hint'}>{hint}</div> : null}
      {error ? <span className="usa-error-message" id={id + '-error'}>{error}</span> : null}
      {children}
    </div>
  )
}

export function TextField(props: TextProps) {
  const inputClass = props.error ? 'usa-input usa-input--error' : 'usa-input'
  return (
    <Frame {...props}>
      <input
        id={props.id}
        name={props.id}
        className={inputClass}
        type={props.type ?? 'text'}
        value={props.value}
        disabled={props.disabled}
        required={props.required}
        aria-required={props.required}
        aria-invalid={props.error ? true : undefined}
        aria-describedby={describedBy(props.id, props.hint, props.error)}
        autoComplete={props.autoComplete}
        inputMode={props.inputMode}
        spellCheck={props.spellCheck}
        onChange={(event) => props.onChange(event.target.value)}
      />
    </Frame>
  )
}

export function SelectField(props: SelectProps) {
  const selectClass = props.error ? 'usa-select usa-input--error' : 'usa-select'
  return (
    <Frame {...props}>
      <select
        id={props.id}
        name={props.id}
        className={selectClass}
        value={props.value}
        disabled={props.disabled}
        aria-invalid={props.error ? true : undefined}
        aria-describedby={describedBy(props.id, props.hint, props.error)}
        onChange={(event) => props.onChange(event.target.value)}
      >
        {props.options.map((option) => (
          <option key={option.value} value={option.value} lang={option.lang}>{option.label}</option>
        ))}
      </select>
    </Frame>
  )
}

export function TextAreaField(props: AreaProps) {
  const areaClass = props.error ? 'usa-textarea usa-input--error' : 'usa-textarea'
  return (
    <Frame {...props}>
      <textarea
        id={props.id}
        name={props.id}
        className={areaClass}
        value={props.value}
        disabled={props.disabled}
        aria-describedby={describedBy(props.id, props.hint, props.error)}
        onChange={(event) => props.onChange(event.target.value)}
      />
    </Frame>
  )
}

export type FieldError = {
  id: string
  message: string
}

type SummaryProps = {
  errors: FieldError[]
  heading: string
  summaryRef: React.RefObject<HTMLDivElement | null>
}

export function ErrorSummary({ errors, heading, summaryRef }: SummaryProps) {
  if (errors.length === 0) {
    return null
  }
  return (
    <div className="usa-alert usa-alert--error margin-y-2" role="alert" tabIndex={-1} ref={summaryRef}>
      <div className="usa-alert__body">
        <h3 className="usa-alert__heading">{heading}</h3>
        <ul className="usa-list margin-bottom-0">
          {errors.map((error) => (
            <li key={error.id}>
              <a
                href={'#' + error.id}
                onClick={(event) => {
                  event.preventDefault()
                  document.getElementById(error.id)?.focus()
                }}
              >
                {error.message}
              </a>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
