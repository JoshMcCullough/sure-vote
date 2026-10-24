import { Link, Route, Routes } from 'react-router-dom'
import { actorId } from './session'
import { Home } from './pages/Home'
import { Lookup } from './pages/Lookup'
import { Workspace } from './pages/Workspace'

export function App() {
  return (
    <>
      <header className="top">
        <Link to="/" className="brand">SureVote</Link>
        <nav>
          <Link to="/">Lookup</Link>
          <Link to="/workspace">Workspace</Link>
        </nav>
      </header>
      <main>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/workspace" element={<Workspace />} />
          <Route path="/elections/:electionId" element={<Lookup />} />
        </Routes>
      </main>
      <footer>
        Actor {actorId()}. This is an unsigned id stored in this browser. It is not an account.
      </footer>
    </>
  )
}
