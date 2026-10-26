import { Route, Routes } from 'react-router-dom'
import { Layout, NavItem } from '@surevote/shared/components/Layout'
import { NotFound } from '@surevote/shared/components/NotFound'
import { Lookup } from '@surevote/shared/lookup/Lookup'
import { Home } from './Home'

const links: NavItem[] = [{ to: '/', key: 'nav.lookup', end: true }]

export function App() {
  return (
    <Layout links={links} footer="voter.footer">
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/elections/:electionId" element={<Lookup />} />
        <Route path="*" element={<NotFound />} />
      </Routes>
    </Layout>
  )
}
