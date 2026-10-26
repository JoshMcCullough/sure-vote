import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { PageHeading } from '../components/Page'

export function NotFound() {
  const { t } = useTranslation()
  return (
    <>
      <PageHeading>{t('notFound.title')}</PageHeading>
      <p>{t('notFound.body')}</p>
      <p><Link to="/">{t('notFound.home')}</Link></p>
    </>
  )
}
