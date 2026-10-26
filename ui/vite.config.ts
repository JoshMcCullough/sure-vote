import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'

const api = 'http://127.0.0.1:8080'
const uswds = fileURLToPath(new URL('./node_modules/@uswds/uswds/packages', import.meta.url))

export default defineConfig({
  base: '/app/',
  plugins: [react()],
  css: {
    preprocessorOptions: {
      scss: {
        loadPaths: [uswds],
        quietDeps: true,
      },
    },
  },
  server: {
    proxy: {
      '/health': api,
      '/auth': api,
      '/system': api,
      '/officials': api,
      '/workspace': api,
      '/lookup': api,
      '/validators': api,
      '/tenant-validators': api,
      '/tenants': api,
      '/persons': api,
      '/elections': api,
      '/localities': api,
      '/addresses': api,
      '/precincts': api,
      '/districts': api,
      '/precinct-districts': api,
      '/seats': api,
      '/terms': api,
      '/races': api,
      '/candidates': api,
      '/race-candidates': api,
      '/ballot-styles': api,
      '/places': api,
      '/devices': api,
      '/precinct-assignments': api,
      '/showcase': api,
      '/issuances': api,
      '/ballots': api,
      '/disputes': api,
    },
  },
})
