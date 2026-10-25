import { Link, Route, Routes } from 'react-router-dom'
import { Admin } from './pages/Admin'
import { Home } from './pages/Home'
import { Lookup } from './pages/Lookup'
import { SignIn } from './pages/SignIn'
import { Workspace } from './pages/Workspace'

export function App() {
  return (
    <>
      <header className="top">
        <Link to="/" className="brand">SureVote</Link>
        <nav>
          <Link to="/">Lookup</Link>
          <Link to="/workspace">Workspace</Link>
          <Link to="/admin">Officials</Link>
          <Link to="/sign-in">Sign in</Link>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/workspace" element={<Workspace />} />
          <Route path="/sign-in" element={<SignIn />} />
          <Route path="/admin" element={<Admin />} />
          <Route path="/elections/:electionId" element={<Lookup />} />
        </Routes>
      </main>
      <footer>
        Officials and system admins sign in with a passkey. The demo workspace uses a visitor session in the demo tenant.
      </footer>
    </>
  )
}
