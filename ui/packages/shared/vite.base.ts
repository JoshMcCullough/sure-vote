import react from '@vitejs/plugin-react'
import { fileURLToPath } from 'node:url'
import type { UserConfig } from 'vite'

const ui = fileURLToPath(new URL('../../', import.meta.url))
const shared = fileURLToPath(new URL('./src', import.meta.url))
const locales = fileURLToPath(new URL('./locales', import.meta.url))
const uswds = fileURLToPath(new URL('../../node_modules/@uswds/uswds/packages', import.meta.url))
const api = 'http://127.0.0.1:8080'

export function appConfig(base: string, routes: string[]): UserConfig {
  const proxy = Object.fromEntries(routes.map((route) => [route, api]))
  return {
    base,
    plugins: [react()],
    resolve: {
      alias: { '@surevote/shared': shared, '@surevote/locales': locales },
    },
    css: {
      preprocessorOptions: {
        scss: {
          loadPaths: [uswds],
          quietDeps: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      emptyOutDir: true,
    },
    server: {
      fs: { allow: [ui] },
      proxy,
    },
  }
}
