# AGENTS.md

> 新对话接手：先阅读根目录 `HANDOFF.md`（当前进度、未提交改动、凭据/设备状态与待办）。

本仓库是「小满」多端资产记录应用：Android / Web / 桌面（Tauri）共用 `core/` Rust 核心，各端只维护 UI 与系统集成。功能完整，无会员页与付费锁。

## 目录结构

```
apps/android/            # Android 工程（Gradle 单模块 :app）
  app/src/main/java/com/xiaoman/app/   # Kotlin UI（MainActivity/DialogKit/Ui/Store/Backup/NativeCore 等）
  app/src/main/assets/                 # 内置图标；brands/brands.json 由 data/ 同步（gitignore）
  app/build.gradle                     # 版本号、Rust 构建任务、签名配置
core/                    # Rust 核心：calc.rs（计算）、backup.rs（.ias 编解码）、cloud.rs（Drive 云备份协议）、update.rs（Releases 更新检查）、lib.rs（JNI/wasm 导出）
web/                     # Vite + TypeScript Web 端（wasm-bindgen）
desktop/src-tauri/       # Tauri 桌面外壳：加载 web/dist（编译期嵌入）+ 原生云同步命令
data/brands.json         # 品牌与型号数据唯一来源（远程更新入口）
README.md / README.en.md
```

## 构建

### Android
```bash
cd apps/android
./gradlew assembleRelease        # 产物 apps/android/app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
```
依赖：JDK 17+、SDK platform/build-tools 34、NDK 30、Rust（`aarch64-linux-android` 目标）。Gradle 任务 `buildRustAndroid`/`copyRustAndroid` 自动构建 `core` 并打包 `libxiaoman_core.so`。`apps/android/local.properties` 提供 `sdk.dir` 与签名口令；`apps/android/replica.keystore` 均不入库。

### Web
```bash
cd web
npm install
npm run dev        # http://localhost:5173（predev 自动构建 WASM 并同步 data/brands.json）
npm run build      # 产物 web/dist
```
依赖 Node 22+、Rust `wasm32-unknown-unknown`、`wasm-bindgen` CLI（与 crate 版本一致，当前 0.2.130）。

### 桌面（Tauri）
```bash
cd web && npm run build
cd ../desktop/src-tauri && cargo build
./target/debug/xiaoman-desktop
```
Tauri 通过 `frontendDist: ../../web/dist` 加载 Web 端（编译期嵌入）；正式打包需安装 Tauri CLI。桌面云同步需要 `desktop/src-tauri/oauth.enc`（见上），无文件时应用仍可运行、仅隐藏云同步。

## 云同步（Android / Web / 桌面）

云端协议（文件名 `xiaoman_backup.ias`、Drive appDataFolder 的 URL/方法/头部、响应解析、中文错误映射）统一在 `core/src/cloud.rs`，通过 JNI 与 wasm 导出；各端只负责 OAuth 授权、token 存储和 HTTP 执行：

- Android：`apps/android/.../CloudSync.kt` 负责 GitHub 设备码 OAuth + Gist、Google PKCE + Drive；Drive 部分调用 `NativeCore.cloud*`。**当前 GitHub 通过 `ENABLE_GITHUB_LOGIN=false` 隐藏（TODO: 提供真实 OAuth App Client ID 后恢复，并去掉界面条件）**；云端内容为 `.ias` 字节（Gist 用 Base64 文本），手动上传/恢复。Client ID 可写在 `apps/android/local.properties` 的 `oauth.github.clientId` / `oauth.google.clientId`，或在 App 内「设置 → 数据同步」填写。Google 构建时 Client ID（local.properties）会生成自定义 Scheme 回调并注册 intent-filter（授权后直接跳回 App）；应用内填写的 ID 因 Scheme 不可动态注册，自动回退 loopback（需「桌面应用」类型客户端）。MainActivity 为 singleTask 并在 onNewIntent 处理回调。GitHub 仍用设备码（OAuth App 需启用 Device Flow），不使用重定向（交换令牌需 client_secret）。
- Web：`web/src/cloud.ts` 用 Google Identity Services Token 模型（`web/src/cloud.ts` 内 `CLIENT_ID`，SPA 无 client_secret）+ `core/src/cloud.rs` 的请求描述，`fetch` 执行；设置页「数据同步」提供登录、上传、恢复、退出。token 存 localStorage `xiaoman_google_v1`。OAuth 客户端需配置「已获授权的 JavaScript 来源」（localhost:5173、线上域名）。
- 桌面（Tauri）：系统浏览器 + `http://127.0.0.1:41888` loopback（PKCE）；Rust 命令 `google_login` / `google_refresh` / `cloud_file_info` / `cloud_upload` / `cloud_download` 用 reqwest 执行 `core/src/cloud.rs` 的请求。凭据来自**构建时嵌入的加密文件** `desktop/src-tauri/oauth.enc`（AES-256-GCM，密钥随程序分发，仅防明文入库），已 gitignore；用 `desktop/scripts/seed-oauth.mjs` 生成（环境变量或 `--id/--secret`）。**没有该文件时桌面端不显示云同步入口**（`cloudSupported()` 为 false）。CI 用 Actions Secret 在构建前写入该文件。Google Desktop 客户端换取令牌仍需 `client_secret`；授权 URL 用 `prompt=select_account`（首次仍需用户勾选 Drive 权限，应用无法预勾选）。

## 应用更新

更新检查协议（GitHub Releases API、版本比较、APK 资产筛选）在 `core/src/update.rs`，JNI + wasm 导出：

