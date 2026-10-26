import { defineConfig } from 'vite'
import { appConfig } from '../../packages/shared/vite.base'

const routes = [
  '/health', '/auth', '/system', '/officials', '/workspace', '/lookup', '/validators', '/tenant-validators',
  '/persons', '/elections', '/localities', '/addresses', '/precincts', '/districts', '/precinct-districts', '/seats',
  '/terms', '/races', '/candidates', '/race-candidates', '/ballot-styles', '/places', '/devices',
  '/precinct-assignments', '/showcase', '/issuances', '/ballots', '/disputes',
]

export default defineConfig(appConfig('/app/', routes))
