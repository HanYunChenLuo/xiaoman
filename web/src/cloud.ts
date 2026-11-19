/**
 * Web 端 Google 云备份：GIS Token 模型 + Drive appDataFolder。
 *
 * 与 Android 端共用同一 Google Cloud 项目的 appDataFolder 与云端协议；
 * 文件名、请求构造和响应解析都在 Rust `core/src/cloud.rs`，与 Android
 * 通过 JNI 共用同一份实现，因此两端的云端备份可互相恢复。
 *
 * 注意：Client ID 属于公开信息（SPA 无 client_secret）；token 存于
 * localStorage（`xiaoman_google_v1`），仅用于本机。桌面 Tauri 的自定义
 * scheme 不在 Google 授权来源内，`cloudSupported()` 会返回 false。
 */

import {
  cloudCreateRequest,
  cloudDownloadRequest,
  cloudFindRequest,
  cloudParseFile,
  cloudParseFileList,
  cloudUploadRequest,
  type CloudFile,
  type CloudParseResult,
  type CloudRequest
} from './core'

const CLIENT_ID = '114206446288-1of3gc0g5a4gbbo6h6da7rpehcj9rad4.apps.googleusercontent.com'
const SCOPES = 'openid email profile https://www.googleapis.com/auth/drive.appdata'
const GIS_SRC = 'https://accounts.google.com/gsi/client'
const STORE_KEY = 'xiaoman_google_v1'
const EXPIRY_MARGIN_MS = 60_000

export interface CloudAccount {
  email: string
  accessToken: string
  expiresAt: number
  lastSyncAt?: number
}

type TokenResponse = {
  access_token?: string
  expires_in?: number | string
  error?: string
  error_description?: string
}

type PendingRequest = {
  resolve: (resp: TokenResponse) => void
  reject: (err: Error) => void
  timer: number
}

let gisPromise: Promise<any> | null = null
let tokenClient: any = null
let pending: PendingRequest | null = null

// ---------------- 环境与存储 ----------------

export function cloudSupported(): boolean {
  if ((window as any).__TAURI_INTERNALS__) return false
  return location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1'
}

export function cloudUnsupportedReason(): string {
  if ((window as any).__TAURI_INTERNALS__) return '桌面端暂不支持 Google 登录'
  return '需要 HTTPS 或 localhost 环境'
}

export function getAccount(): CloudAccount | null {
  try {
    const raw = localStorage.getItem(STORE_KEY)
    if (!raw) return null
    const a = JSON.parse(raw) as CloudAccount
    if (!a || !a.accessToken) return null
    return a
  } catch {
    return null
  }
}

function saveAccount(a: CloudAccount | null): void {
  if (a) localStorage.setItem(STORE_KEY, JSON.stringify(a))
  else localStorage.removeItem(STORE_KEY)
}

export function cloudConnected(): boolean {
  return !!getAccount()?.email
}

/** token 是否仍在有效期内（含 1 分钟余量）。 */
export function cloudTokenFresh(): boolean {
  return tokenValid(getAccount())
}

export function accountEmail(): string {
  return getAccount()?.email ?? ''
}

export function lastSyncText(): string {
  const t = getAccount()?.lastSyncAt
  if (!t) return '从未上传'
  const d = new Date(t)
  const p = (n: number) => (n < 10 ? '0' + n : String(n))
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}

function tokenValid(a: CloudAccount | null): boolean {
  return !!a?.accessToken && a.expiresAt > Date.now() + EXPIRY_MARGIN_MS
}

/** 提前一分钟视为过期，避免请求途中失效。 */
function applyToken(token: string, expiresIn: number, email: string): CloudAccount {
  const prev = getAccount()
  const account: CloudAccount = {
    email: email || prev?.email || '',
    accessToken: token,
    expiresAt: Date.now() + Math.max(0, expiresIn * 1000),
    lastSyncAt: prev?.lastSyncAt
  }
  saveAccount(account)
  return account
}

// ---------------- GIS Token 模型 ----------------

function loadGis(): Promise<any> {
  const existing = (window as any).google
  if (existing?.accounts?.oauth2) return Promise.resolve(existing)
  if (gisPromise) return gisPromise
  gisPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = GIS_SRC
    script.async = true
    script.defer = true
    script.onload = () => {
      const g = (window as any).google
      if (g?.accounts?.oauth2) resolve(g)
      else {
        gisPromise = null
        reject(new Error('Google 登录组件加载异常'))
      }
    }
    script.onerror = () => {
      gisPromise = null
      reject(new Error('无法加载 Google 登录组件，请检查网络'))
    }
    document.head.append(script)
  })
  return gisPromise
}

function settle(resp: TokenResponse): void {
  if (!pending) return
  const p = pending
  pending = null
  clearTimeout(p.timer)
  if (resp?.error || !resp?.access_token) {
    let msg = 'Google 登录失败'
    if (resp?.error === 'popup_closed' || resp?.error === 'popup_closed_by_user') msg = '已关闭授权窗口'
    else if (resp?.error === 'interaction_required' || resp?.error === 'access_denied') msg = '未完成 Google 授权'
    else if (resp?.error_description) msg = `${msg}：${resp.error_description}`
    else if (resp?.error && resp.error !== 'error') msg = `${msg}：${resp.error}`
    p.reject(new Error(msg))
    return
  }
  p.resolve(resp)
}

