#!/usr/bin/env node
/**
 * 生成桌面端内置 OAuth 凭据文件（AES-256-GCM）。
 *
 * 用法（不要提交生成的文件）：
 *   XIAOMAN_GOOGLE_CLIENT_ID=... XIAOMAN_GOOGLE_CLIENT_SECRET=... \
 *     node desktop/scripts/seed-oauth.mjs
 *
 * 可选参数：
 *   --id <client id>       --secret <client secret>
 *   --out <path>           默认 desktop/src-tauri/oauth.enc
 *
 * 密钥与 Rust 端 `OAUTH_FILE_KEY_SEED` 保持一致；加密仅用于避免明文入库，
 * 程序必须能自行解密，因此不构成机密性保证。
 */
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SEED = 'xiaoman-desktop/oauth-file/v1/5b0f6c1e'

const args = process.argv.slice(2)
const arg = (name) => {
  const i = args.indexOf('--' + name)
  return i >= 0 ? args[i + 1] : undefined
}

const clientId = (arg('id') ?? process.env.XIAOMAN_GOOGLE_CLIENT_ID ?? '').trim()
const clientSecret = (arg('secret') ?? process.env.XIAOMAN_GOOGLE_CLIENT_SECRET ?? '').trim()
const out =
  arg('out') ?? path.resolve(fileURLToPath(new URL('../src-tauri/oauth.enc', import.meta.url)))

if (!clientId || !clientSecret) {
  console.error('缺少凭据。请设置 XIAOMAN_GOOGLE_CLIENT_ID / XIAOMAN_GOOGLE_CLIENT_SECRET，或使用 --id/--secret。')
  process.exit(1)
}

const key = crypto.createHash('sha256').update(SEED, 'utf8').digest()
const nonce = crypto.randomBytes(12)
const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce)
const plain = Buffer.from(JSON.stringify({ clientId, clientSecret }), 'utf8')
const body = Buffer.concat([cipher.update(plain), cipher.final(), cipher.getAuthTag()])
const data = Buffer.concat([nonce, body])

fs.mkdirSync(path.dirname(out), { recursive: true })
fs.writeFileSync(out, data, { mode: 0o600 })
try {
  fs.chmodSync(out, 0o600)
} catch {
  /* ignore */
}
console.log(`已写入 ${out}（${data.length} 字节，client ${clientId.slice(0, 12)}…）`)
