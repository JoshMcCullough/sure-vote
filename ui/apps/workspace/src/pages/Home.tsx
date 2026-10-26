import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { PageHeading, Panel } from '@surevote/shared/components/Page'

export function Home() {
  const { t } = useTranslation()
  return (
    <>
      <PageHeading>{t('home.title')}</PageHeading>
      <p className="usa-intro">{t('home.intro')}</p>
      <div className="grid-row grid-gap">
        <div className="grid-col-12 desktop:grid-col-8">
          <Panel title={t('home.workspaceTitle')}>
            <p>{t('home.workspaceBody')}</p>
            <p>{t('home.workspaceKeys')}</p>
            <Link className="usa-button" to="/workspace">{t('home.openWorkspace')}</Link>
          </Panel>
        </div>
      </div>
    </>
  )
}
