import { ReactNode, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, NavLink, useLocation } from 'react-router-dom'
import { GridContainer, Header, NavCloseButton, NavList, NavMenuButton, Title } from '@trussworks/react-uswds'
import { languageReviewed } from '../i18n'
import { LanguageSwitcher } from './LanguageSwitcher'

export type NavItem = {
  to: string
  key: string
  end: boolean
}

type Props = {
  children: ReactNode
  links: NavItem[]
  footer: string
}

export function Layout({ children, links, footer }: Props) {
  const { t, i18n } = useTranslation()
  const location = useLocation()
  const [expanded, setExpanded] = useState(false)
  const [lastPath, setLastPath] = useState(location.pathname)
  const firstRender = useRef(true)
  const menuButton = useRef<HTMLDivElement>(null)
  const closeButton = useRef<HTMLDivElement>(null)
  const reviewed = languageReviewed(i18n.language)

  if (lastPath !== location.pathname) {
    setLastPath(location.pathname)
    setExpanded(false)
  }

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    const frame = requestAnimationFrame(() => {
      const heading = document.querySelector<HTMLElement>('main h1')
      const main = document.getElementById('main-content')
      const target = heading ?? main
      target?.focus()
    })
    return () => cancelAnimationFrame(frame)
  }, [location.pathname])

  useEffect(() => {
    if (!expanded) {
      return
    }
    closeButton.current?.querySelector('button')?.focus()
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setExpanded(false)
        menuButton.current?.querySelector('button')?.focus()
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [expanded])

  function close() {
    setExpanded(false)
    menuButton.current?.querySelector('button')?.focus()
  }

  const items = links.map((link) => (
    <NavLink
      key={link.to}
      to={link.to}
      end={link.end}
      className={({ isActive }) => (isActive ? 'usa-nav-link usa-current' : 'usa-nav-link')}
    >
      <span>{t(link.key)}</span>
    </NavLink>
  ))

  return (
    <>
      <a className="usa-skipnav" href="#main-content">{t('nav.skip')}</a>
      <div className={expanded ? 'usa-overlay is-visible' : 'usa-overlay'} onClick={close} />
      <Header basic>
        <div className="usa-nav-container">
          <div className="usa-navbar">
            <Title>
              <Link to="/" title={t('app.home')}>{t('app.name')}</Link>
            </Title>
            <div className="sv-navbar-tools">
              <LanguageSwitcher />
              <div ref={menuButton}>
                <NavMenuButton
                  label={t('nav.menu')}
                  aria-expanded={expanded}
                  aria-controls="primary-nav"
                  onClick={() => setExpanded(true)}
                />
              </div>
            </div>
          </div>
          <nav id="primary-nav" aria-label={t('nav.label')} className={expanded ? 'usa-nav is-visible' : 'usa-nav'}>
            <div className="usa-nav__inner">
              <div ref={closeButton}>
                <NavCloseButton aria-label={t('nav.close')} onClick={close} />
              </div>
              <NavList items={items} type="primary" />
            </div>
          </nav>
        </div>
      </Header>
      <main id="main-content" tabIndex={-1} className="sv-heading-focus">
        <GridContainer className="padding-y-4">
          {reviewed ? null : (
            <div className="usa-alert usa-alert--info usa-alert--slim margin-bottom-3">
              <div className="usa-alert__body">
                <p className="usa-alert__text">{t('app.translationDraft')}</p>
              </div>
            </div>
          )}
          {children}
        </GridContainer>
      </main>
      <footer className="usa-footer usa-footer--slim">
        <div className="usa-footer__secondary-section">
          <GridContainer>
            <p className="margin-y-1">{t(footer)}</p>
          </GridContainer>
        </div>
      </footer>
    </>
  )
}
