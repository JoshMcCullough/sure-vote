import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { withoutCredentials } from '@surevote/shared/api'
import { startI18n } from '@surevote/shared/i18n'
import enCommon from '@surevote/locales/en/common.json'
import enLookup from '@surevote/locales/en/lookup.json'
import esCommon from '@surevote/locales/es/common.json'
import esLookup from '@surevote/locales/es/lookup.json'
import '@surevote/shared/styles/index.scss'
import { App } from './App'

const root = document.getElementById('root')
if (!root) {
  throw new Error('missing root')
}

withoutCredentials()
startI18n({
  en: { common: enCommon, lookup: enLookup },
  es: { common: esCommon, lookup: esLookup },
})

createRoot(root).render(
  <StrictMode>
    <BrowserRouter basename="/voter">
      <App />
    </BrowserRouter>
  </StrictMode>,
)
