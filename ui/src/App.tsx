import { Route, Routes } from 'react-router-dom'
import { Layout } from './components/Layout'
import { Admin } from './pages/Admin'
import { Home } from './pages/Home'
import { Lookup } from './pages/Lookup'
import { NotFound } from './pages/NotFound'
import { SignIn } from './pages/SignIn'
import { Workspace } from './pages/Workspace'

export function App() {
  return (
    <Layout>
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
