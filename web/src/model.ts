import { decodeIas, encodeIas } from './core'

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
  coverData?: string
  noteImagesData?: string[]
  expireAt?: string
  favorite?: boolean
  incomeRecords?: IncomeRecord[]
  priceRecords?: PriceRecord[]
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
  for (const a of s.assets) {
    if (!Array.isArray(a.incomeRecords)) a.incomeRecords = []
    if (!Array.isArray(a.priceRecords)) a.priceRecords = []
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
interface BackupImage {
  name: string
  data: string
}

export function exportIas(state: State): Uint8Array {
  const { data, images } = toBackupData(state)
  return encodeIas({ data, images })
}

export function importIas(bytes: Uint8Array): {
  state: Partial<State>
  count: number
  exportedAt: number
} {
  const parsed = decodeIas(bytes) as { data: any; images?: Record<string, string> }
  const result = fromBackupData(parsed.data, parsed.images ?? {})
  const exportedAt = Number(parsed.data?.exported_at ?? 0)
  return { ...result, exportedAt }
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

function dateFromEpoch(sec: number): string {
  if (!sec) return todayStr()
  const d = new Date(sec * 1000)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 收入/价格记录的日期：兼容 epoch 秒与 `YYYY-MM-DD` 字符串。 */
function recordDate(v: unknown): string {
  const n = Number(v)
  if (Number.isFinite(n) && n > 0) return dateFromEpoch(n)
  const s = typeof v === 'string' ? v : ''
  return s || todayStr()
}

function epoch(date: string): number {
  if (!date) return 0
  const d = new Date(date + 'T00:00:00')
  return Math.floor(d.getTime() / 1000)
}

function toBackupData(state: State): { data: unknown; images: BackupImage[] } {
  const images: BackupImage[] = []
  const imageRecords: any[] = []
  let imgId = 0
  let sort = 0
  const assets = state.assets.map((a) => {
    let coverName = ''
    if (a.coverData) {
      coverName = `images/${a.id}_cover${extOf(a.coverData)}`
      images.push({ name: coverName, data: dataUrlToBase64(a.coverData) })
      imageRecords.push({
        id: `img_${imgId++}`,
        asset_id: a.id,
        image_name: coverName,
        type: 'cover',
        sort: 0,
        is_deleted: false,
        created_at: 0,
        update_at: 0
      })
    }
    const noteNames: string[] = []
    ;(a.noteImagesData ?? []).slice(0, 9).forEach((d, i) => {
      const nm = `images/${a.id}_note${i}${extOf(d)}`
      noteNames.push(nm)
      images.push({ name: nm, data: dataUrlToBase64(d) })
      imageRecords.push({
        id: `img_${imgId++}`,
        asset_id: a.id,
        image_name: nm,
        type: 'note',
        sort: i,
        is_deleted: false,
        created_at: 0,
        update_at: 0
      })
    })
    return {
      id: a.id,
      name: a.name ?? '',
      model: a.model ?? '',
      icon_name: 'icon_default_object',
      cover_image: coverName,
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
      note_images: noteNames,
      is_top: !!a.favorite,
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
    }
  })

  const reminders = state.assets
    .filter((a) => a.expireAt)
    .map((a) => ({
      id: a.id,
      asset_id: a.id,
      expiry_at: epoch(a.expireAt!),
      expiry_remind: 0,
      remind_time_unit: 'day',
      remind_at: 0,
      is_deleted: false,
      created_at: 0,
      update_at: 0
    }))

  return {
    data: {
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
      asset_images: imageRecords,
      usages: [],
      income_categories: state.incomeCategories.map((c, i) => ({
        id: c.id,
        title: c.name,
        icon_name: 'icon_income_1',
        color_hex: '#A3E635',
        sort: i,
        is_deleted: false,
        created_at: 0,
        update_at: 0
      })),
      income_records: state.assets.flatMap((a) =>
        (a.incomeRecords ?? []).map((r) => ({
          id: r.id,
          asset_id: a.id,
          category_id: r.categoryId ?? '',
          amount: r.amount ?? 0,
          income_date: epoch(r.date),
          note: r.note ?? '',
          is_deleted: false,
          created_at: 0,
          update_at: 0
        }))
      ),
      asset_price_records: state.assets.flatMap((a) =>
        (a.priceRecords ?? []).map((r) => ({
          id: r.id,
          asset_id: a.id,
          price: r.price ?? 0,
          record_date: epoch(r.date),
          is_deleted: false,
          created_at: 0,
          update_at: 0
        }))
      ),
      save_money: [],
      expire_reminders: reminders,
      platform: 'WEB',
      version: '1.0.0',
      exported_at: Math.floor(Date.now() / 1000)
    },
    images
  }
}

function fromBackupData(data: any, imageMap: Record<string, string>): { state: Partial<State>; count: number } {
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

  const incomeCategories: Category[] = []
  for (const c of data?.income_categories ?? []) {
    if (!c || c.is_deleted) continue
    const name = String(c.title ?? '')
    if (!name) continue
    incomeCategories.push({ id: String(c.id ?? uid('inc_cat_')), name })
  }

  const incomeByAsset = new Map<string, IncomeRecord[]>()
  for (const r of data?.income_records ?? []) {
    if (!r || r.is_deleted) continue
    const key = String(r.asset_id ?? r.assetId ?? '')
    if (!key) continue
    const list = incomeByAsset.get(key) ?? []
    list.push({
      id: String(r.id ?? uid('inc_')),
      amount: Number(r.amount ?? 0),
      date: recordDate(r.income_date ?? r.date),
      categoryId: String(r.category_id ?? r.categoryId ?? ''),
      note: String(r.note ?? '')
    })
    incomeByAsset.set(key, list)
  }

  const priceByAsset = new Map<string, PriceRecord[]>()
  for (const r of data?.asset_price_records ?? []) {
    if (!r || r.is_deleted) continue
    const key = String(r.asset_id ?? r.assetId ?? '')
    if (!key) continue
    const list = priceByAsset.get(key) ?? []
    list.push({
      id: String(r.id ?? uid('price_')),
      date: recordDate(r.record_date ?? r.date),
      price: Number(r.price ?? 0)
    })
    priceByAsset.set(key, list)
  }

  const imageRecords: any[] = Array.isArray(data?.asset_images) ? data.asset_images : []
  const coverByName = new Map<string, string>()
  const noteByAsset = new Map<string, string[]>()
  for (const r of imageRecords) {
    if (!r || r.is_deleted) continue
    const name = String(r.image_name ?? '')
    const url = dataUrlOf(name, imageMap)
    if (!url) continue
    const type = String(r.type ?? '')
    if (type === 'cover' || type === '0') {
      coverByName.set(String(r.asset_id ?? ''), url)
    } else {
      const key = String(r.asset_id ?? '')
      const list = noteByAsset.get(key) ?? []
      list.push(url)
      noteByAsset.set(key, list)
    }
  }

  const reminders: any[] = Array.isArray(data?.expire_reminders) ? data.expire_reminders : []
  const expireByAsset = new Map<string, string>()
  for (const r of reminders) {
    if (!r || r.is_deleted) continue
    const key = String(r.asset_id ?? r.assetId ?? r.id ?? '')
    if (r.expiry_at) expireByAsset.set(key, dateFromEpoch(Number(r.expiry_at)))
  }

  const assets: Asset[] = []
  for (const a of data?.assets ?? []) {
    if (!a || a.is_deleted) continue
    const status: Asset['status'] = a.is_sell ? 'sold' : a.is_retirement ? 'retired' : 'serving'
    const id = String(a.id ?? uid('a_'))
    const coverName = String(a.cover_image ?? '')
    const noteNames: string[] = Array.isArray(a.note_images) ? a.note_images.map(String) : []
    const item: Asset = {
      id,
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
      favorite: !!a.is_top,
      targetCost: Number(a.target_cost ?? 0),
      targetDate: a.target_date ? dateFromEpoch(Number(a.target_date)) : ''
    }
    const cover = dataUrlOf(coverName, imageMap) ?? coverByName.get(id)
    if (cover) item.coverData = cover
    const notes = noteNames.map((n) => dataUrlOf(n, imageMap)).filter((x): x is string => !!x)
    const extraNotes = noteByAsset.get(id) ?? []
    const allNotes = [...notes, ...extraNotes]
    if (allNotes.length) item.noteImagesData = allNotes.slice(0, 9)
    const expire = expireByAsset.get(id)
    if (expire) item.expireAt = expire
    const incomes = incomeByAsset.get(id)
    if (incomes?.length) item.incomeRecords = incomes
    const prices = priceByAsset.get(id)
    if (prices?.length) item.priceRecords = prices
    assets.push(item)
  }
  return { state: { assets, categories, tags, incomeCategories }, count: assets.length }
}
