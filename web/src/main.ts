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
  State,
  autoIcon,
  displayName,
  exportIas,
  fmt,
  importIas,
  loadBrands,
  loadState,
  matchBrand,
  money,
  pad,
  saveState,
  statusText,
  sym,
  todayStr,
  uid
} from './model'

type View = 'home' | 'wishes' | 'trends' | 'settings'

const appEl = document.getElementById('app')!
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

function toast(msg: string): void {
  const el = E('div', {
    text: msg,
    style: {
      position: 'fixed',
      left: '50%',
      bottom: '110px',
      transform: 'translateX(-50%)',
      background: 'rgba(20,20,20,0.9)',
      color: '#fff',
      padding: '10px 18px',
      borderRadius: '12px',
      fontSize: '13px',
      zIndex: '100'
    }
  })
  document.body.append(el)
  setTimeout(() => el.remove(), 2200)
}

class App {
  state: State = loadState()
  brands: BrandBook = emptyBook()
  view: View = 'home'
  filter = 'all'
  categoryFilter = 'all'
  range = 365

  async boot(): Promise<void> {
    await initCore()
    this.brands = await loadBrands()
    this.applyTheme()
    this.render()
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
    wrap.append(
      E(
        'div',
        { class: 'footer-ver' },
        `版本 1.0.0 · 核心 ${coreVersion()}\n全部数据仅保存在本机（浏览器）`
      )
    )
    appEl.append(wrap, this.nav(), this.fab())
  }

  hero(): HTMLElement {
    return E(
      'header',
      { class: 'hero' },
      E(
        'div',
        { class: 'hero-top' },
        E('div', { class: 'logo', text: '小满' }),
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
      { class: 'overview', style: { marginTop: '6px' } },
      E('div', { class: 'label muted', text: '心愿总值' }),
      E(
        'div',
        { class: 'value', style: { marginTop: '6px' } },
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
          class: 'chip soft' + (this.filter === key ? ' on' : ''),
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
      E('img', { class: 'icon', attrs: { src: a.icon || autoIcon(model), alt: '' } }),
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
        { class: 'chip-row', style: { paddingTop: '4px' } },
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
        { class: 'overview', style: { marginTop: '6px' } },
        E('div', { class: 'label muted', text: '资产总值' }),
        E(
          'div',
          { class: 'value' },
          E('small', { text: sym(this.state.settings.currency) }),
          this.fmt(totalOfKind(this.state.assets, 'asset'))
        ),
        E('div', { class: 'muted', style: { marginTop: '6px' } }, `日均成本 ${this.money(dailyOfKind(this.state.assets, 'asset'))}`)
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
    fill.setAttribute('fill', '#c9f44f')
    fill.setAttribute('fill-opacity', '0.25')
    const stroke = document.createElementNS(svg.namespaceURI, 'path')
    stroke.setAttribute('d', path)
    stroke.setAttribute('fill', 'none')
    stroke.setAttribute('stroke', '#b6ee25')
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
        this.linkRow('品牌与型号数据', `v${this.brands.versionName || '内置'}`, () => this.checkBrands()),
        this.linkRow('导出 .ias 备份', '下载', () => this.exportBackup()),
        this.linkRow('导入 .ias 备份', '选择文件', () => this.importBackup())
      ]))
    )
    root.append(
      E('div', { class: 'section' }, E('h2', { text: '关于' }), this.group([
        this.linkRow('项目主页', 'GitHub', () => window.open('https://github.com/HanYunChenLuo/xiaoman', '_blank')),
        this.linkRow('核心版本', coreVersion(), null)
      ]))
    )
    const wipe = E('button', {
      class: 'btn danger',
      text: '抹掉所有内容',
      style: { margin: '18px 18px 0', width: 'calc(100% - 36px)' },
      on: {
        click: () => {
          if (confirm('确定抹掉全部本地数据？此操作无法恢复。')) {
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

  checkBrands(): void {
    loadBrands().then((book) => {
      this.brands = book
      toast('已刷新品牌数据')
      this.render()
    })
  }

  manageList(kind: 'categories' | 'tags'): void {
    const list = this.state[kind]
    const content = E('div', {})
    content.append(E('h2', { text: kind === 'categories' ? '分类管理' : '标签管理' }))
    const rows = E('div', { class: 'group' })
    for (const item of list) {
      rows.append(
        E(
          'div',
          { class: 'row' },
          E('span', { class: 'grow', text: item.name }),
          E('button', {
            class: 'btn danger',
            text: '删除',
            on: {
              click: () => {
                if (!confirm(`删除「${item.name}」？`)) return
                this.state[kind] = list.filter((x) => x.id !== item.id)
                if (kind === 'categories') {
                  for (const a of this.state.assets) if (a.categoryId === item.id) a.categoryId = ''
                } else {
                  for (const a of this.state.assets) a.tagIds = (a.tagIds ?? []).filter((t) => t !== item.id)
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
    }
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
            click: () => {
              const name = prompt(kind === 'categories' ? '分类名称' : '标签名称')
              if (!name?.trim()) return
              this.state[kind].push({ id: uid(kind === 'categories' ? 'cat_' : 'tag_'), name: name.trim() })
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
    if (a.note) rows.push(['备注', a.note])
    if (a.excludeTotal) rows.push(['计入总资产', '否'])

    const content = E('div', {})
    content.append(
      E('button', { class: 'close', text: '✕', on: { click: () => this.closeModal() } }),
      E(
        'div',
        { class: 'detail-head' },
        E('img', { attrs: { src: a.icon || autoIcon(a.model || a.name), alt: '' } }),
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
      E(
        'div',
        { class: 'form-actions' },
        E('button', {
          class: 'btn danger',
          text: '删除',
          on: {
            click: () => {
              if (!confirm(`删除「${displayName(a)}」？`)) return
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
      class: 'icon',
      style: { width: '56px', height: '56px' },
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
            click: () => {
              const name = prompt('标签名称')
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

  closeModal(): void {
    document.getElementById('modal-host')?.remove()
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
        const { state, count } = importIas(bytes)
        const replace = confirm(`读取到 ${count} 条记录。\n\n确定 = 覆盖本地数据；取消 = 合并到本地。`)
        if (replace) {
          this.state.assets = state.assets ?? []
        } else {
          const byId = new Map(this.state.assets.map((a) => [a.id, a]))
          for (const a of state.assets ?? []) byId.set(a.id, a)
          this.state.assets = [...byId.values()]
        }
        for (const c of state.categories ?? []) {
          if (!this.state.categories.some((x) => x.id === c.id)) this.state.categories.push(c)
        }
        for (const t of state.tags ?? []) {
          if (!this.state.tags.some((x) => x.id === t.id)) this.state.tags.push(t)
        }
        // 兼容从其他端导入时没有图标的情况
        for (const a of this.state.assets) {
          if (!a.icon || a.icon.includes('default')) a.icon = autoIcon(a.model || a.name)
          if (!a.status) a.status = 'serving'
          if (!a.kind) a.kind = 'asset'
        }
        this.save()
        this.render()
        toast(`成功导入 ${count} 条数据`)
      } catch (e) {
        toast('导入失败：' + (e as Error).message)
      }
    })
    input.click()
  }
}

const app = new App()
app.boot()
