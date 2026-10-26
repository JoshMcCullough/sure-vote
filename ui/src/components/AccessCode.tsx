import { useId, useState } from 'react'
import { useTranslation } from 'react-i18next'

type Props = {
  code: string
}

const groupSize = 4

function groups(code: string): string[] {
  const out: string[] = []
  for (let index = 0; index < code.length; index += groupSize) {
    out.push(code.slice(index, index + groupSize))
  }
  return out
}

export function AccessCode({ code }: Props) {
  const { t, i18n } = useTranslation()
  const spelledId = useId()
  const labelId = useId()
  const [status, setStatus] = useState('')
  const [spelled, setSpelled] = useState(false)
  const parts = groups(code)
  const list = new Intl.ListFormat(i18n.language, { style: 'short', type: 'unit' })

  function spell(char: string): string {
    if (/[A-Z]/.test(char)) {
      return t('accessCode.upper', { char })
    }
    if (/[a-z]/.test(char)) {
      return t('accessCode.lower', { char })
    }
    if (/[0-9]/.test(char)) {
      return t('accessCode.digit', { char })
    }
    if (char === '-') {
      return t('accessCode.hyphen')
    }
    if (char === '_') {
      return t('accessCode.underscore')
    }
    return char
  }

  async function copy() {
    try {
      await navigator.clipboard.writeText(code)
      setStatus(t('accessCode.copied'))
    } catch {
      setStatus(t('accessCode.copyFailed'))
    }
  }

  return (
    <div>
      <p id={labelId} className="text-bold margin-bottom-1">{t('accessCode.label', { count: code.length })}</p>
      <p className="sv-access-code" aria-labelledby={labelId} data-access-code="" translate="no">
        {parts.map((part, index) => (
          <span key={index} className="sv-code-group">{part}</span>
        ))}
      </p>
      <div className="usa-button-group margin-top-2">
        <button type="button" className="usa-button" onClick={() => void copy()}>
          {t('accessCode.copy')}
        </button>
        <button
          type="button"
          className="usa-button usa-button--outline"
          aria-expanded={spelled}
          aria-controls={spelledId}
          onClick={() => setSpelled(!spelled)}
        >
          {spelled ? t('accessCode.hideSpelled') : t('accessCode.showSpelled')}
        </button>
      </div>
      <p role="status" aria-live="polite" className="margin-y-1">{status}</p>
      <ol id={spelledId} hidden={!spelled} className="sv-spelled">
        {parts.map((part, index) => (
          <li key={index}>
            {t('accessCode.group', { number: index + 1, chars: list.format([...part].map(spell)) })}
          </li>
        ))}
      </ol>
    </div>
  )
}
