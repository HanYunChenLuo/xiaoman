#!/usr/bin/env node
/**
 * 校验发版时各端版本号一致，并可对照 tag。
 * 用法：node scripts/check-versions.mjs v1.0.2
 */
import fs from 'node:fs'

const read = (path) => fs.readFileSync(new URL(path, import.meta.url), 'utf8')
const android = read('../apps/android/app/build.gradle')
const androidVersion = /versionName\s+'([^']+)'/.exec(android)?.[1] ?? ''
const androidCode = /versionCode\s+(\d+)/.exec(android)?.[1] ?? ''
const desktopVersion = JSON.parse(read('../desktop/src-tauri/tauri.conf.json')).version ?? ''
const webVersion = /APP_VERSION\s*=\s*'([^']+)'/.exec(read('../web/src/update.ts'))?.[1] ?? ''
const tag = (process.argv[2] ?? '').replace(/^v/, '')

const errors = []
if (!androidVersion) errors.push('无法读取 Android versionName')
if (!desktopVersion) errors.push('无法读取桌面 version')
if (!webVersion) errors.push('无法读取 Web APP_VERSION')
if (androidVersion && desktopVersion && androidVersion !== desktopVersion) {
  errors.push(`Android ${androidVersion} ≠ 桌面 ${desktopVersion}`)
}
if (androidVersion && webVersion && androidVersion !== webVersion) {
  errors.push(`Android ${androidVersion} ≠ Web ${webVersion}`)
}
if (tag && androidVersion && tag !== androidVersion) {
  errors.push(`tag v${tag} ≠ versionName ${androidVersion}`)
}
if (!androidCode) errors.push('无法读取 Android versionCode')

if (errors.length) {
  console.error('版本校验失败：\n- ' + errors.join('\n- '))
  process.exit(1)
}
console.log(`版本一致：${androidVersion}（versionCode ${androidCode}）`)