async function requestToken(interactive: boolean): Promise<TokenResponse> {
  const g = await loadGis()
  if (!tokenClient) {
    tokenClient = g.accounts.oauth2.initTokenClient({
      client_id: CLIENT_ID,
      scope: SCOPES,
      callback: (resp: TokenResponse) => settle(resp),
      error_callback: (err: any) => settle({ error: err?.type ?? 'error', error_description: err?.message })
    })
  }
  if (pending) throw new Error('Google 登录正在进行，请稍候')
  return new Promise<TokenResponse>((resolve, reject) => {
    const timer = window.setTimeout(() => {
      if (!pending) return
      pending = null
      reject(new Error('Google 登录超时，请重试'))
    }, 180_000)
    pending = { resolve, reject, timer }
    try {
      tokenClient.requestAccessToken({ prompt: interactive ? 'select_account' : '' })
    } catch (e) {
      pending = null
      clearTimeout(timer)
      reject(e instanceof Error ? e : new Error(String(e)))
    }
  })
}

async function fetchEmail(token: string): Promise<string> {
  try {
    const res = await fetch('https://www.googleapis.com/oauth2/v3/userinfo', {
      headers: { Authorization: `Bearer ${token}` }
    })
    if (!res.ok) return ''
    const j = (await res.json()) as { email?: string }
    return String(j.email ?? '')
  } catch {
    return ''
  }
}

/** 取得可用 token；`interactive` 为 true 时允许弹出授权窗口。 */
async function ensureToken(interactive: boolean): Promise<string> {
  const acc = getAccount()
  if (tokenValid(acc)) return acc!.accessToken
  if (!interactive && !acc) throw new Error('请先登录 Google')
  let resp: TokenResponse
  try {
    resp = await requestToken(interactive)
  } catch (e) {
    if (!interactive) throw new Error('登录已过期，请重新登录')
    throw e
  }
  const token = resp.access_token!
  const email = acc?.email || (await fetchEmail(token))
  const expires = Number(resp.expires_in ?? 3600)
  applyToken(token, Number.isFinite(expires) && expires > 0 ? expires : 3600, email)
  return token
}

export async function signIn(): Promise<CloudAccount> {
  if (!cloudSupported()) throw new Error(cloudUnsupportedReason())
  const prev = getAccount()
  const resp = await requestToken(true)
  const token = resp.access_token!
  const expires = Number(resp.expires_in ?? 3600)
  const email = (await fetchEmail(token)) || prev?.email || 'Google 账号'
  return applyToken(token, Number.isFinite(expires) && expires > 0 ? expires : 3600, email)
}

export async function signOut(): Promise<void> {
  const acc = getAccount()
  saveAccount(null)
  if (!acc?.accessToken) return
  try {
    await fetch('https://oauth2.googleapis.com/revoke?token=' + encodeURIComponent(acc.accessToken), {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
    })
  } catch {
    /* 离线时忽略吊销失败 */
  }
}

// ---------------- Drive appDataFolder ----------------
// 请求构造与响应解析在 Rust core/src/cloud.rs（Android 通过 JNI 共用）。

class ApiError extends Error {
  constructor(readonly status: number, message: string) {
    super(message)
  }
}

function asError(result: CloudParseResult): ApiError {
  return new ApiError(result.status ?? 0, result.error ?? '云端请求失败')
}

async function callJson(
  req: CloudRequest,
  parse: (status: number, body: string) => CloudParseResult
): Promise<CloudParseResult> {
  const res = await fetch(req.url, { method: req.method, headers: req.headers, body: req.body })
  return parse(res.status, await res.text())
}

/** 首次 401 时尝试静默续期并重试一次。 */
async function authed<T>(fn: (token: string) => Promise<T>): Promise<T> {
  let token = await ensureToken(false)
  try {
    return await fn(token)
  } catch (e) {
    if (!(e instanceof ApiError) || e.status !== 401) throw e
    const acc = getAccount()
    if (acc) saveAccount({ ...acc, accessToken: '', expiresAt: 0 })
    token = await ensureToken(false)
    return fn(token)
  }
}

export async function remoteInfo(): Promise<CloudFile | null> {
  return authed((token) => findBackup(token))
}

export async function uploadBackup(bytes: Uint8Array): Promise<CloudFile> {
  return authed(async (token) => {
    let file = await findBackup(token)
    if (!file) {
      const created = await callJson(cloudCreateRequest(token), cloudParseFile)
      if (!created.ok || !created.file) throw asError(created)
      file = created.file
    }
    const req = cloudUploadRequest(token, file.id)
    const res = await fetch(req.url, {
      method: req.method,
      headers: req.headers,
      body: bytes as unknown as BodyInit
    })
    const parsed = cloudParseFile(res.status, await res.text())
    if (!parsed.ok) throw asError(parsed)
    const acc = getAccount()
    if (acc) saveAccount({ ...acc, lastSyncAt: Date.now() })
    return parsed.file ?? file
  })
}

async function findBackup(token: string): Promise<CloudFile | null> {
  const result = await callJson(cloudFindRequest(token), cloudParseFileList)
  if (!result.ok) throw asError(result)
  return result.file ?? null
}

export async function downloadBackup(): Promise<{ bytes: Uint8Array; file: CloudFile } | null> {
  return authed(async (token) => {
    const file = await findBackup(token)
    if (!file) return null
    const req = cloudDownloadRequest(token, file.id)
    const res = await fetch(req.url, { method: req.method, headers: req.headers })
    if (!res.ok) {
      const parsed = cloudParseFile(res.status, await res.text())
      throw asError(parsed)
    }
    return { bytes: new Uint8Array(await res.arrayBuffer()), file }
  })
}
