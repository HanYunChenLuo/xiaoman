import './style.css'
import {
  ageDays,
  countOfKind,
  coreVersion,
  dailyCost,
  dailyOfKind,
  initCore,
  netCost,
  totalOfKind
} from './core'
import {
  Asset,
  BrandBook,
  IncomeRecord,
  State,
  autoIcon,
  displayName,
  exportIas,
  fetchRemoteBrands,
  fmt,
  importIas,
  incomeTotal,
  latestPrice,
  loadBrands,
  loadState,
  matchBrand,
  mergeIasState,
  money,
  pad,
  saveState,
  statusText,
  sym,
  todayStr,
  uid
} from './model'
import { APP_VERSION, checkDesktopUpdate } from './update'
import {
  accountEmail,
  cloudConnected,
  cloudSupported,
  cloudTokenFresh,
  cloudUnsupportedReason,
  downloadBackup,
  initDesktopCloud,
  isTauri,
  lastRemoteAt,
  lastSyncText,
  remoteInfo,
  signIn,
  signOut,
  uploadBackup
} from './cloud'

type View = 'home' | 'wishes' | 'trends' | 'settings'

const appEl = document.getElementById('app')!

// 图表与提示色（与 style.css 的 --lime/--lime-deep 一致）
const COLOR_LIME = '#c9f44f'
const COLOR_LIME_DEEP = '#b6ee25'
const media = window.matchMedia('(prefers-color-scheme: dark)')

type Props = {
  class?: string
  text?: string
  title?: string
  attrs?: Record<string, string>
  style?: Partial<CSSStyleDeclaration>
  on?: Partial<Record<keyof HTMLElementEventMap, EventListener>>
}

function E<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  props: Props = {},
  ...children: Array<Node | string | null | undefined | false>
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag)
  if (props.class) el.className = props.class
  if (props.text != null) el.textContent = props.text
  if (props.title) el.title = props.title
  if (props.attrs) for (const [k, v] of Object.entries(props.attrs)) el.setAttribute(k, v)
  if (props.style) Object.assign(el.style, props.style)
  if (props.on) for (const [k, fn] of Object.entries(props.on)) el.addEventListener(k, fn as EventListener)
  for (const child of children) {
    if (child == null || child === false) continue
    el.append(typeof child === 'string' ? document.createTextNode(child) : child)
  }
  return el
}

function emptyBook(): BrandBook {
  return { categories: {}, byId: new Map(), aliases: [], versionName: '' }
}

async function fileToDataUrl(file: File, maxSize = 1280): Promise<string> {
  const bitmap = await createImageBitmap(file)
  const scale = Math.min(1, maxSize / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(bitmap.width * scale))
  canvas.height = Math.max(1, Math.round(bitmap.height * scale))
  const ctx = canvas.getContext('2d')
  if (!ctx) return ''
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  return canvas.toDataURL('image/jpeg', 0.82)
}

