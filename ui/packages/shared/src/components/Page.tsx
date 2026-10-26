import { ReactNode, useEffect, useId } from 'react'
import { useTranslation } from 'react-i18next'

type HeadingProps = {
  children: string
}

export function PageHeading({ children }: HeadingProps) {
  const { t } = useTranslation()

  useEffect(() => {
    document.title = t('app.documentTitle', { page: children })
  }, [children, t])

  return <h1 tabIndex={-1} className="sv-heading-focus font-heading-xl margin-top-0">{children}</h1>
}

type PanelProps = {
  title: string
  headingId?: string
  children: ReactNode
}

export function Panel({ title, headingId, children }: PanelProps) {
  const generated = useId()
  const id = headingId ?? generated
  return (
    <section className="usa-card margin-bottom-3" aria-labelledby={id}>
      <div className="usa-card__container margin-x-0">
        <div className="usa-card__header">
          <h2 className="usa-card__heading" id={id}>{title}</h2>
        </div>
        <div className="usa-card__body">{children}</div>
      </div>
    </section>
  )
}

type AlertProps = {
  message: string | null
}

export function ErrorAlert({ message }: AlertProps) {
  if (!message) {
    return null
  }
  return (
    <div className="usa-alert usa-alert--error usa-alert--slim margin-y-2" role="alert" data-error-banner="">
      <div className="usa-alert__body">
        <p className="usa-alert__text">{message}</p>
      </div>
    </div>
  )
}

export function SuccessAlert({ message }: AlertProps) {
  if (!message) {
    return null
  }
  return (
    <div className="usa-alert usa-alert--success usa-alert--slim margin-y-2" role="status">
      <div className="usa-alert__body">
        <p className="usa-alert__text">{message}</p>
      </div>
    </div>
  )
}
