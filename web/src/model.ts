import {
  backupExport,
  backupImport,
  backupMerge,
  decodeIas,
  encodeIas,
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

export function normalize(s: State): State {
  s.assets ??= []
  s.categories ??= []
  s.tags ??= []
  if (!Array.isArray(s.incomeCategories) || s.incomeCategories.length === 0) {
    s.incomeCategories = defaultIncomeCategories()
  }
  if (!Array.isArray(s.saveMoney)) s.saveMoney = []
  for (const a of s.assets) {
    if (!Array.isArray(a.incomeRecords)) a.incomeRecords = []
    if (!Array.isArray(a.priceRecords)) a.priceRecords = []
    if (!Array.isArray(a.usages)) a.usages = []
  }
  s.settings = {
    currency: s.settings?.currency ?? 'CNY',
    decimals: s.settings?.decimals ?? 2,
    thousands: s.settings?.thousands ?? false,
    theme: s.settings?.theme ?? 'system',
    notifyExpiry: s.settings?.notifyExpiry ?? false,
    updatedAt: s.settings?.updatedAt ?? 0
  }
  return s
}

export function uid(prefix: string): string {
  return prefix + Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
}

export function todayStr(): string {
  const d = new Date()
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
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

/** 某件资产的累计收入。 */
export function incomeTotal(a: Asset): number {
  return (a.incomeRecords ?? []).reduce((sum, r) => sum + (Number(r.amount) || 0), 0)
}

/** 最近一条价格记录（按日期倒序）。 */
export function latestPrice(a: Asset): PriceRecord | null {
  const list = a.priceRecords ?? []
  if (!list.length) return null
  return [...list].sort((x, y) => (x.date < y.date ? 1 : -1))[0]
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
  aliases: Array<{ alias: string; name: string; id: string; latin: boolean }>
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

function parseBrandBook(json: any): BrandBook {
  const book: BrandBook = emptyBrandBook()
  const source = json.brands ?? json
  book.versionName = json.dataVersionName ?? ''
  for (const cat of Object.keys(source)) {
    const list: Brand[] = []
    for (const item of source[cat] as unknown[]) {
      const arr = item as [string, string, string[], Array<[string, string[]]>]
      const brand: Brand = {
        name: arr[0],
        id: arr[1],
        aliases: arr[2] ?? [],
        groups: arr[3] ?? []
      }
      list.push(brand)
      book.byId.set(brand.id, brand)
      for (const alias of brand.aliases) {
        if (!alias) continue
        book.aliases.push({
          alias: alias.toLowerCase(),
          name: brand.name,
          id: brand.id,
          latin: /[A-Za-z]/.test(alias)
        })
      }
    }
    book.categories[cat] = list
  }
  book.aliases.sort((a, b) => b.alias.length - a.alias.length)
  return book
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
    text = await fetchBrandText(
      'https://api.github.com/repos/HanYunChenLuo/xiaoman/contents/data/brands.json',
      'application/vnd.github.raw'
    )
  } catch {
    text = await fetchBrandText(
      'https://raw.githubusercontent.com/HanYunChenLuo/xiaoman/main/data/brands.json?t=' + Date.now()
    )
  }
  const json = JSON.parse(text)
  const schema = Number(json.schema ?? 1)
  if (schema > 1) throw new Error('数据格式版本过高')
  const sup = json.supportedApp
  if (sup) {
    const min = Number(sup.minVersionCode ?? 0)
    const max = Number(sup.maxVersionCode ?? 0)
    if (1 < min || (max > 0 && 1 > max)) throw new Error('当前版本不支持该数据')
  }
  localStorage.setItem(BRANDS_CACHE, JSON.stringify(json))
  return String(json.dataVersionName ?? '')
}
export function matchBrand(text: string, book: BrandBook): Brand | null {
  const lower = text.trim().toLowerCase()
  if (!lower) return null
  for (const a of book.aliases) {
    const hit = a.latin
      ? new RegExp(`(?<![a-z0-9])${escapeRe(a.alias)}(?![a-z0-9])`).test(lower)
      : a.alias.length >= 2 && lower.includes(a.alias)
    if (hit) return book.byId.get(a.id) ?? null
  }
  return null
}

function escapeRe(s: string): string {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

const ICON_RULES: Array<[string, string[]]> = [
  ['phone', ['iphone', 'galaxy', 'pixel', 'mate', 'redmi', 'ipad', '平板', 'matepad', '手机', '小米', 'oppo', 'vivo', '一加', 'oneplus', '荣耀', 'honor', 'magic']],
  ['laptop', ['macbook', '笔记本', 'thinkpad', '小新', 'matebook', 'rog', '拯救者', '天选', 'xps', '轻薄本', '游戏本']],
  ['desktop', ['mac mini', 'mac studio', 'imac', '台式机', '台式', '主机', '组装机', '一体机']],
  ['game', ['switch', 'ns2', 'ns', 'ps5', 'ps4', 'playstation', 'xbox', '游戏机']],
  ['drone', ['dji', 'pocket', 'osmo', '动作相机', '运动相机', '摄像机', 'mavic', '无人机', 'drone']],
  ['camera', ['相机', 'camera', '单反', '微单', '镜头', 'gopro']],
  ['headphone', ['airpods', '耳机', 'headphone', '音箱', '音响', 'speaker']],
  ['watch', ['apple watch', '手表', '手环', 'watch']],
  ['tv', ['电视', 'tv', '显示器', 'monitor']],
  ['car', ['汽车', '理想', '特斯拉', '比亚迪', '蔚来', '小鹏', '宝马', '奔驰', '奥迪', '大众', '丰田', '本田', '日产', '保时捷', '沃尔沃', '极星', '马自达']],
  ['bike', ['自行车', '公路车', '山地车', '单车', 'bike', 'giant', '捷安特', 'trek']],
  ['moto', ['摩托', 'motorcycle']],
  ['scooter', ['滑板车', '电动滑板', 'scooter']],
  ['plane', ['飞机', 'airplane']]
]

export function autoIcon(text: string): string {
  const lower = text.toLowerCase()
  for (const [icon, keys] of ICON_RULES) {
    for (const k of keys) {
      const latin = /[a-z]/.test(k)
      const hit = latin
        ? new RegExp(`(?<![a-z0-9])${escapeRe(k)}(?![a-z0-9])`).test(lower)
        : k.length >= 2 && lower.includes(k)
      if (hit) return `/icons/${icon}.png`
    }
  }
  return '/icons/default.png'
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