function fmtTime(ms: number): string {
  if (!ms) return '未知'
  const d = new Date(ms)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function toast(msg: string, duration = 2200): void {
  const el = E('div', { class: 'toast', text: msg })
  document.body.append(el)
  setTimeout(() => el.remove(), duration)
}

class App {
  state: State = loadState()
  brands: BrandBook = emptyBook()
  view: View = 'home'
  filter = 'all'
  categoryFilter = 'all'
  range = 365
  cloudBusy = false

  async boot(): Promise<void> {
    await initCore()
    await initDesktopCloud()
    this.brands = await loadBrands()
    this.applyTheme()
    this.render()
    this.checkExpiryNotifications()
    media.addEventListener('change', () => {
      if (this.state.settings.theme === 'system') this.render()
    })
  }

  dark(): boolean {
    const t = this.state.settings.theme
    return t === 'dark' || (t === 'system' && media.matches)
  }

  applyTheme(): void {
    document.body.classList.toggle('dark', this.dark())
  }

  save(): void {
    this.state.settings.updatedAt = Date.now()
    saveState(this.state)
  }

  money(v: number): string {
    return money(v, this.state.settings)
  }

  fmt(v: number): string {
    return fmt(v, this.state.settings)
  }

  render(): void {
    this.applyTheme()
    appEl.innerHTML = ''
    const wrap = E('div', { class: 'app' })
    wrap.append(this.hero())
    if (this.view === 'home') this.renderHome(wrap)
    else if (this.view === 'wishes') this.renderWishes(wrap)
    else if (this.view === 'trends') this.renderTrends(wrap)
    else this.renderSettings(wrap)
    appEl.append(wrap, this.nav(), this.fab())
  }

  hero(): HTMLElement {
    return E(
      'header',
      { class: 'hero' },
      E(
        'div',
        { class: 'hero-top' },
        E('img', { class: 'logo-img', attrs: { src: this.dark() ? './logo_dark.png' : './logo_light.png', alt: '小满' } }),
        E(
          'div',
          { class: 'hero-actions' },
          E('button', {
            class: 'icon-btn',
            title: '切换主题',
            text: this.dark() ? '☀' : '☾',
            on: {
              click: () => {
                this.state.settings.theme = this.dark() ? 'light' : 'dark'
                this.save()
                this.render()
              }
            }
          })
        )
      )
    )
  }

  nav(): HTMLElement {
    const items: Array<[View, string, string]> = [
      ['home', '小满', '⌂'],
      ['wishes', '心愿', '♡'],
      ['trends', '趋势', '∿'],
      ['settings', '设置', '⚙']
    ]
    return E(
      'nav',
      { class: 'bottom-nav' },
      ...items.map(([v, label, icon]) =>
        E(
          'button',
          {
            class: 'nav-item' + (this.view === v ? ' on' : ''),
            on: {
              click: () => {
                this.view = v
                this.render()
              }
            }
          },
          E('span', { class: 'ico', text: icon }),
          E('span', { text: label })
        )
      )
    )
  }

  fab(): HTMLElement {
    return E('button', {
      class: 'fab',
      text: '+',
      title: '新增',
      on: {
        click: () => {
          const draft: Asset = {
            id: uid('a_'),
            name: '',
            model: '',
            price: 0,
            buyDate: todayStr(),
            kind: this.view === 'wishes' ? 'wish' : 'asset',
            status: 'serving',
            rule: 'day',
            categoryId: '',
            tagIds: [],
            icon: '/icons/default.png',
            secondhandInCost: true
          }
          this.openForm(draft)
        }
      }
    })
  }

  // ---------------- 首页 ----------------
  renderHome(root: HTMLElement): void {
    root.append(this.overview(), this.categoryChips(), this.statusChips())
    const list = this.state.assets.filter((a) => {
      if (a.kind !== 'asset') return false
      if (this.filter !== 'all' && a.status !== this.filter) return false
      if (this.categoryFilter === '__none__') return !a.categoryId
      if (this.categoryFilter !== 'all' && a.categoryId !== this.categoryFilter) return false
      return true
    })
    root.append(this.grid(list))
  }

  renderWishes(root: HTMLElement): void {
    root.append(
      E(
        'div',
        { class: 'page-head' },
        E('h1', { text: '心愿' }),
        E('span', { class: 'muted', text: `${this.state.assets.filter((a) => a.kind === 'wish').length} 个` })
      )
    )
    const card = E(
      'div',
      { class: 'overview', style: { marginTop: 'var(--space-2)' } },
      E('div', { class: 'label muted', text: '心愿总值' }),
      E(
        'div',
        { class: 'value', style: { marginTop: 'var(--space-2)' } },
        E('small', { text: sym(this.state.settings.currency) }),
        this.fmt(totalOfKind(this.state.assets, 'wish'))
      )
    )
    root.append(card)
    root.append(this.grid(this.state.assets.filter((a) => a.kind === 'wish')))
  }

  overview(): HTMLElement {
    const assets = this.state.assets
    const serving = countOfKind(assets, 'asset', 'serving')
    const total = assets.filter((a) => a.kind === 'asset').length
    const statuses: Array<[string, string]> = [
      ['serving', '服役中'],
      ['retired', '已退役'],
      ['sold', '已卖出']
    ]
    const denom = Math.max(1, total)
    return E(
      'section',
      { class: 'overview' },
      E(
        'div',
        { class: 'overview-head' },
        E('h2', { text: '资产总览' }),
        E('span', { class: 'muted', text: `${serving}/${total}` })
      ),
      E(
        'div',
        { class: 'overview-grid' },
        E(
          'div',
          {},
          E('div', { class: 'label', text: '总资产' }),
          E(
            'div',
            { class: 'value' },
            E('small', { text: sym(this.state.settings.currency) }),
            this.fmt(totalOfKind(assets, 'asset'))
          )
        ),
        E(
          'div',
          { style: { textAlign: 'right' } },
          E('div', { class: 'label', text: '日均成本' }),
          E(
            'div',
            { class: 'value' },
            E('small', { text: sym(this.state.settings.currency) }),
            this.fmt(dailyOfKind(assets, 'asset'))
          )
        )
      ),
      E(
        'div',
        { class: 'status-bars' },
        ...statuses.map(([key, label]) => {
          const n = countOfKind(assets, 'asset', key)
          return E(
            'div',
            { class: 'status-bar' },
            E('div', { class: 'cap' }, E('span', { text: label }), E('span', { text: String(n) })),
            E('div', { class: 'track' }, E('div', { class: 'fill', style: { width: `${(n / denom) * 100}%` } }))
          )
        })
      )
    )
  }

  categoryChips(): HTMLElement {
    const cats = this.state.categories
    return E(
      'div',
      { class: 'chip-row' },
      ...['全部', ...cats.map((c) => c.name), '未分类'].map((label, i) => {
        const key = i === 0 ? 'all' : i === cats.length + 1 ? '__none__' : cats[i - 1].id
        return E('button', {
          class: 'chip' + (this.categoryFilter === key ? ' on' : ''),
          text: label,
          on: {
            click: () => {
              this.categoryFilter = key
              this.render()
            }
          }
        })
      })
    )
  }

  statusChips(): HTMLElement {
    const defs: Array<[string, string]> = [
      ['all', '全部'],
      ['serving', '服役中'],
      ['retired', '已退役'],
      ['sold', '已卖出']
    ]
    return E(
      'div',
      { class: 'filters' },
      ...defs.map(([key, label]) =>
        E('button', {
          class: 'chip' + (this.filter === key ? ' on' : ''),
          text: label,
          on: {
            click: () => {
              this.filter = key
              this.render()
            }
          }
        })
      )
    )
  }

  grid(list: Asset[]): HTMLElement {
    if (!list.length) return E('div', { class: 'empty' }, E('div', { text: '空空如也' }), E('div', { text: '点击 + 添加资产' }))
    return E('div', { class: 'grid' }, ...list.map((a) => this.card(a)))
  }

  card(a: Asset): HTMLElement {
    const model = a.model || a.name
    return E(
      'article',
      {
        class: 'card',
        on: {
          click: () => this.openDetail(a)
        }
      },
      E('img', { class: 'icon', attrs: { src: a.coverData || a.icon || autoIcon(model), alt: '' } }),
      E('span', { class: 'badge ' + a.status, text: statusText(a.status) }),
      E('h3', { text: displayName(a) }),
      E('div', { class: 'sub', text: `${a.brandName ? a.brandName + ' ｜ ' : ''}${this.money(a.price)} ｜ ${ageDays(a, todayStr())} 天` }),
      E(
        'div',
        { class: 'cost' },
        this.money(dailyCost(a)),
        E('small', { text: '/天' })
      )
    )
  }

  // ---------------- 趋势 ----------------
  renderTrends(root: HTMLElement): void {
    root.append(
      E('div', { class: 'page-head' }, E('h1', { text: '趋势' }), E('span', { class: 'pill', text: '统计' })),
      E(
        'div',
        { class: 'chip-row', style: { paddingTop: 'var(--space-1)' } },
        ...([
          [7, '最近一周'],
          [90, '最近90天'],
          [180, '最近180天'],
          [365, '最近一年']
        ] as Array<[number, string]>).map(([days, label]) =>
          E('button', {
            class: 'chip soft' + (this.range === days ? ' on' : ''),
            text: label,
            on: {
              click: () => {
                this.range = days
                this.render()
              }
            }
          })
        )
      )
    )
    const assets = this.state.assets.filter((a) => a.kind === 'asset')
    root.append(
      E(
        'div',
        { class: 'overview', style: { marginTop: 'var(--space-2)' } },
        E('div', { class: 'label muted', text: '资产总值' }),
        E(
          'div',
          { class: 'value' },
          E('small', { text: sym(this.state.settings.currency) }),
          this.fmt(totalOfKind(this.state.assets, 'asset'))
        ),
        E('div', { class: 'muted', style: { marginTop: 'var(--space-2)' } }, `日均成本 ${this.money(dailyOfKind(this.state.assets, 'asset'))}`)
      )
    )
    root.append(E('div', { class: 'chart-wrap' }, this.chart(assets, this.range)))
    root.append(E('div', { class: 'section' }, E('h2', { text: '类型分布' }), this.distribution(assets)))
  }

  chart(assets: Asset[], days: number): SVGSVGElement {
    const values: number[] = []
    const start = new Date()
    start.setHours(0, 0, 0, 0)
    start.setDate(start.getDate() - (days - 1))
    for (let i = 0; i < days; i++) {
      const d = new Date(start)
      d.setDate(start.getDate() + i)
      const key = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
      let v = 0
      for (const a of assets) {
        if (a.buyDate <= key && (!a.soldDate || a.soldDate >= key)) v += a.price
      }
      values.push(v)
    }
    const max = Math.max(100, ...values)
    const w = 800
    const h = 200
    const padL = 6
    const padR = 54
    const padT = 10
    const padB = 22
    const cw = w - padL - padR
    const ch = h - padT - padB
    const pts = values.map((v, i) => {
      const x = padL + (values.length === 1 ? cw : (cw * i) / (values.length - 1))
      const y = padT + ch - (v / max) * ch
      return [x, y] as [number, number]
    })
    const path = pts.map(([x, y], i) => `${i === 0 ? 'M' : 'L'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ')
    const area = `${path} L${pts[pts.length - 1][0].toFixed(1)},${padT + ch} L${pts[0][0].toFixed(1)},${padT + ch} Z`
    const end = new Date()
    const labelL = this.shortDate(start)
    const labelR = '今天'
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
    svg.setAttribute('viewBox', `0 0 ${w} ${h}`)
    svg.setAttribute('class', 'chart')
    const grid = document.createElementNS(svg.namespaceURI, 'g')
    for (const r of [1 / 3, 2 / 3]) {
      const line = document.createElementNS(svg.namespaceURI, 'line')
      const y = padT + ch * r
      line.setAttribute('x1', String(padL))
      line.setAttribute('x2', String(padL + cw))
      line.setAttribute('y1', String(y))
      line.setAttribute('y2', String(y))
      line.setAttribute('stroke', 'currentColor')
      line.setAttribute('stroke-opacity', '0.15')
      line.setAttribute('stroke-dasharray', '4 5')
      grid.append(line)
    }
    const fill = document.createElementNS(svg.namespaceURI, 'path')
    fill.setAttribute('d', area)
    fill.setAttribute('fill', COLOR_LIME)
    fill.setAttribute('fill-opacity', '0.25')
    const stroke = document.createElementNS(svg.namespaceURI, 'path')
    stroke.setAttribute('d', path)
    stroke.setAttribute('fill', 'none')
    stroke.setAttribute('stroke', COLOR_LIME_DEEP)
    stroke.setAttribute('stroke-width', '2.5')
    stroke.setAttribute('stroke-linejoin', 'round')
    const label = (x: number, y: number, text: string, anchor = 'end') => {
      const t = document.createElementNS(svg.namespaceURI, 'text')
      t.setAttribute('x', String(x))
      t.setAttribute('y', String(y))
      t.setAttribute('font-size', '11')
      t.setAttribute('fill', 'currentColor')
      t.setAttribute('fill-opacity', '0.55')
      t.setAttribute('text-anchor', anchor)
      t.textContent = text
      return t
    }
    svg.append(
      grid,
      fill,
      stroke,
      label(w - 4, padT + 10, this.fmt(max)),
      label(w - 4, padT + ch / 2 + 4, this.fmt(max / 2)),
      label(w - 4, padT + ch, '0'),
      label(padL, h - 4, labelL, 'start'),
      label(padL + cw, h - 4, labelR)
    )
    return svg
  }

  shortDate(d: Date): string {
    const cross = d.getFullYear() !== new Date().getFullYear()
    return cross ? `${d.getFullYear()}.${pad(d.getMonth() + 1)}.${pad(d.getDate())}` : `${pad(d.getMonth() + 1)}.${pad(d.getDate())}`
  }

  distribution(assets: Asset[]): HTMLElement {
    const map = new Map<string, number>()
    for (const a of assets) {
      const cat = this.state.categories.find((c) => c.id === a.categoryId)?.name ?? '未分类'
      map.set(cat, (map.get(cat) ?? 0) + 1)
    }
    const total = Math.max(1, assets.length)
    const box = E('div', { class: 'group', style: { padding: '4px 16px 12px' } })
    if (!map.size) box.append(E('div', { class: 'empty', text: '暂无数据' }))
    for (const [name, n] of [...map.entries()].sort((a, b) => b[1] - a[1])) {
      box.append(
        E(
          'div',
          { class: 'dist-row' },
          E(
            'div',
            { class: 'dist-item' },
            E('div', { style: { display: 'flex', justifyContent: 'space-between' } }, E('span', { text: name }), E('span', { class: 'num', text: `${n} 件` })),
            E('div', { class: 'bar' }, E('i', { style: { width: `${(n / total) * 100}%` } }))
          )
        )
      )
    }
    return box
  }

  // ---------------- 设置 ----------------
  renderSettings(root: HTMLElement): void {
    root.append(E('div', { class: 'page-head' }, E('h1', { text: '设置' })))
    const s = this.state.settings
    root.append(
      E('div', { class: 'section' }, E('h2', { text: '数值与单位' }), this.group([
        this.rowSelect('货币单位', ['CNY', 'USD', 'EUR', 'JPY', 'GBP', 'HKD'], s.currency, (v) => {
          s.currency = v
          this.save()
          this.render()
        }),
        this.rowSelect('小数点设置', ['0', '1', '2', '3'], String(s.decimals), (v) => {
          s.decimals = Number(v)
          this.save()
          this.render()
        }),
        this.rowCheck('使用千位分隔符', s.thousands, (v) => {
          s.thousands = v
          this.save()
          this.render()
        }),
        this.rowSelect('主题模式', [['system', '跟随系统'], ['light', '浅色'], ['dark', '深色']], s.theme, (v) => {
          s.theme = v as State['settings']['theme']
          this.save()
          this.render()
        })
      ]))
    )
    root.append(
      E('div', { class: 'section' }, E('h2', { text: '数据管理' }), this.group([
        this.linkRow('分类管理', `${this.state.categories.length} 个`, () => this.manageList('categories')),
        this.linkRow('标签管理', `${this.state.tags.length} 个`, () => this.manageList('tags')),
        this.linkRow('收入分类', `${this.state.incomeCategories.length} 个`, () => this.manageList('incomeCategories')),
        this.linkRow('品牌与型号数据', `v${this.brands.versionName || '内置'}`, () => this.checkBrands()),
        this.linkRow('导出 .ias 备份', '下载', () => this.exportBackup()),
        this.linkRow('导入 .ias 备份', '选择文件', () => this.importBackup())
      ]))
    )
    root.append(
      E('div', { class: 'section' }, E('h2', { text: '数据同步' }), this.group(this.cloudRows()))
    )
    root.append(
      E('div', { class: 'section' }, E('h2', { text: '提醒' }), this.group([
        this.rowCheck('到期提醒（浏览器通知）', !!s.notifyExpiry, async (v) => {
          if (v) {
            if (!('Notification' in window)) {
              toast('当前环境不支持通知')
              return
            }
            const perm = await Notification.requestPermission()
            if (perm !== 'granted') {
              toast('未授予通知权限')
              return
            }
          }
          s.notifyExpiry = v
          this.save()
          this.render()
        })
      ]))
    )
    root.append(
      E('div', { class: 'section' }, E('h2', { text: '关于' }), this.group([
        this.linkRow('项目主页', 'GitHub', () => window.open('https://github.com/HanYunChenLuo/xiaoman', '_blank')),
        ...(isTauri() ? [this.linkRow('检查更新', `v${APP_VERSION}`, () => this.checkUpdate())] : []),
        this.linkRow('版本', APP_VERSION, null),
        this.linkRow('核心版本', coreVersion(), null)
      ]), E('p', {
        class: 'muted',
        text: '数据默认保存在本机（浏览器），可选择备份到云端',
        style: { margin: 'var(--space-4) var(--space-1) 0', fontSize: 'var(--font-sm2)' }
      }))
    )
    const wipe = E('button', {
      class: 'btn danger',
      text: '抹掉所有内容',
      style: { margin: '18px 18px 0', width: 'calc(100% - 36px)' },
      on: {
        click: async () => {
          if (await this.askConfirm('确定抹掉全部本地数据？此操作无法恢复。')) {
            localStorage.removeItem('xiaoman_web_v1')
            this.state = loadState()
            this.render()
          }
        }
      }
    })
    root.append(wipe)
  }

  group(children: HTMLElement[]): HTMLElement {
    return E('div', { class: 'group' }, ...children)
  }

  linkRow(label: string, value: string | null, action: (() => void) | null): HTMLElement {
    return E(
      'div',
      {
        class: 'row' + (action ? ' clickable' : ''),
        on: action ? { click: action } : undefined
      },
      E('span', { class: 'grow', text: label }),
      value != null ? E('span', { class: 'val', text: value }) : null,
      action ? E('span', { class: 'chev', text: '›' }) : null
    )
  }

  rowSelect(label: string, options: Array<string | [string, string]>, value: string, onChange: (v: string) => void): HTMLElement {
    const select = E('select', {
      on: {
        change: () => onChange(select.value)
      }
    }) as HTMLSelectElement
    for (const o of options) {
      const val = Array.isArray(o) ? o[0] : o
      const text = Array.isArray(o) ? o[1] : o
      const opt = E('option', { text }) as HTMLOptionElement
      opt.value = val
      select.append(opt)
    }
    select.value = value
    return E('div', { class: 'row' }, E('span', { class: 'grow', text: label }), select)
  }

  rowCheck(label: string, checked: boolean, onChange: (v: boolean) => void): HTMLElement {
    const input = E('input', { attrs: { type: 'checkbox' } }) as HTMLInputElement
    input.checked = checked
    input.addEventListener('change', () => onChange(input.checked))
    return E('label', { class: 'row' }, E('span', { class: 'grow', text: label }), E('span', { class: 'toggle' }, input))
  }

  // ---------------- 应用更新（桌面端） ----------------
  async checkUpdate(): Promise<void> {
    toast('正在检查更新…')
    try {
      const update = await checkDesktopUpdate()
      if (!update) {
        toast(`已是最新版本 v${APP_VERSION}`)
        return
      }
      const notes = update.notes.trim()
      const ok = await this.askConfirm(
        `${notes ? notes.slice(0, 600) + '\n\n' : ''}发现新版本 ${update.version}，是否下载并安装？`,
        '检查更新'
      )
      if (!ok) return
      toast('正在下载更新… 0%', 6000)
      await update.install((pct) => toast(`正在下载更新… ${pct}%`, 1200))
    } catch (e) {
      toast('检查更新失败：' + ((e as Error)?.message ?? String(e)), 6000)
    }
  }

  checkBrands(): void {
    toast('正在检查品牌数据…')
    fetchRemoteBrands()
      .then(async (ver) => {
        this.brands = await loadBrands()
        toast(ver ? '品牌数据已更新至 ' + ver : '品牌数据已更新')
        this.render()
      })
      .catch((e) => toast('检查更新失败：' + (e as Error).message))
  }

  // ---------------- 云同步 ----------------
  cloudRows(): HTMLElement[] {
    if (!cloudSupported()) {
      return [this.linkRow('Google 云备份', cloudUnsupportedReason(), null)]
    }
    const rows: HTMLElement[] = []
    if (!cloudConnected()) {
      rows.push(this.linkRow('Google 账号', '点击登录', () => this.cloudSignIn()))
      rows.push(this.linkRow('云端备份', '登录后可上传 / 恢复', null))
      return rows
    }
    if (!cloudTokenFresh()) {
      rows.push(this.linkRow('Google 账号', '登录已过期，点击重新授权', () => this.cloudSignIn()))
      rows.push(this.linkRow('云端备份', '重新授权后可用', null))
      return rows
    }
    rows.push(
      this.linkRow('Google 账号', accountEmail(), () => this.cloudAccountModal()),
      this.linkRow('上传备份到云端', lastSyncText(), () => this.cloudUpload()),
      this.linkRow('从云端恢复', '下载', () => this.cloudRestore())
    )
    return rows
  }

  async cloudSignIn(): Promise<void> {
    if (this.cloudBusy) return
    this.cloudBusy = true
    toast(isTauri() ? '已打开系统浏览器，请在网页中完成 Google 授权…' : '正在打开 Google 登录…', 6000)
    try {
      const acc = await signIn()
      toast('已登录 ' + (acc.email || 'Google 账号'))
      this.render()
    } catch (e) {
      toast((e as Error).message, 6000)
    } finally {
      this.cloudBusy = false
    }
  }

  async cloudUpload(skipCheck = false): Promise<void> {
    if (this.cloudBusy) return
    this.cloudBusy = true
    try {
      if (!skipCheck) {
        toast('正在检查云端备份…')
        const info = await remoteInfo()
        const remoteMs = info?.modifiedTime ? new Date(info.modifiedTime).getTime() : 0
        const localMs = this.state.settings.updatedAt ?? 0
        // 云端有别人更新过（晚于本地最后修改、也晚于我们上次见到的时间）时提醒。
        if (remoteMs > 0 && remoteMs > localMs && remoteMs > lastRemoteAt()) {
          this.cloudBusy = false
          const choice = await this.askChoice(
            '云端备份较新',
            `云端：最后修改 ${fmtTime(remoteMs)}\n本地：最后修改 ${fmtTime(localMs)}\n\n上传会用本地数据覆盖云端备份。`,
            ['取消', '仍然上传', '先恢复云端']
          )
          if (choice === 1) await this.cloudUpload(true)
          else if (choice === 2) await this.cloudRestore()
          return
        }
      }
      toast('正在上传备份…')
      const bytes = exportIas(this.state)
      await uploadBackup(bytes)
      toast('已上传到云端')
    } catch (e) {
      toast('上传失败：' + (e as Error).message)
    } finally {
      this.cloudBusy = false
      this.render()
    }
  }

  async cloudRestore(): Promise<void> {
    if (this.cloudBusy) return
    this.cloudBusy = true
    toast('正在读取云端备份…')
    try {
      const got = await downloadBackup()
      if (!got) {
        toast('云端暂无备份')
        return
      }
      const when = got.file.modifiedTime ? new Date(got.file.modifiedTime).toLocaleString('zh-CN') : ''
      await this.applyBackup(got.bytes, when ? `云端备份（${when}）` : '云端备份')
    } catch (e) {
      toast('恢复失败：' + (e as Error).message)
    } finally {
      this.cloudBusy = false
      this.render()
    }
  }

  cloudAccountModal(): void {
    const content = E('div', {})
    const rows = E('div', { class: 'group' })
    const addRow = (k: string, v: string) =>
      rows.append(E('div', { class: 'row' }, E('span', { class: 'grow', text: k }), E('span', { class: 'val', text: v })))
    addRow('账号', accountEmail())
    if (isTauri()) addRow('登录方式', '系统浏览器 · loopback')
    addRow('上次上传', lastSyncText())
    const remote = E('span', { class: 'val', text: '查询中…' })
    rows.append(E('div', { class: 'row' }, E('span', { class: 'grow', text: '云端文件' }), remote))
    content.append(
      E('button', { class: 'close', text: '✕', on: { click: () => this.closeModal() } }),
      E('h2', { text: 'Google 账号' }),
      rows,
      E(
        'div',
        { class: 'form-actions' },
        E('button', {
          class: 'btn danger',
          text: '退出登录',
          on: {
            click: async () => {
              await signOut()
              this.closeModal()
              this.render()
              toast('已退出 Google')
            }
          }
        }),
        E('button', { class: 'btn', text: '关闭', on: { click: () => this.closeModal() } })
      )
    )
    this.openModal(content)
    remoteInfo()
      .then((info) => {
        remote.textContent = info
          ? `${info.modifiedTime ? new Date(info.modifiedTime).toLocaleString('zh-CN') : '已上传'}${
              info.size ? ` · ${(Number(info.size) / 1024).toFixed(1)} KB` : ''
            }`
          : '尚无备份'
      })
      .catch((e) => {
        remote.textContent = '读取失败：' + (e as Error).message
      })
  }

  checkExpiryNotifications(): void {
    if (!this.state.settings.notifyExpiry) return
    if (!('Notification' in window) || Notification.permission !== 'granted') return
    const today = todayStr()
    const soon = new Date()
    soon.setDate(soon.getDate() + 7)
    const limit = `${soon.getFullYear()}-${pad(soon.getMonth() + 1)}-${pad(soon.getDate())}`
    const items = this.state.assets.filter((a) => a.expireAt && a.expireAt <= limit)
    if (!items.length) return
    const key = 'xiaoman_noticed_' + today
    if (localStorage.getItem(key)) return
    localStorage.setItem(key, '1')
    try {
      new Notification('小满 · 到期提醒', {
        body: items
          .slice(0, 3)
          .map((a) => `${displayName(a)} 将于 ${a.expireAt} 到期`)
          .join('\n')
      })
    } catch {
      /* ignore */
    }
  }

  manageList(kind: 'categories' | 'tags' | 'incomeCategories'): void {
    const list = this.state[kind]
    const title = kind === 'categories' ? '分类管理' : kind === 'tags' ? '标签管理' : '收入分类管理'
    const content = E('div', {})
    content.append(E('h2', { text: title }))
    const rows = E('div', { class: 'group' })
    const move = (index: number, delta: number) => {
      const target = index + delta
      if (target < 0 || target >= list.length) return
      const arr = this.state[kind]
      const tmp = arr[index]
      arr[index] = arr[target]
      arr[target] = tmp
      this.save()
      this.closeModal()
      this.manageList(kind)
      this.render()
    }
    list.forEach((item, index) => {
      rows.append(
        E(
          'div',
          { class: 'row' },
          E('span', { class: 'grow', text: item.name }),
          E('button', {
            class: 'tag-chip',
            text: '↑',
            on: { click: () => move(index, -1) }
          }),
          E('button', {
            class: 'tag-chip',
            text: '↓',
            on: { click: () => move(index, 1) }
          }),
          E('button', {
            class: 'tag-chip',
            text: '重命名',
            on: {
              click: async () => {
                const name = await this.askText('重命名', item.name)
                if (!name?.trim()) return
                item.name = name.trim()
                this.save()
                this.closeModal()
                this.manageList(kind)
                this.render()
              }
            }
          }),
          E('button', {
            class: 'btn danger',
            text: '删除',
            on: {
              click: async () => {
                if (!(await this.askConfirm(`删除「${item.name}」？`))) return
                this.state[kind] = list.filter((x) => x.id !== item.id)
                if (kind === 'categories') {
                  for (const a of this.state.assets) if (a.categoryId === item.id) a.categoryId = ''
                } else if (kind === 'tags') {
                  for (const a of this.state.assets) a.tagIds = (a.tagIds ?? []).filter((t) => t !== item.id)
                } else {
                  for (const a of this.state.assets) {
                    for (const r of a.incomeRecords ?? []) if (r.categoryId === item.id) r.categoryId = ''
                  }
                }
                this.save()
                this.closeModal()
                this.manageList(kind)
                this.render()
              }
            }
          })
        )
      )
    })
    if (!list.length) rows.append(E('div', { class: 'empty', text: '暂无内容' }))
    content.append(rows)
    content.append(
      E(
        'div',
        { class: 'form-actions' },
        E('button', {
          class: 'btn lime',
          text: '新建',
          on: {
            click: async () => {
              const name = await this.askText(
                kind === 'categories' ? '分类名称' : kind === 'tags' ? '标签名称' : '收入分类名称'
              )
              if (!name?.trim()) return
              const prefix = kind === 'categories' ? 'cat_' : kind === 'tags' ? 'tag_' : 'inc_cat_'
              this.state[kind].push({ id: uid(prefix), name: name.trim() })
              this.save()
              this.closeModal()
              this.manageList(kind)
              this.render()
            }
          }
        }),
        E('button', { class: 'btn', text: '关闭', on: { click: () => this.closeModal() } })
      )
    )
    this.openModal(content)
  }

  // ---------------- 详情 ----------------
  openDetail(a: Asset): void {
    const rows: Array<[string, string]> = [
      ['单价', this.money(a.price)],
      ['类别', this.state.categories.find((c) => c.id === a.categoryId)?.name ?? '未分类']
    ]
    if (a.brandName) rows.push(['品牌', a.brandName])
    if (a.model) rows.push(['型号', a.model])
    rows.push(['购买日期', a.buyDate])
    if ((a.secondhandValue ?? 0) > 0 && a.status !== 'sold') {
      rows.push(['二手估值', this.money(a.secondhandValue!)])
      rows.push(['持有成本', `${this.money(netCost(a))} ｜ ${this.money(dailyCost(a))}/天`])
    }
    if (a.status !== 'serving') rows.push(['状态', statusText(a.status)])
    if (a.status === 'retired' && a.retireDate) rows.push(['退役日期', a.retireDate])
    if (a.status === 'sold') {
      if ((a.soldPrice ?? 0) > 0) rows.push(['卖出价格', this.money(a.soldPrice!)])
      if (a.soldDate) rows.push(['卖出日期', a.soldDate])
    }
    if (a.kind === 'wish') {
      if ((a.targetCost ?? 0) > 0) rows.push(['目标价格', this.money(a.targetCost!)])
      if (a.targetDate) rows.push(['目标日期', a.targetDate])
    }
    if (a.expireAt) rows.push(['到期时间', a.expireAt])
    const income = incomeTotal(a)
    if (income > 0) rows.push(['累计收入', this.money(income)])
    const lastPrice = latestPrice(a)
    if (lastPrice && lastPrice.price > 0) rows.push(['最近价格', `${lastPrice.date} ｜ ${this.money(lastPrice.price)}`])
    if (a.note) rows.push(['备注', a.note])
    if (a.excludeTotal) rows.push(['计入总资产', '否'])

    const content = E('div', {})
    content.append(
      E('button', { class: 'close', text: '✕', on: { click: () => this.closeModal() } }),
      E(
        'div',
        { class: 'detail-head' },
        E('img', { attrs: { src: a.coverData || a.icon || autoIcon(a.model || a.name), alt: '' } }),
        E('h2', { text: displayName(a) }),
        E('div', { class: 'detail-cost', text: `${this.money(dailyCost(a))}/天` }),
        E('div', { class: 'meta', text: `总价：${this.money(a.price)}  |  已服役 ${ageDays(a, todayStr())} 天` })
      ),
      E(
        'div',
        { class: 'detail-rows' },
        ...rows.map(([k, v]) =>
          E('div', { class: 'detail-row' }, E('span', { class: 'k', text: k }), E('span', { text: v }))
        )
      ),
      ...(a.noteImagesData?.length
        ? [
            E(
              'div',
              { class: 'tag-picker', style: { marginTop: 'var(--space-5)' } },
              ...a.noteImagesData.map((d) => E('img', { class: 'thumb', attrs: { src: d, alt: '' } }))
            )
          ]
        : []),
      this.priceBlock(a),
      this.incomeBlock(a),
      E(
        'div',
        { class: 'form-actions' },
        E('button', {
          class: 'btn',
          text: a.favorite ? '取消收藏' : '收藏',
          on: {
            click: () => {
              a.favorite = !a.favorite
              this.save()
              this.refreshDetail(a)
            }
          }
        }),
        E('button', {
          class: 'btn danger',
          text: '删除',
          on: {
            click: async () => {
              if (!(await this.askConfirm(`删除「${displayName(a)}」？`))) return
              this.state.assets = this.state.assets.filter((x) => x.id !== a.id)
              this.save()
              this.closeModal()
              this.render()
            }
          }
        }),
        E('button', {
          class: 'btn primary',
          text: '编辑',
          on: {
            click: () => {
              this.closeModal()
              this.openForm(structuredClone(a))
            }
          }
        })
      )
    )
    this.openModal(content)
  }

  // ---------------- 收入 / 价格记录 ----------------
  refreshDetail(a: Asset): void {
    this.closeModal()
    this.openDetail(a)
  }

  priceBlock(a: Asset): HTMLElement {
    const block = E('div', { class: 'record-block' }, E('div', { class: 'record-title', text: '价格记录' }))
    const list = E('div', { class: 'group' })
    const items = [...(a.priceRecords ?? [])].sort((x, y) => (x.date < y.date ? 1 : x.date > y.date ? -1 : 0))
    if (!items.length) list.append(E('div', { class: 'empty', text: '暂无价格记录' }))
    for (const r of items) {
      list.append(
        E(
          'div',
          { class: 'row' },
          E('span', { class: 'grow', text: r.date }),
          E('span', { class: 'val', text: this.money(r.price) }),
          E('button', {
            class: 'tag-chip',
            text: '删除',
            on: {
              click: () => {
                a.priceRecords = (a.priceRecords ?? []).filter((x) => x.id !== r.id)
                this.save()
                this.refreshDetail(a)
              }
            }
          })
        )
      )
    }
    const date = E('input', { attrs: { type: 'date' } }) as HTMLInputElement
    date.value = todayStr()
    const price = E('input', { attrs: { type: 'number', step: '0.01', min: '0', placeholder: '价格' } }) as HTMLInputElement
    const add = E('button', {
      class: 'btn lime',
      text: '添加',
      on: {
        click: () => {
          const v = Number(price.value)
          if (!(v > 0)) {
            toast('请输入有效价格')
            return
          }
          a.priceRecords = [...(a.priceRecords ?? []), { id: uid('price_'), date: date.value || todayStr(), price: v }]
          this.save()
          this.refreshDetail(a)
        }
      }
    })
    block.append(list, E('div', { class: 'record-form' }, date, price, add))
    return block
  }

  incomeBlock(a: Asset): HTMLElement {
    const block = E('div', { class: 'record-block' }, E('div', { class: 'record-title', text: '收入记录' }))
    const list = E('div', { class: 'group' })
    const items = [...(a.incomeRecords ?? [])].sort((x, y) => (x.date < y.date ? 1 : x.date > y.date ? -1 : 0))
    if (!items.length) list.append(E('div', { class: 'empty', text: '暂无收入记录' }))
    for (const r of items) {
      const cat = this.state.incomeCategories.find((c) => c.id === r.categoryId)?.name ?? '未分类'
      list.append(
        E(
          'div',
          { class: 'row' },
          E('span', { class: 'grow', text: `${r.date} ｜ ${cat}${r.note ? ' ｜ ' + r.note : ''}` }),
          E('span', { class: 'val', text: this.money(r.amount) }),
          E('button', {
            class: 'tag-chip',
            text: '删除',
            on: {
              click: () => {
                a.incomeRecords = (a.incomeRecords ?? []).filter((x) => x.id !== r.id)
                this.save()
                this.refreshDetail(a)
              }
            }
          })
        )
      )
    }
    const date = E('input', { attrs: { type: 'date' } }) as HTMLInputElement
    date.value = todayStr()
    const amount = E('input', { attrs: { type: 'number', step: '0.01', min: '0', placeholder: '金额' } }) as HTMLInputElement
    const catSelect = E('select') as HTMLSelectElement
    catSelect.append(E('option', { text: '未分类' }) as HTMLOptionElement)
    for (const c of this.state.incomeCategories) {
      const o = E('option', { text: c.name }) as HTMLOptionElement
      o.value = c.id
      catSelect.append(o)
    }
    const note = E('input', { attrs: { type: 'text', placeholder: '备注（可选）', maxlength: '30' } }) as HTMLInputElement
    const add = E('button', {
      class: 'btn lime',
      text: '添加',
      on: {
        click: () => {
          const v = Number(amount.value)
          if (!(v > 0)) {
            toast('请输入有效金额')
            return
          }
          const rec: IncomeRecord = {
            id: uid('inc_'),
            amount: v,
            date: date.value || todayStr(),
            categoryId: catSelect.value,
            note: note.value.trim()
          }
          a.incomeRecords = [...(a.incomeRecords ?? []), rec]
          this.save()
          this.refreshDetail(a)
        }
      }
    })
    block.append(list, E('div', { class: 'record-form' }, date, amount, catSelect, note, add))
    return block
  }

  // ---------------- 表单 ----------------
  openForm(draft: Asset): void {
    const content = E('div', {})
    content.append(
      E('button', { class: 'close', text: '✕', on: { click: () => this.closeModal() } }),
      E('h2', { text: this.state.assets.some((a) => a.id === draft.id) ? '编辑资产' : '新增资产' })
    )

    const nameInput = E('input', { attrs: { type: 'text', placeholder: '名称 / 昵称' } }) as HTMLInputElement
    nameInput.value = draft.name ?? ''
    const modelInput = E('input', { attrs: { type: 'text', placeholder: '具体型号' } }) as HTMLInputElement
    modelInput.value = draft.model ?? ''
    const priceInput = E('input', { attrs: { type: 'number', step: '0.01', min: '0', placeholder: '0.00' } }) as HTMLInputElement
    priceInput.value = draft.price ? String(draft.price) : ''
    const secondInput = E('input', { attrs: { type: 'number', step: '0.01', min: '0', placeholder: '可选' } }) as HTMLInputElement
    secondInput.value = draft.secondhandValue ? String(draft.secondhandValue) : ''
    const todayInput = E('input', { attrs: { type: 'date' } }) as HTMLInputElement
    todayInput.value = draft.buyDate || todayStr()

    const brandSelect = E('select') as HTMLSelectElement
    brandSelect.append(E('option', { text: '无品牌' }) as HTMLOptionElement)
    const catNames: Record<string, string> = { digital: '数码', car: '汽车', bike: '自行车' }
    for (const [cat, list] of Object.entries(this.brands.categories)) {
      const group = E('optgroup', { attrs: { label: catNames[cat] ?? cat } }) as HTMLOptGroupElement
      for (const b of list) {
        const opt = E('option', { text: b.name }) as HTMLOptionElement
        opt.value = b.id
        group.append(opt)
      }
      brandSelect.append(group)
    }
    brandSelect.value = draft.brandId || ''

    const modelSelect = E('select') as HTMLSelectElement
    const modelCustom = E('input', { attrs: { type: 'text', placeholder: '自定义型号' } }) as HTMLInputElement
    modelCustom.value = draft.model ?? ''

    const preview = E('img', {
      class: 'icon icon-preview',
      attrs: { src: draft.icon || autoIcon(draft.model || draft.name), alt: '' }
    })

    const refreshModels = () => {
      modelSelect.innerHTML = ''
      const brand = this.brands.byId.get(brandSelect.value)
      modelSelect.append(E('option', { text: '选择型号' }) as HTMLOptionElement)
      if (brand) {
        for (const [groupName, models] of brand.groups) {
          const group = E('optgroup', { attrs: { label: groupName } }) as HTMLOptGroupElement
          for (const m of models) {
            const opt = E('option', { text: m }) as HTMLOptionElement
            opt.value = m
            group.append(opt)
          }
          modelSelect.append(group)
        }
      }
      if (draft.model && [...modelSelect.options].some((o) => o.value === draft.model)) modelSelect.value = draft.model
    }
    refreshModels()

    const syncIcon = () => {
      const auto = autoIcon(modelInput.value || nameInput.value)
      preview.setAttribute('src', auto)
      draft.icon = auto
    }

    const autoBrand = () => {
      if (draft.brandAuto === false) return
      const m = matchBrand(modelInput.value || nameInput.value, this.brands)
      if (m) {
        draft.brandId = m.id
        draft.brandName = m.name
        brandSelect.value = m.id
        refreshModels()
      }
    }

    nameInput.addEventListener('input', () => {
      draft.name = nameInput.value
      autoBrand()
      syncIcon()
    })
    modelInput.addEventListener('input', () => {
      draft.model = modelInput.value
      modelCustom.value = modelInput.value
      autoBrand()
      syncIcon()
    })
    brandSelect.addEventListener('change', () => {
      draft.brandAuto = false
      const brand = this.brands.byId.get(brandSelect.value)
      draft.brandId = brand?.id ?? ''
      draft.brandName = brand?.name ?? ''
      refreshModels()
    })
    modelSelect.addEventListener('change', () => {
      if (!modelSelect.value) return
      modelInput.value = modelSelect.value
      modelCustom.value = modelSelect.value
      draft.model = modelSelect.value
      syncIcon()
    })
    modelCustom.addEventListener('input', () => {
      modelInput.value = modelCustom.value
      draft.model = modelCustom.value
      syncIcon()
    })

    const typeSelect = E('select', {
      on: {
        change: () => {
          draft.kind = typeSelect.value as Asset['kind']
        }
      }
    }) as HTMLSelectElement
    for (const [v, l] of [
      ['asset', '资产'],
      ['wish', '心愿']
    ]) {
      const o = E('option', { text: l }) as HTMLOptionElement
      o.value = v
      typeSelect.append(o)
    }
    typeSelect.value = draft.kind

    const statusSelect = E('select', {
      on: {
        change: () => {
          draft.status = statusSelect.value as Asset['status']
        }
      }
    }) as HTMLSelectElement
    for (const [v, l] of [
      ['serving', '服役中'],
      ['retired', '已退役'],
      ['sold', '已卖出']
    ]) {
      const o = E('option', { text: l }) as HTMLOptionElement
      o.value = v
      statusSelect.append(o)
    }
    statusSelect.value = draft.status

    const ruleSelect = E('select', {
      on: {
        change: () => {
          draft.rule = ruleSelect.value as Asset['rule']
        }
      }
    }) as HTMLSelectElement
    for (const [v, l] of [
      ['day', '按天'],
      ['count', '按次'],
      ['custom', '自定义天数']
    ]) {
      const o = E('option', { text: l }) as HTMLOptionElement
      o.value = v
      ruleSelect.append(o)
    }
    ruleSelect.value = draft.rule ?? 'day'

    const catSelect = E('select', {
      on: {
        change: () => {
          draft.categoryId = catSelect.value
        }
      }
    }) as HTMLSelectElement
    catSelect.append(E('option', { text: '未分类' }) as HTMLOptionElement)
    for (const c of this.state.categories) {
      const o = E('option', { text: c.name }) as HTMLOptionElement
      o.value = c.id
      catSelect.append(o)
    }
    catSelect.value = draft.categoryId || ''

    const tagBox = E('div', { class: 'tag-picker' })
    const selected = new Set(draft.tagIds ?? [])
    const renderTags = () => {
      tagBox.innerHTML = ''
      for (const t of this.state.tags) {
        tagBox.append(
          E('button', {
            class: 'tag-chip' + (selected.has(t.id) ? ' on' : ''),
            text: t.name,
            on: {
              click: () => {
                if (selected.has(t.id)) selected.delete(t.id)
                else selected.add(t.id)
                draft.tagIds = [...selected]
                renderTags()
              }
            }
          })
        )
      }
      tagBox.append(
        E('button', {
          class: 'tag-chip',
          text: '＋ 新建',
          on: {
            click: async () => {
              const name = await this.askText('标签名称')
              if (!name?.trim()) return
              const tag = { id: uid('tag_'), name: name.trim() }
              this.state.tags.push(tag)
              selected.add(tag.id)
              draft.tagIds = [...selected]
              this.save()
              renderTags()
            }
          }
        })
      )
    }
    renderTags()

    const noteInput = E('textarea', { attrs: { placeholder: '备注（可选）', maxlength: '200' } }) as HTMLTextAreaElement
    noteInput.value = draft.note ?? ''

    const soldPrice = E('input', { attrs: { type: 'number', step: '0.01', placeholder: '卖出价格' } }) as HTMLInputElement
    soldPrice.value = draft.soldPrice ? String(draft.soldPrice) : ''
    const soldDate = E('input', { attrs: { type: 'date' } }) as HTMLInputElement
    soldDate.value = draft.soldDate || ''
    const retireDate = E('input', { attrs: { type: 'date' } }) as HTMLInputElement
    retireDate.value = draft.retireDate || ''
    const targetPrice = E('input', { attrs: { type: 'number', step: '0.01', placeholder: '目标价格' } }) as HTMLInputElement
    targetPrice.value = draft.targetCost ? String(draft.targetCost) : ''
    const targetDate = E('input', { attrs: { type: 'date' } }) as HTMLInputElement
    targetDate.value = draft.targetDate || ''
    const uses = E('input', { attrs: { type: 'number', min: '1', placeholder: '使用次数' } }) as HTMLInputElement
    uses.value = draft.uses ? String(draft.uses) : ''
    const customDays = E('input', { attrs: { type: 'number', min: '1', placeholder: '计费天数' } }) as HTMLInputElement
    customDays.value = draft.customDays ? String(draft.customDays) : ''

    const deduct = E('input', { attrs: { type: 'checkbox' } }) as HTMLInputElement
    deduct.checked = draft.secondhandInCost !== false
    const exclude = E('input', { attrs: { type: 'checkbox' } }) as HTMLInputElement
    exclude.checked = !!draft.excludeTotal

    const expireInput = E('input', { attrs: { type: 'date' } }) as HTMLInputElement
    expireInput.value = draft.expireAt || ''

    let coverData = draft.coverData
    const noteData = [...(draft.noteImagesData ?? [])]
    const coverInput = E('input', { attrs: { type: 'file', accept: 'image/*' } }) as HTMLInputElement
    const notesFile = E('input', { attrs: { type: 'file', accept: 'image/*', multiple: 'multiple' } }) as HTMLInputElement
    const coverBox = E('div', { class: 'tag-picker' })
    const noteBox = E('div', { class: 'tag-picker' })

    const renderCover = () => {
      coverBox.innerHTML = ''
      coverBox.append(coverInput)
      if (coverData) {
        coverBox.append(E('img', { class: 'thumb', attrs: { src: coverData, alt: '主图' } }))
        coverBox.append(
          E('button', {
            class: 'tag-chip',
            text: '× 移除主图',
            on: {
              click: () => {
                coverData = undefined
                renderCover()
              }
            }
          })
        )
      }
    }
    const renderNotes = () => {
      noteBox.innerHTML = ''
      noteBox.append(notesFile)
      noteData.forEach((d, i) => {
        noteBox.append(
          E(
            'span',
            { style: { position: 'relative' } },
            E('img', { class: 'thumb', attrs: { src: d, alt: '' } }),
            E('button', {
              class: 'tag-chip',
              text: '×',
              style: { position: 'absolute', right: '-6px', top: '-6px', padding: '2px 6px' },
              on: {
                click: () => {
                  noteData.splice(i, 1)
                  renderNotes()
                }
              }
            })
          )
        )
      })
    }
    coverInput.addEventListener('change', async () => {
      const f = coverInput.files?.[0]
      if (!f) return
      coverData = await fileToDataUrl(f)
      renderCover()
    })
    notesFile.addEventListener('change', async () => {
      const files = [...(notesFile.files ?? [])]
      for (const f of files) {
        if (noteData.length >= 9) break
        const d = await fileToDataUrl(f)
        if (d) noteData.push(d)
      }
      renderNotes()
    })
    renderCover()
    renderNotes()

    const field = (label: string, control: HTMLElement, full = false) =>
      E('label', { class: 'field' + (full ? ' full' : '') }, E('span', { text: label }), control)

    content.append(
      E(
        'div',
        { class: 'form-grid' },
        E('div', { class: 'field full', style: { alignItems: 'center' } }, preview),
        field('名称', nameInput),
        field('型号', modelInput),
        field('价格', priceInput),
        field('二手估值', secondInput),
        field('类型', typeSelect),
        field('状态', statusSelect),
        field('品牌', brandSelect),
        field('型号选择', modelSelect),
        field('自定义型号', modelCustom),
        field('购买日期', todayInput),
        field('分类', catSelect),
        field('成本规则', ruleSelect),
        field('使用次数', uses),
        field('自定义天数', customDays),
        field('卖出价格', soldPrice),
        field('卖出日期', soldDate),
        field('退役日期', retireDate),
        field('目标价格', targetPrice),
        field('目标日期', targetDate),
        field('到期时间', expireInput),
        E('div', { class: 'field full' }, E('span', { text: '主图' }), coverBox),
        E('div', { class: 'field full' }, E('span', { text: '备注图片（最多 9 张）' }), noteBox),
        E('div', { class: 'field full' }, E('span', { text: '标签' }), tagBox),
        field('备注', noteInput, true),
        E('label', { class: 'toggle field full' }, deduct, E('span', { text: '扣减二手残值' })),
        E('label', { class: 'toggle field full' }, exclude, E('span', { text: '不计入总资产' }))
      )
    )

    const saveBtn = E('button', { class: 'btn primary', text: '保存' })
    saveBtn.addEventListener('click', () => {
      const name = nameInput.value.trim()
      const model = modelInput.value.trim()
      const price = Number(priceInput.value) || 0
      if (!name && !model) {
        toast('请填写名称或型号')
        return
      }
      if (price <= 0) {
        toast('请输入有效价格')
        return
      }
      draft.name = name
      draft.model = model
      draft.price = price
      draft.buyDate = todayInput.value || todayStr()
      draft.kind = typeSelect.value as Asset['kind']
      draft.status = statusSelect.value as Asset['status']
      draft.rule = ruleSelect.value as Asset['rule']
      draft.categoryId = catSelect.value
      draft.tagIds = [...selected]
      draft.note = noteInput.value.slice(0, 200)
      draft.secondhandValue = Number(secondInput.value) || 0
      draft.secondhandInCost = deduct.checked
      draft.excludeTotal = exclude.checked
      draft.soldPrice = Number(soldPrice.value) || 0
      draft.soldDate = soldDate.value
      draft.retireDate = retireDate.value
      draft.targetCost = Number(targetPrice.value) || 0
      draft.targetDate = targetDate.value
      draft.uses = Number(uses.value) || 0
      draft.customDays = Number(customDays.value) || 0
      draft.expireAt = expireInput.value
      draft.coverData = coverData
      draft.noteImagesData = noteData.slice(0, 9)
      draft.icon = autoIcon(model || name)
      const brand = this.brands.byId.get(brandSelect.value)
      draft.brandId = brand?.id ?? ''
      draft.brandName = brand?.name ?? ''
      const idx = this.state.assets.findIndex((a) => a.id === draft.id)
      if (idx >= 0) this.state.assets[idx] = draft
      else this.state.assets.push(draft)
      this.save()
      this.closeModal()
      this.render()
      toast('已保存')
    })

    content.append(
      E(
        'div',
        { class: 'form-actions' },
        E('button', { class: 'btn', text: '取消', on: { click: () => this.closeModal() } }),
        saveBtn
      )
    )
    this.openModal(content)
  }

  openModal(content: HTMLElement, small = false): void {
    this.closeModal()
    const backdrop = E('div', { class: 'modal-backdrop' }, E('div', { class: 'modal' + (small ? ' small' : '') }, content))
    backdrop.addEventListener('mousedown', (e) => {
      if (e.target === backdrop) this.closeModal()
    })
    backdrop.id = 'modal-host'
    document.body.append(backdrop)
  }

  private modalDismiss?: () => void

  closeModal(): void {
    document.getElementById('modal-host')?.remove()
    const dismiss = this.modalDismiss
    this.modalDismiss = undefined
    dismiss?.()
  }

  /**
   * 应用内文本/确认对话框。桌面端（WKWebView）不支持原生 prompt/confirm，
   * 所有交互统一走这里。
   */
  askDialog(title: string, message: string, initial: string | null, password = false): Promise<string | null> {
    return new Promise((resolve) => {
      let settled = false
      const finish = (v: string | null) => {
        if (settled) return
        settled = true
        this.modalDismiss = undefined
        this.closeModal()
        resolve(v)
      }
      const input = initial === null ? null : (E('input', { attrs: { type: password ? 'password' : 'text' } }) as HTMLInputElement)
      if (input) input.value = initial ?? ''
      const content = E(
        'div',
        {},
        E('button', { class: 'close', text: '✕', on: { click: () => finish(null) } }),
        E('h2', { text: title }),
        message ? E('p', { class: 'muted', text: message, style: { margin: '0 0 var(--space-5)', whiteSpace: 'pre-line' } }) : null,
        input ? E('label', { class: 'field full' }, input) : null,
        E(
          'div',
          { class: 'form-actions' },
          E('button', { class: 'btn', text: '取消', on: { click: () => finish(null) } }),
          E('button', { class: 'btn primary', text: '确定', on: { click: () => finish(input ? input.value : '') } })
        )
      )
      this.openModal(content, true)
      this.modalDismiss = () => finish(null)
      if (input) {
        input.focus()
        input.addEventListener('keydown', (e) => {
          if (e.key === 'Enter') finish(input.value)
        })
      }
    })
  }

  askText(title: string, initial = '', password = false): Promise<string | null> {
    return this.askDialog(title, '', initial, password)
  }

  askConfirm(message: string, title = '请确认'): Promise<boolean> {
    return this.askDialog(title, message, null).then((v) => v !== null)
  }

  /** 多个选项的确认框，返回选项索引；关闭返回 null。 */
  askChoice(title: string, message: string, options: string[]): Promise<number | null> {
    return new Promise((resolve) => {
      let settled = false
      const finish = (v: number | null) => {
        if (settled) return
        settled = true
        this.modalDismiss = undefined
        this.closeModal()
        resolve(v)
      }
      const buttons = options.map((label, i) =>
        E('button', {
          class: 'btn' + (i === 0 ? ' primary' : ''),
          text: label,
          on: { click: () => finish(i) }
        })
      )
      const content = E(
        'div',
        {},
        E('button', { class: 'close', text: '✕', on: { click: () => finish(null) } }),
        E('h2', { text: title }),
        E('p', { class: 'muted', text: message, style: { margin: '0 0 14px', whiteSpace: 'pre-line' } }),
        E(
          'div',
          { class: 'form-actions', style: { flexDirection: 'column', alignItems: 'stretch', gap: 'var(--space-3)' } },
          ...buttons
        )
      )
      this.openModal(content, true)
      this.modalDismiss = () => finish(null)
    })
  }

  // ---------------- 备份 ----------------
  exportBackup(): void {
    try {
      const bytes = exportIas(this.state)
      const copy = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer
      const blob = new Blob([copy], { type: 'application/octet-stream' })
      const a = document.createElement('a')
      const d = new Date()
      a.href = URL.createObjectURL(blob)
      a.download = `小满_Backup_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}_${pad(d.getHours())}${pad(d.getMinutes())}.ias`
      a.click()
      setTimeout(() => URL.revokeObjectURL(a.href), 5000)
      toast('已导出备份')
    } catch (e) {
      toast('导出失败：' + (e as Error).message)
    }
  }

  importBackup(): void {
    const input = E('input', { attrs: { type: 'file', accept: '.ias,application/octet-stream' } }) as HTMLInputElement
    input.addEventListener('change', async () => {
      const file = input.files?.[0]
      if (!file) return
      try {
        const bytes = new Uint8Array(await file.arrayBuffer())
        await this.applyBackup(bytes, '本地备份')
      } catch (e) {
        toast('导入失败：' + (e as Error).message)
      }
    })
    input.click()
  }

  /** 解析 .ias 字节并按用户选择覆盖或合并；文件导入与云端恢复共用。 */
  async applyBackup(bytes: Uint8Array, source: string): Promise<void> {
    const { state, count, backupTimeMs } = importIas(bytes)
    const localCount = this.state.assets.length
    const localTime = this.state.settings.updatedAt ?? 0
    const backupTime = backupTimeMs
    const choice = await this.askChoice(
      '导入备份',
      `来源：${source}\n备份：${count} 条，最后修改 ${fmtTime(backupTime)}\n本地：${localCount} 条，最后修改 ${fmtTime(localTime)}\n\n覆盖 = 用备份替换本地；合并 = 保留双方，重复条目取较新的一方。`,
      ['覆盖本地', '合并', '取消']
    )
    if (choice !== 0 && choice !== 1) return

    const preferCloud = backupTime > localTime
    const merged = mergeIasState(this.state, state, choice === 0, preferCloud)
    this.state.assets = merged.assets ?? []
    this.state.categories = merged.categories ?? []
    this.state.tags = merged.tags ?? []
    this.state.incomeCategories = merged.incomeCategories ?? []
    this.state.saveMoney = merged.saveMoney ?? []
    // 兼容从其他端导入时没有图标的情况
    for (const a of this.state.assets) {
      if (!a.icon || a.icon.includes('default')) a.icon = autoIcon(a.model || a.name)
      if (!a.status) a.status = 'serving'
      if (!a.kind) a.kind = 'asset'
      a.incomeRecords ??= []
      a.priceRecords ??= []
      a.usages ??= []
    }
    this.save()
    this.render()
    toast(choice === 0 ? `已覆盖导入 ${count} 条数据` : `已合并 ${count} 条数据`)
  }
}

const app = new App()
app.boot()
