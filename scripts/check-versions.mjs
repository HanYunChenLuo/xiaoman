#!/usr/bin/env node
/**
 * 版本单一来源校验：根目录 version.json 是唯一需要维护的版本号。
 * 用法：node scripts/check-versions.mjs v1.0.3
 */
import fs from 'node:fs'

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8')
const versionJson = JSON.parse(read('../version.json'))
const version = String(versionJson.version ?? '')
const tag = (process.argv[2] ?? '').replace(/^v/, '')

const errors = []
if (!/^\d+\.\d+\.\d+$/.test(version)) errors.push(`version.json 版本号无效：${version || '(空)'}`)

const tauri = JSON.parse(read('../desktop/src-tauri/tauri.conf.json'))
if (tauri.version !== '../../version.json') {
  errors.push('tauri.conf.json > version 必须指向 ../../version.json')
}

const android = read('../apps/android/app/build.gradle')
if (!android.includes("../../version.json") || !android.includes('versionName appVersion')) {
  errors.push('Android build.gradle 未从 version.json 读取版本')
}
if (/versionCode\s+\d+/.test(android) || /versionName\s+'/.test(android)) {
  errors.push('Android build.gradle 仍包含硬编码 versionCode/versionName')
}

const updateTs = read('../web/src/update.ts')
if (!updateTs.includes('__APP_VERSION__')) errors.push('web/src/update.ts 未使用 __APP_VERSION__')

const viteConfig = read('../web/vite.config.ts')
if (!viteConfig.includes("'../version.json'") && !viteConfig.includes('"../version.json"')) {
  errors.push('web/vite.config.ts 未读取根目录 version.json')
}

if (tag && tag !== version) errors.push(`tag v${tag} ≠ version.json ${version}`)

if (errors.length) {
  console.error('版本校验失败：\n- ' + errors.join('\n- '))
  process.exit(1)
}

const parts = version.split('.').map((x) => parseInt(x, 10))
const versionCode = parts[0] * 10000 + parts[1] * 100 + parts[2]
console.log(`版本唯一来源：${version}（Android versionCode ${versionCode}）`)
