import { useId } from 'react'
import { useTranslation } from 'react-i18next'
import { languages } from '../i18n'

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation()
  const id = useId()
  const current = i18n.resolvedLanguage ?? i18n.language
  const others = languages.filter((language) => language.code !== current)

  if (others.length === 1) {
    const other = others[0]
    return (
      <div className="usa-language-container usa-language--small">
        <button
          type="button"
          className="usa-button usa-button--outline usa-language__link margin-0"
          lang={other.code}
          onClick={() => void i18n.changeLanguage(other.code)}
        >
          {other.name}
        </button>
      </div>
    )
  }

  return (
    <div>
      <label className="usa-sr-only" htmlFor={id}>{t('nav.language')}</label>
      <select
        id={id}
        className="usa-select"
        value={current}
        onChange={(event) => void i18n.changeLanguage(event.target.value)}
      >
        {languages.map((language) => (
          <option key={language.code} value={language.code} lang={language.code}>{language.name}</option>
        ))}
      </select>
    </div>
  )
}
