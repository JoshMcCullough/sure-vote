import { useEffect, useState } from 'react'
import { Route, Routes } from 'react-router-dom'
import { api } from '@surevote/shared/api'
import { Layout, NavItem } from '@surevote/shared/components/Layout'
import { NotFound } from '@surevote/shared/components/NotFound'
import { Lookup } from '@surevote/shared/lookup/Lookup'
import { Admin } from './pages/Admin'
import { Home } from './pages/Home'
import { SignIn } from './pages/SignIn'
import { Workspace } from './pages/Workspace'

const workspaceLinks: NavItem[] = [
  { to: '/', key: 'nav.home', end: true },
  { to: '/workspace', key: 'nav.workspace', end: false },
]

const officialLinks: NavItem[] = [
  { to: '/admin', key: 'nav.officials', end: false },
  { to: '/sign-in', key: 'nav.signIn', end: false },
]

type Config = {
  surface?: string
}

export function App() {
  const [surface, setSurface] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    api<Config>('GET', '/auth/config').then(
      (config) => active && setSurface(config.surface ?? 'all'),
      () => active && setSurface('all'),
    )
    return () => {
      active = false
    }
  }, [])

  const links = surface === 'demo' ? workspaceLinks : [...workspaceLinks, ...officialLinks]
  const footer = surface === 'demo' ? 'app.demoFooter' : 'app.footer'

  return (
    <Layout links={links} footer={footer}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/workspace" element={<Workspace />} />
        <Route path="/sign-in" element={<SignIn />} />
        <Route path="/admin" element={<Admin />} />
        <Route path="/elections/:electionId" element={<Lookup />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Layout>
  )
}
