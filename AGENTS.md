# AGENTS.md

本仓库是「小满」多端资产记录应用：Android / Web / 桌面（Tauri）共用 `core/` Rust 核心，各端只维护 UI 与系统集成。功能完整，无会员页与付费锁。

## 目录结构

```
apps/android/            # Android 工程（Gradle 单模块 :app）
  app/src/main/java/com/xiaoman/app/   # Kotlin UI（MainActivity/DialogKit/Ui/Store/Backup/NativeCore 等）
  app/src/main/assets/                 # 内置图标；brands/brands.json 由 data/ 同步（gitignore）
  app/build.gradle                     # 版本号、Rust 构建任务、签名配置
core/                    # Rust 核心：calc.rs（计算）、backup.rs（.ias 编解码）、lib.rs（JNI/wasm 导出）
web/                     # Vite + TypeScript Web 端（wasm-bindgen）
desktop/src-tauri/       # Tauri 桌面外壳，加载 web/dist
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
Tauri 通过 `frontendDist: ../../web/dist` 加载 Web 端；正式打包需安装 Tauri CLI。

## 云同步（Android）

`apps/android/.../CloudSync.kt` 实现 GitHub 设备码 OAuth + Gist、Google PKCE + Drive appDataFolder；**当前 GitHub 通过 `ENABLE_GITHUB_LOGIN=false` 隐藏（TODO: 提供真实 OAuth App Client ID 后恢复，并去掉界面条件）**；只有 Google 显示在设置登录入口与云同步页；云端内容为 `.ias` 字节（Gist 用 Base64 文本），手动上传/恢复。Client ID 可写在 `apps/android/local.properties` 的 `oauth.github.clientId` / `oauth.google.clientId`，或在 App 内「设置 → 数据同步」填写。Google 构建时 Client ID（local.properties）会生成自定义 Scheme 回调并注册 intent-filter（授权后直接跳回 App）；应用内填写的 ID 因 Scheme 不可动态注册，自动回退 loopback（需「桌面应用」类型客户端）。MainActivity 为 singleTask 并在 onNewIntent 处理回调。GitHub 仍用设备码（OAuth App 需启用 Device Flow），不使用重定向（交换令牌需 client_secret）。Web/桌面云同步尚未实现。

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
- 计算与 `.ias` 编解码以 Rust `core/` 为准，跨端保持一致；改动后在 Android + Web 两端验证导入导出。
- 品牌数据只改 `data/brands.json`；各端构建/启动时同步。
- 暗色模式：Android 表单图标用 `themedIcon()`，品牌 Logo 需白色底板；Web 使用 CSS 变量。
- 升级版本：`apps/android/app/build.gradle` 的 `versionCode/versionName`；Android 的 `APP_VERSION_CODE` 取自 `BuildConfig`。
- 测试设备时先导出 `.ias` 备份，避免破坏数据。
- 本机 github.com HTTPS 不通，`origin` 使用 SSH：`git@github.com:HanYunChenLuo/xiaoman.git`；cargo 走 rsproxy 镜像。
