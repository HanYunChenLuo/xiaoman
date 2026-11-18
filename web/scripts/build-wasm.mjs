import { execSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const core = resolve(here, '../../core')
const out = resolve(here, '../src/wasm')

execSync('cargo build --target wasm32-unknown-unknown --release', { cwd: core, stdio: 'inherit' })
execSync(
  `wasm-bindgen target/wasm32-unknown-unknown/release/xiaoman_core.wasm --target web --out-dir "${out}"`,
  { cwd: core, stdio: 'inherit' }
)
