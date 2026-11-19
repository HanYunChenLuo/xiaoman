# 小满 · Xiaoman

简体中文 | [English](README.en.md)

「小满」是一款**本地优先**的多端资产记录应用（Android / Web / 桌面）：记录你拥有的物品、心愿单与日均成本，让每一件物品的价值清晰可见。功能完整，无会员页与付费锁；数据默认保存在本机。

- 包名：`com.xiaoman.app`　版本：1.0.0（versionCode 1）
- 最低系统：Android 7.0（API 24），适配平板 / 手机竖屏
- 技术栈：Kotlin UI + Rust 核心；Web 用 Vite/TypeScript + WASM，桌面用 Tauri

## 平台支持

- **Android**：Kotlin UI + Rust 核心（当前可用）
- **Web**：Vite + TypeScript，通过 WASM 复用 `core/`；支持资产/心愿/趋势/设置、主图与备注图片、到期时间与浏览器提醒、品牌数据在线更新、`.ias` 导入导出，可安装为 PWA
- **桌面**：Tauri 外壳加载 Web 构建产物
- 业务核心（数据、备份、品牌匹配、云同步）集中于 Rust，各平台仅维护 UI 与系统集成

## 功能

- **资产与心愿**：总价、日均成本、服役天数，支持退役 / 卖出 / 不计入总资产
- **分类与标签**：不限量，可拖动排序、重命名、删除
- **细节记录**：备注、主图与备注图片、二手估值与残值抵扣、到期提醒、应用锁
- **趋势统计**：日均成本曲线、分类与状态筛选
- **品牌与型号**：数码 / 汽车 / 自行车 70+ 品牌、2000+ 型号，按产品线分组；支持自动识别品牌与实物图标
- **备份与恢复**：支持加密 `.ias` 备份的导入与导出（WinZip AES-256）
- **云同步**（Android / Web）：Google 登录 + Drive 应用数据文件夹，手动上传/恢复加密备份；云端协议由 Rust core 统一实现（GitHub 设备码 + Gist 已实现但暂时隐藏，见 TODO）

## 下载与安装

目前通过源码构建安装（APK 未随仓库分发）：

```bash
git clone git@github.com:HanYunChenLuo/xiaoman.git
cd xiaoman/apps/android
echo "sdk.dir=/path/to/android-sdk" > local.properties   # 或设置 ANDROID_SDK_ROOT
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

要求：

- JDK 17+（开发环境使用 JDK 27）
- Android SDK Platform 34、Build-Tools 34、NDK 30
- Rust（`aarch64-linux-android` 目标）与 Gradle wrapper（本机网络下使用腾讯镜像）

Web / 桌面：
```bash
cd web && npm install && npm run dev      # Web 开发
npm run build                             # Web 产物 dist/
cd ../desktop/src-tauri && cargo build    # 桌面（需先构建 Web）
```
需要 Node 22+、Rust `wasm32-unknown-unknown`、`wasm-bindgen` CLI。

签名：若仓库根目录存在 `replica.keystore`，且 `local.properties` 配置了 `signing.storePassword`、`signing.keyPassword` 与 `signing.keyAlias`，debug 与 release 都会使用它签名，便于覆盖升级；密钥与口令均不入库，缺少时 release 产物未签名。

## 品牌与型号数据

品牌、别名与型号是应用内可远程更新的数据，单一来源为 [`data/brands.json`](data/brands.json)，无需重新发版即可更新。

```json
{
  "schema": 1,
  "dataVersion": 20261119,
  "dataVersionName": "2026.11.19",
  "updatedAt": "2026-11-19",
  "supportedApp": { "minVersionCode": 40, "maxVersionCode": 0 },
  "brands": { "digital": [], "car": [], "bike": [] }
}
```

- 品牌条目：`[显示名, id, 别名数组, [[产品线, [型号...]], ...]]`
- `schema`：数据结构版本，App 不识别更高版本时会忽略
- `supportedApp`：可接受该数据的 App 版本区间（`maxVersionCode` 为 0 表示不限）
- App 启动时通过 GitHub Contents API（失败回退 raw）读取，校验版本区间，`dataVersion` 更新后即时生效；设置 → 数据管理 → 品牌与型号数据可手动检查
- 构建时 `syncBrandData` 会把 `data/brands.json` 复制进 assets 作为离线内置数据

## 登录与云同步配置（Android / Web）

云同步需要自备 OAuth 客户端（不会随仓库分发）：

- **GitHub**（暂时隐藏，TODO）：待提供启用 Device Flow 的 OAuth App Client ID 后恢复；`ENABLE_GITHUB_LOGIN=false` 时界面不显示。
- **Google（Android）**：在 Google Cloud 创建项目、启用 Drive API、配置 OAuth 同意屏幕，创建「桌面应用」类型 OAuth 客户端；将 Client ID 粘贴到 App 内对应项（Google 登录使用 127.0.0.1 loopback 回调）。
- **Google（Web）**：在同一项目创建「Web 应用」类型 OAuth 客户端，把站点来源（如 `http://localhost:5173` 与线上域名）加入「已获授权的 JavaScript 来源」。SPA 走 GIS Token 模型，无需 client_secret；Client ID 写在 `web/src/cloud.ts` 的 `CLIENT_ID`。

也可在 `apps/android/local.properties` 写入 `oauth.github.clientId=...` 与 `oauth.google.clientId=...` 作为默认值。**Google 跳转登录**：构建时写入 `oauth.google.clientId` 会注册 `com.googleusercontent.apps.<client-id>:/oauth2redirect` 回调，授权后浏览器直接跳回 App；应用内临时填写的 Client ID 因无法动态注册 Scheme，会自动使用 127.0.0.1 loopback 方式。GitHub 仍使用设备码（网页授权 + 输入代码），因为其 OAuth App 交换令牌需要 client_secret，不适合内置。云端保存的是加密 `.ias` 备份，Android 与 Web 共用 `core/src/cloud.rs` 的请求构造与解析；桌面端尚未接入。

## 目录结构

```
app/src/main/java/com/xiaoman/app/   # 应用源码
app/src/main/res/                    # 资源与图标
app/src/main/assets/                 # 内置图片、品牌 Logo（brands.json 构建时同步）
data/brands.json                     # 品牌/型号数据（远程更新入口）
AGENTS.md                            # 开发约定与维护说明
```

## 数据与隐私

- Android 数据保存在应用本地（SharedPreferences + 私有目录），Web 保存在浏览器 localStorage，桌面复用 Web 本地存储
- 默认无账号体系；数据默认只保存在本机，只有用户主动点击「上传」时才会把加密备份写入自己的 Google Drive 应用数据文件夹
- 备份文件由用户主动导出，格式为加密的 `.ias`，可在兼容的备份工具之间互相导入

## 第三方资源与商标

- 品牌 Logo 与商标归各自权利人所有，仅用于本地个人展示；部分 Logo 来自 Simple Icons、worldvectorlogo、logo.wine 及品牌官网
- 物品图标由本机 macOS Apple Color Emoji 渲染，仅限个人本地使用，详见 `app/src/main/assets/apple/NOTICE.txt`
- 本项目仅供学习与个人使用
