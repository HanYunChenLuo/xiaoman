import { readFileSync } from 'node:fs'
import { defineConfig } from 'vite'

const appVersion = JSON.parse(
  readFileSync(new URL('../version.json', import.meta.url), 'utf8')
).version as string

export default defineConfig({
  base: './',
  define: { __APP_VERSION__: JSON.stringify(appVersion) },
  server: { port: 5173, host: true },
  build: { outDir: 'dist', target: 'es2022' }
})
