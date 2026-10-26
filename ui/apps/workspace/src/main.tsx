import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { startI18n } from '@surevote/shared/i18n'
import enCommon from '@surevote/locales/en/common.json'
import enLookup from '@surevote/locales/en/lookup.json'
import enWorkspace from '@surevote/locales/en/workspace.json'
import esCommon from '@surevote/locales/es/common.json'
import esLookup from '@surevote/locales/es/lookup.json'
import esWorkspace from '@surevote/locales/es/workspace.json'
import '@surevote/shared/styles/index.scss'
import { App } from './App'

const root = document.getElementById('root')
if (!root) {
  throw new Error('missing root')
}

startI18n({
  en: { workspace: enWorkspace, common: enCommon, lookup: enLookup },
  es: { workspace: esWorkspace, common: esCommon, lookup: esLookup },
})

createRoot(root).render(
  <StrictMode>
    <BrowserRouter basename="/app">
      <App />
    </BrowserRouter>
  </StrictMode>,
)
