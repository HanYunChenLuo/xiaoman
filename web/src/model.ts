import { decodeIas, encodeIas } from './core'

export interface Category {
  id: string
  name: string
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
}

export interface Settings {
  currency: string
  decimals: number
  thousands: boolean
  theme: 'system' | 'light' | 'dark'
}

export interface State {
  assets: Asset[]
  categories: Category[]
  tags: Category[]
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
    settings: { currency: 'CNY', decimals: 2, thousands: false, theme: 'system' }
  })
}

export function saveState(s: State): void {
  localStorage.setItem(KEY, JSON.stringify(s))
}

export function normalize(s: State): State {
  s.assets ??= []
  s.categories ??= []
  s.tags ??= []
  s.settings = {
    currency: s.settings?.currency ?? 'CNY',
    decimals: s.settings?.decimals ?? 2,
    thousands: s.settings?.thousands ?? false,
    theme: s.settings?.theme ?? 'system'
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

export async function loadBrands(): Promise<BrandBook> {
  const book: BrandBook = { categories: {}, byId: new Map(), aliases: [], versionName: '' }
  try {
    const res = await fetch('./brands.json', { cache: 'no-cache' })
    const json = await res.json()
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
  } catch {
    /* offline: empty book */
  }
  return book
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
interface BackupImage {
  name: string
  data: string
}

export function exportIas(state: State): Uint8Array {
  const data = toBackupData(state)
  return encodeIas({ data, images: [] as BackupImage[] })
}

export function importIas(bytes: Uint8Array): { state: Partial<State>; count: number } {
  const parsed = decodeIas(bytes) as { data: any; images?: Record<string, string> }
  return fromBackupData(parsed.data)
}

function dateFromEpoch(sec: number): string {
  if (!sec) return todayStr()
  const d = new Date(sec * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function epoch(date: string): number {
  if (!date) return 0
  const d = new Date(date + 'T00:00:00')
  return Math.floor(d.getTime() / 1000)
}

function toBackupData(state: State): unknown {
  let sort = 0
  const assets = state.assets.map((a) => ({
    id: a.id,
    name: a.name ?? '',
    model: a.model ?? '',
    icon_name: 'icon_default_object',
    cover_image: '',
    price: a.price ?? 0,
    type: a.kind === 'wish' ? 1 : 0,
    category_id: a.categoryId ?? '',
    state: 0,
    buy_date: epoch(a.buyDate),
    tag_ids: a.tagIds ?? [],
    target_type: 0,
    target_cost_unit: '',
    target_each_increment: 0,
    target_date: epoch(a.targetDate ?? ''),
    target_usage_days: 0,
    target_cost: a.targetCost ?? 0,
    target_value: 0,
    additional_item_ids: [],
    additional_calculation_mode: 0,
    note: a.note ?? '',
    note_images: [],
    is_top: false,
    is_retirement: a.status === 'retired',
    retirement_date: epoch(a.retireDate ?? ''),
    is_sell: a.status === 'sold',
    used_price: a.soldPrice ?? 0,
    secondhand_value: a.secondhandValue ?? 0,
    secondhand_in_cost: a.secondhandInCost ?? true,
    brand_id: a.brandId ?? '',
    brand_name: a.brandName ?? '',
    sell_date: epoch(a.soldDate ?? ''),
    wish_date: epoch(a.targetDate ?? ''),
    is_not_include_total: !!a.excludeTotal,
    is_excluded_from_total: !!a.excludeTotal,
    sort: sort++,
    is_deleted: false,
    created_at: 0,
    update_at: 0
  }))
  return {
    categories: state.categories.map((c, i) => ({
      id: c.id,
      title: c.name,
      sort: i,
      is_deleted: false,
      created_at: 0,
      update_at: 0
    })),
    tags: state.tags.map((t, i) => ({
      id: t.id,
      title: t.name,
      sort: i,
      is_deleted: false,
      created_at: 0,
      update_at: 0
    })),
    assets,
    asset_images: [],
    usages: [],
    income_categories: [],
    income_records: [],
    asset_price_records: [],
    save_money: [],
    expire_reminders: [],
    platform: 'WEB',
    version: '1.0.0',
    exported_at: Math.floor(Date.now() / 1000)
  }
}

function fromBackupData(data: any): { state: Partial<State>; count: number } {
  const categories: Category[] = []
  for (const c of data?.categories ?? []) {
    if (!c || c.is_deleted) continue
    categories.push({ id: String(c.id ?? uid('cat_')), name: String(c.title ?? '') })
  }
  const tags: Category[] = []
  for (const t of data?.tags ?? []) {
    if (!t || t.is_deleted) continue
    tags.push({ id: String(t.id ?? uid('tag_')), name: String(t.title ?? '') })
  }
  const assets: Asset[] = []
  for (const a of data?.assets ?? []) {
    if (!a || a.is_deleted) continue
    const status: Asset['status'] = a.is_sell
      ? 'sold'
      : a.is_retirement
        ? 'retired'
        : 'serving'
    const item: Asset = {
      id: String(a.id ?? uid('a_')),
      name: String(a.name ?? ''),
      model: String(a.model ?? ''),
      price: Number(a.price ?? a.target_cost ?? 0),
      buyDate: dateFromEpoch(Number(a.buy_date ?? 0)),
      kind: Number(a.type ?? 0) === 1 ? 'wish' : 'asset',
      status,
      rule: 'day',
      categoryId: String(a.category_id ?? ''),
      tagIds: Array.isArray(a.tag_ids) ? a.tag_ids.map(String) : [],
      icon: '/icons/default.png',
      note: String(a.note ?? ''),
      soldPrice: Number(a.used_price ?? 0),
      soldDate: a.is_sell ? dateFromEpoch(Number(a.sell_date ?? 0)) : '',
      retireDate: a.is_retirement ? dateFromEpoch(Number(a.retirement_date ?? 0)) : '',
      secondhandValue: Number(a.secondhand_value ?? 0),
      secondhandInCost: a.secondhand_in_cost !== false,
      excludeTotal: !!a.is_not_include_total || !!a.is_excluded_from_total,
      brandId: String(a.brand_id ?? ''),
      brandName: String(a.brand_name ?? ''),
      brandAuto: false,
      targetCost: Number(a.target_cost ?? 0),
      targetDate: a.target_date ? dateFromEpoch(Number(a.target_date)) : ''
    }
    assets.push(item)
  }
  return { state: { assets, categories, tags }, count: assets.length }
}
