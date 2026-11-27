import {
  backupExport,
  backupImport,
  backupMerge,
  brandIconKey,
  brandsApiUrl,
  brandsMatch,
  brandsParse,
  brandsRawUrl,
  brandsValidate,
  decodeIas,
  encodeIas,
  incomeTotalState,
  latestPriceState,
  stateNormalize,
  todayString,
  tzOffsetMs
} from './core'

export interface Category {
  id: string
  name: string
}

export interface IncomeRecord {
  id: string
  amount: number
  date: string
  categoryId?: string
  note?: string
}

export interface PriceRecord {
  id: string
  date: string
  price: number
}

export interface UsageRecord {
  id: string
  date: string
  note?: string
}

export interface Asset {
  id: string
  name: string
  model?: string
  price: number
  buyDate: string
  kind: 'asset' | 'wish'
  status: 'serving' | 'retired' | 'sold'
  rule?: 'day' | 'count' | 'custom'
  categoryId?: string
  tagIds?: string[]
  icon?: string
  note?: string
  soldPrice?: number
  soldDate?: string
  retireDate?: string
  secondhandValue?: number
  secondhandInCost?: boolean
  excludeTotal?: boolean
  brandId?: string
  brandName?: string
  brandAuto?: boolean
  targetCost?: number
  targetDate?: string
  uses?: number
  customDays?: number
  targetUsageDays?: number
  coverData?: string
  noteImagesData?: string[]
  expireAt?: string
  favorite?: boolean
  incomeRecords?: IncomeRecord[]
  priceRecords?: PriceRecord[]
  /** 原 App 的使用记录；Web 不展示，但随备份往返保留。 */
  usages?: UsageRecord[]
}

export interface Settings {
  currency: string
  decimals: number
  thousands: boolean
  theme: 'system' | 'light' | 'dark'
  notifyExpiry?: boolean
  /** 本地最后一次修改时间（毫秒），用于导入时判断哪边更新。 */
  updatedAt?: number
}

export interface State {
  assets: Asset[]
  categories: Category[]
  tags: Category[]
  incomeCategories: Category[]
  /** 原 App 的存钱记录（不透明数组）；Web 不展示，但随备份往返保留。 */
  saveMoney: unknown[]
  settings: Settings
}

const KEY = 'xiaoman_web_v1'

export function loadState(): State {
  try {
    const raw = localStorage.getItem(KEY)
    if (raw) {
      const s = JSON.parse(raw)
      if (Array.isArray(s.assets)) return normalize(s)
    }
  } catch {
    /* ignore */
  }
  return normalize({
    assets: [],
    categories: [{ id: 'cat_digital', name: '数码' }],
    tags: [],
    incomeCategories: defaultIncomeCategories(),
    saveMoney: [],
    settings: { currency: 'CNY', decimals: 2, thousands: false, theme: 'system' }
  })
}

function defaultIncomeCategories(): Category[] {
  return ['出租', '工资', '理财', '其他'].map((name, i) => ({ id: `inc_cat_${i}`, name }))
}

export function saveState(s: State): void {
  localStorage.setItem(KEY, JSON.stringify(s))
}

/** 数组、收入分类与默认设置由 core 统一补齐（与 Android 共用一份实现）。 */
export function normalize(s: State): State {
  return JSON.parse(
    stateNormalize(s, { thousands: false, notifyExpiry: false })
  ) as State
}

export function uid(prefix: string): string {
  return prefix + Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
}

export function todayStr(): string {
  return todayString(Date.now(), tzOffsetMs())
}

