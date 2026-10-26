import { defineConfig } from 'vite'
import { appConfig } from '../../packages/shared/vite.base'

export default defineConfig(appConfig('/voter/', ['/health', '/lookup', '/elections']))