- Android：`Updater.kt` 调 `NativeCore.update*` 检查 `HanYunChenLuo/xiaoman` 的 latest release，发现新版本后下载 `*-android.apk`（资产名以 `.apk` 结尾）到 cache，经 FileProvider 拉起系统安装器（同签名覆盖升级；系统要求用户确认，无法静默安装）。设置 → 关于 → 检查更新。
- 桌面：`tauri-plugin-updater` + GitHub Release 的 `latest.json` + minisign 签名；设置 → 关于 → 检查更新，下载安装后调用 `restart_app`。签名公钥在 `tauri.conf.json`，私钥仅存本机 `~/.tauri/xiaoman-updater.key`（CI secret `TAURI_SIGNING_PRIVATE_KEY`）。
- 发布流程：`.github/workflows/release.yml`，推送 `v*` tag 触发；需先提升 `apps/android/app/build.gradle` 的 `versionCode/versionName` 与 `desktop/src-tauri/tauri.conf.json` 的 `version`，并让 tag 与 versionName 一致。桌面 job 用 `npx @tauri-apps/cli build --target aarch64-apple-darwin` 打包，自行生成 `latest.json`（`darwin-aarch64` 条目，签名取 `.sig`），资源统一命名为 `xiaoman-desktop-aarch64.app.tar.gz` / `.dmg` / `latest.json`；Android job 上传 `xiaoman-android.apk`。
- 桌面打包：`cd web && npm run build` 后 `cd desktop && npx @tauri-apps/cli@2.12.6 build`（需 `TAURI_SIGNING_PRIVATE_KEY`，本地可 `export TAURI_SIGNING_PRIVATE_KEY=$(cat ~/.tauri/xiaoman-updater.key)`）。`bundle.resources` 包含 `oauth.enc`，缺失时先用 seed 脚本生成（可选，缺失则云同步隐藏）。

## 品牌/型号数据

`data/brands.json` 结构：
```json
{
  "schema": 1,
  "dataVersion": 20261120,
  "dataVersionName": "2026.11.20",
  "updatedAt": "2026-11-20",
  "supportedApp": { "minVersionCode": 1, "maxVersionCode": 0 },
  "brands": { "digital": [], "car": [], "bike": [] }
}
```
品牌条目为 `[显示名, id, 别名数组, [[组名, [型号...]], ...]]`。

发布：修改 `data/brands.json` → 提升 `dataVersion/dataVersionName` → commit/push `main`。Android 启动时经 GitHub Contents API（回退 raw）拉取；Web 构建时复制到 `public/brands.json`。仓库：https://github.com/HanYunChenLuo/xiaoman

## 修改约定

- UI：Android 改 `apps/android/app/src/main/java/com/xiaoman/app/`；Web 改 `web/src/`；桌面仅外壳。
- 桌面：`frontendDist` 在**编译期**嵌入二进制，改完 Web 后必须 `touch desktop/src-tauri/src/main.rs && cargo build` 再启动，否则运行的是旧前端。凭据文件：`node desktop/scripts/seed-oauth.mjs`（需环境变量 `XIAOMAN_GOOGLE_CLIENT_ID` / `XIAOMAN_GOOGLE_CLIENT_SECRET`），生成 `desktop/src-tauri/oauth.enc`（gitignore）。关闭窗口=隐藏到菜单栏（不退出），托盘左键恢复、菜单「打开小满 / 退出」；macOS 图标按苹果网格留边（1024 画布内 824 圆角方），Dock 大小才与其他应用一致。
- Web 端已支持主图/备注图片（Canvas 压缩为 JPEG data URL，存 localStorage；随 `.ias` 的 images/ 条目互通，已在 Android 导入验证）与到期时间字段；图片数量/体积受 localStorage 限制。
- Web 品牌数据会缓存到 localStorage（`xiaoman_brands_v1`），设置页「品牌与型号数据」可通过 GitHub（Contents API，失败回退 raw）拉取最新版本；分类/标签支持重命名与上下移动；到期提醒使用浏览器 Notification（页面打开时检查，需手动授权）；提供 `public/manifest.webmanifest` 支持安装为 PWA。
- 计算、`.ias` 编解码与云备份协议以 Rust `core/` 为准，跨端保持一致；改动后在 Android + Web 两端验证导入导出与云同步。
- 云同步只改 `core/src/cloud.rs` 的协议与各端授权/存储；新增云端字段时同步更新 JNI（`lib.rs`）与 wasm 导出、两端薄适配层（`CloudSync.kt` / `web/src/cloud.ts`）。
- 品牌数据只改 `data/brands.json`；各端构建/启动时同步。
- 应用图标/Logo：`apps/android/app/src/main/res/drawable/appicon.png`（浅色版，启动器图标）；`apps/android/app/src/main/assets/img/logo_light.png` 与 `logo_dark.png` 用于 Android 首页顶栏随主题切换；Web 端 `web/public/logo_light.png`/`logo_dark.png`（由 sync-data 从 Android assets 复制，gitignore）用于页面 Logo，favicon/PWA 图标使用浅色版。
- 暗色模式：Android 表单图标用 `themedIcon()`，品牌 Logo 需白色底板；Web 使用 CSS 变量。
- Android UI 尺寸统一引用 `app/src/main/java/com/xiaoman/app/Design.kt` 的 `Radius` / `FontSize` / `Size` / `Spacing` 令牌；不要在布局里新增 `16f`、`20f` 之类的裸数字。
- 升级版本：`apps/android/app/build.gradle` 的 `versionCode/versionName`；Android 的 `APP_VERSION_CODE` 取自 `BuildConfig`。
- 测试设备时先导出 `.ias` 备份，避免破坏数据。
- 本机 github.com HTTPS 不通，`origin` 使用 SSH：`git@github.com:HanYunChenLuo/xiaoman.git`；cargo 走 rsproxy 镜像。