export function pad(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

export function sym(currency: string): string {
  switch (currency) {
    case 'USD':
      return '$'
    case 'EUR':
      return '€'
    case 'JPY':
      return '¥'
    case 'GBP':
      return '£'
    case 'HKD':
      return 'HK$'
    default:
      return '¥'
  }
}

export function fmt(v: number, settings: Settings): string {
  return v.toLocaleString('zh-CN', {
    minimumFractionDigits: settings.decimals,
    maximumFractionDigits: settings.decimals,
    useGrouping: settings.thousands
  })
}

export function money(v: number, settings: Settings): string {
  return sym(settings.currency) + fmt(v, settings)
}

export function statusText(s: string): string {
  if (s === 'retired') return '已退役'
  if (s === 'sold') return '已卖出'
  return '服役中'
}

export function displayName(a: Asset): string {
  return a.name?.trim() ? a.name : a.model ?? ''
}

/** 某件资产的累计收入（core 计算）。 */
export function incomeTotal(a: Asset): number {
  return incomeTotalState(a)
}

/** 最近一条价格记录（按日期最大；core 计算）。 */
export function latestPrice(a: Asset): PriceRecord | null {
  return (latestPriceState(a) as PriceRecord | null) ?? null
}

// ---------------- 品牌与型号 ----------------
export interface Brand {
  name: string
  id: string
  aliases: string[]
  groups: Array<[string, string[]]>
}

export interface BrandBook {
  categories: Record<string, Brand[]>
  byId: Map<string, Brand>
  /** core 输出的别名表：`[aliasLower, name, id, latin(1/0)]`，按长度倒序。 */
  aliases: Array<[string, string, string, number]>
  versionName: string
}

const BRANDS_CACHE = 'xiaoman_brands_v1'

export async function loadBrands(): Promise<BrandBook> {
  let json: any = null
  try {
    const raw = localStorage.getItem(BRANDS_CACHE)
    if (raw) json = JSON.parse(raw)
  } catch {
    json = null
  }
  if (!json) {
    try {
      const res = await fetch('./brands.json', { cache: 'no-cache' })
      json = await res.json()
    } catch {
      json = null
    }
  }
  return json ? parseBrandBook(json) : emptyBrandBook()
}

function emptyBrandBook(): BrandBook {
  return { categories: {}, byId: new Map(), aliases: [], versionName: '' }
}

function parseBrandBook(json: unknown): BrandBook {
  const parsed = JSON.parse(brandsParse(JSON.stringify(json))) as {
    categories?: Record<string, Brand[]>
    byId?: Record<string, Brand>
    aliases?: Array<[string, string, string, number]>
    versionName?: string
  }
  return {
    categories: parsed.categories ?? {},
    byId: new Map(Object.entries(parsed.byId ?? {})),
    aliases: parsed.aliases ?? [],
    versionName: parsed.versionName ?? ''
  }
}

async function fetchBrandText(url: string, accept?: string): Promise<string> {
  const res = await fetch(url, {
    cache: 'no-store',
    headers: accept ? { Accept: accept } : undefined
  })
  if (!res.ok) throw new Error('HTTP ' + res.status)
  return res.text()
}

/** 从 GitHub 拉取最新品牌数据并写入本地缓存；返回版本名。 */
export async function fetchRemoteBrands(): Promise<string> {
  let text: string
  try {
    text = await fetchBrandText(brandsApiUrl(), 'application/vnd.github.raw')
  } catch {
    text = await fetchBrandText(brandsRawUrl() + '?t=' + Date.now())
  }
  const check = JSON.parse(brandsValidate(text, 1)) as {
    ok: boolean
    versionName: string
    error: string
  }
  if (!check.ok) throw new Error(check.error)
  localStorage.setItem(BRANDS_CACHE, text)
  return String(check.versionName ?? '')
}

export function matchBrand(text: string, book: BrandBook): Brand | null {
  const out = brandsMatch(text, JSON.stringify(book.aliases))
  if (!out || out === 'null') return null
  const hit = JSON.parse(out) as { name: string; id: string }
  return book.byId.get(hit.id) ?? null
}

/** 型号/名称 → 本地图标路径（推断规则在 core）。 */
export function autoIcon(text: string): string {
  const key = brandIconKey(text)
  return key === 'default' ? '/icons/default.png' : `/icons/${key}.png`
}

// ---------------- .ias 备份 ----------------
// 字段映射与合并策略在 core/src/state.rs（与 Android 共用）；这里只负责
// 图片名称 ↔ data URL 的转换。

export interface ImportedState {
  state: Partial<State>
  count: number
  exportedAt: number
  /** 备份数据的本地最后修改时间（毫秒）；旧备份没有该字段时为 0。 */
  updatedAt: number
  /** 用于和本地比较新旧的备份时间（updatedAt，回退 exported_at）。 */
  backupTimeMs: number
}

function dataUrlToBase64(dataUrl: string): string {
  const i = dataUrl.indexOf('base64,')
  return i >= 0 ? dataUrl.slice(i + 7) : ''
}

function extOf(dataUrl: string): string {
  return dataUrl.startsWith('data:image/png') ? '.png' : '.jpg'
}

function dataUrlOf(name: string, map: Record<string, string>): string | undefined {
  const b = map[name]
  if (!b) return undefined
  const mime = name.toLowerCase().endsWith('.png') ? 'png' : 'jpeg'
  return `data:image/${mime};base64,${b}`
}

export function exportIas(state: State): Uint8Array {
  const images: Record<string, string> = {}
  const assets = state.assets.map((a) => {
    const c: any = { ...a }
    delete c.coverData
    delete c.noteImagesData
    if (a.coverData) {
      const name = `images/${a.id}_cover${extOf(a.coverData)}`
      images[name] = dataUrlToBase64(a.coverData)
      c.coverImage = name
    }
    const notes = (a.noteImagesData ?? []).map((d, i) => {
      const name = `images/${a.id}_note${i}${extOf(d)}`
      images[name] = dataUrlToBase64(d)
      return name
    })
    c.noteImages = notes
    return c
  })
  const canonical = {
    assets,
    categories: state.categories,
    tags: state.tags,
    incomeCategories: state.incomeCategories,
    saveMoney: state.saveMoney ?? [],
    settings: { updatedAt: state.settings.updatedAt ?? 0 }
  }
  const payload = JSON.parse(
    backupExport(
      JSON.stringify(canonical),
      JSON.stringify(images),
      'WEB',
      Date.now(),
      tzOffsetMs()
    )
  )
  return encodeIas(payload)
}

export function importIas(bytes: Uint8Array): ImportedState {
  const parsed = decodeIas(bytes) as { data: any; images?: Record<string, string> }
  const res = JSON.parse(
    backupImport(JSON.stringify(parsed), Date.now(), tzOffsetMs())
  ) as { state?: any; meta?: any }
  const state: any = res.state ?? {}
  const imageMap = parsed.images ?? {}
  const assets: any[] = Array.isArray(state.assets) ? state.assets : []
  for (const a of assets) {
    const coverName = a.coverImage
    delete a.coverImage
    if (coverName) {
      const cover = dataUrlOf(String(coverName), imageMap)
      if (cover) a.coverData = cover
    }
    const noteNames = a.noteImages
    delete a.noteImages
    if (Array.isArray(noteNames)) {
      const urls = noteNames
        .map((n) => dataUrlOf(String(n), imageMap))
        .filter((x): x is string => !!x)
      if (urls.length) a.noteImagesData = urls
    }
    if (!a.icon) a.icon = autoIcon(a.model || a.name)
    if (!Array.isArray(a.usages)) a.usages = []
  }
  const meta = res.meta ?? {}
  return {
    state: state as Partial<State>,
    count: Number(meta.count ?? assets.length),
    exportedAt: Number(meta.exportedAt ?? 0),
    updatedAt: Number(meta.updatedAt ?? 0),
    backupTimeMs: Number(meta.backupTimeMs ?? 0)
  }
}

/** 合并本地与导入状态；返回的数组由 Rust 统一决定去重/取新语义。 */
export function mergeIasState(
  local: State,
  incoming: Partial<State>,
  replace: boolean,
  preferIncoming: boolean
): Partial<State> {
  return JSON.parse(
    backupMerge(JSON.stringify(local), JSON.stringify(incoming), replace, preferIncoming)
  ) as Partial<State>
}
