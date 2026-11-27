import init, * as wasm from './wasm/xiaoman_core.js'

let ready = false

export async function initCore(): Promise<void> {
  if (ready) return
  await init()
  ready = true
}

export function coreVersion(): string {
  return wasm.version()
}

export function dailyCost(asset: unknown): number {
  return wasm.daily_cost(JSON.stringify(asset), Date.now(), tzOffsetMs())
}

export function dailyOfKind(assets: unknown[], kind: string): number {
  return wasm.daily_of_kind(JSON.stringify(assets), kind, Date.now(), tzOffsetMs())
}

export function totalOfKind(assets: unknown[], kind: string): number {
  return wasm.total_of_kind(JSON.stringify(assets), kind)
}

export function countOfKind(assets: unknown[], kind: string, status: string): number {
  return wasm.count_of_kind(JSON.stringify(assets), kind, status)
}

// ---------------- 品牌/型号/图标（core/src/brands.rs） ----------------

/** 品牌数据 → book JSON：`{brands, categories, byId, models, aliases, versionName}`。 */
export function brandsParse(json: string): string {
  return wasm.brands_parse(json)
}

/** 别名匹配；`aliasesJson` 为 book.aliases 的 JSON。返回 `{name,id}` 或 `null`。 */
export function brandsMatch(text: string, aliasesJson: string): string {
  return wasm.brands_match(text, aliasesJson)
}

/** 型号/名称 → 图标 key（`phone` / `laptop` / … / `default`）。 */
export function brandIconKey(text: string): string {
  return wasm.brand_icon_key(text)
}

/** 校验品牌数据兼容性；返回 `{ok, version, versionName, error}`。 */
export function brandsValidate(json: string, appVersionCode: number): string {
  return wasm.brands_validate(json, appVersionCode)
}

export function brandsApiUrl(): string {
  return wasm.brands_api_url()
}

export function brandsRawUrl(): string {
  return wasm.brands_raw_url()
}

export function todayString(nowMs: number, tzOffset: number): string {
  return wasm.today_string(nowMs, tzOffset)
}

/** 最近 `days` 天的资产总值序列（末尾为今天）。 */
export function trendSeries(assets: unknown[], days: number): number[] {
  return JSON.parse(wasm.trend_series(JSON.stringify(assets), days, Date.now(), tzOffsetMs()))
}

export function categoryDistribution(
  assets: unknown[],
  categories: unknown[]
): Array<{ name: string; count: number }> {
  return JSON.parse(
    wasm.category_distribution(JSON.stringify(assets), JSON.stringify(categories))
  )
}

export function incomeTotalState(asset: unknown): number {
  return wasm.income_total(JSON.stringify(asset))
}

export function latestPriceState(asset: unknown): unknown {
  return JSON.parse(wasm.latest_price(JSON.stringify(asset)))
}

/** 本地状态规范化；defaults 是各端特有的设置默认值。 */
export function stateNormalize(state: unknown, defaults: Record<string, unknown>): string {
  return wasm.state_normalize(JSON.stringify(state), JSON.stringify(defaults))
}

export function ageDays(asset: unknown, today: string): number {
  return wasm.age_days(JSON.stringify(asset), today)
}

export function netCost(asset: unknown): number {
  return wasm.net_cost(JSON.stringify(asset))
}

export function encodeIas(payload: unknown): Uint8Array {
  return wasm.encode_ias(JSON.stringify(payload))
}

export function decodeIas(bytes: Uint8Array): unknown {
  return JSON.parse(wasm.decode_ias(bytes))
}

// ---------------- .ias 语义映射（core/src/state.rs） ----------------

/** 规范状态 + 图片名称 -> 备份 payload JSON（交给 encodeIas）。 */
export function backupExport(
  state: string,
  images: string,
  platform: string,
  nowMs: number,
  tzOffset: number
): string {
  return wasm.backup_export(state, images, platform, nowMs, tzOffset)
}

/** 备份 payload JSON -> `{state, meta}` JSON。 */
export function backupImport(payload: string, nowMs: number, tzOffset: number): string {
  return wasm.backup_import(payload, nowMs, tzOffset)
}

/** 合并本地与导入状态；返回完整状态 JSON。 */
export function backupMerge(
  local: string,
  incoming: string,
  replace: boolean,
  preferIncoming: boolean
): string {
  return wasm.backup_merge(local, incoming, replace, preferIncoming)
}

export function backupTimeMs(payload: string): number {
  return wasm.backup_time_ms(payload)
}

// ---------------- 云备份协议（core/src/cloud.rs） ----------------

export interface CloudRequest {
  method: string
  url: string
  headers: Record<string, string>
  body?: string
}

export interface CloudFile {
  id: string
  name?: string
  modifiedTime?: string
  size?: string
}

export interface CloudParseResult {
  ok: boolean
  file?: CloudFile | null
  status?: number
  relogin?: boolean
  error?: string
}

export function cloudBackupName(): string {
  return wasm.cloud_backup_name()
}

export function cloudFindRequest(token: string): CloudRequest {
  return JSON.parse(wasm.cloud_find_request(token))
}

export function cloudCreateRequest(token: string): CloudRequest {
  return JSON.parse(wasm.cloud_create_request(token))
}

export function cloudUploadRequest(token: string, fileId: string): CloudRequest {
  return JSON.parse(wasm.cloud_upload_request(token, fileId))
}

export function cloudDownloadRequest(token: string, fileId: string): CloudRequest {
  return JSON.parse(wasm.cloud_download_request(token, fileId))
}

export function cloudParseFileList(status: number, body: string): CloudParseResult {
  return JSON.parse(wasm.cloud_parse_file_list(status, body))
}

export function cloudParseFile(status: number, body: string): CloudParseResult {
  return JSON.parse(wasm.cloud_parse_file(status, body))
}

/** Drive 的 ISO 8601 时间 → 毫秒；失败返回 0。 */
export function cloudTimeMs(iso: string): number {
  return wasm.cloud_time_ms(iso)
}

export interface CloudConflict {
  newer: boolean
  remoteMs: number
}

/** 云端是否比本地新（且不是我们上次上传后见过的那份）。 */
export function cloudConflict(
  remoteIso: string,
  localMs: number,
  lastRemoteMs: number
): CloudConflict {
  return JSON.parse(wasm.cloud_conflict(remoteIso, localMs, lastRemoteMs))
}

export function tzOffsetMs(): number {
  return -new Date().getTimezoneOffset() * 60000
}
