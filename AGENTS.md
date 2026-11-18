# AGENTS.md

本仓库是「小满」Android 应用（原《小满》复刻，默认按付费版呈现）。原生 Java 实现，单 Activity，自绘图表与对话框，**不使用 Gradle / AndroidX / WebView**。

## 目录结构

```
AndroidManifest.xml        # 入口、权限、版本号（versionCode 40 / 1.6.0）
build.sh                   # aapt2 → javac → d8 → zipalign → apksigner 构建脚本
src/com/xiaoman/app/
  MainActivity.java        # 全部页面：首页/心愿/趋势/设置/新增编辑/详情/备份
  DialogKit.java           # 底部弹层、对话框、图标/品牌/型号选择器
  Ui.java                  # dp、字体、主题色、圆角、图标
  Store.java               # SharedPreferences JSON 数据层
  Backup.java              # 官方 .ias（WinZip AES-256）导入导出
  NotificationHelper.java  # 到期提醒 / AlarmManager
  Widgets.java             # 柱状图、折线图、书签等自绘 View
assets/                    # 内置图片、图标、品牌 Logo；brands.json 构建时由 data/ 生成（已 gitignore）
data/brands.json           # 品牌与型号数据唯一来源（远程更新入口）
res/                       # 应用图标等资源
reference/                 # 原 App 截图参考（本地，不入库）
```

## 构建与安装

```bash
./build.sh                         # 产物：./xiaoman.apk
adb install -r xiaoman.apk         # 设备：小米平板（无线 adb）
adb shell am start -n com.xiaoman.app/.MainActivity
```

- SDK 默认 `/opt/homebrew/share/android-commandlinetools`，可用 `ANDROID_SDK_ROOT` 覆盖；需要 build-tools 34 与 platforms;android-34。
- 签名密钥 `replica.keystore` 不入库；首次构建会自动生成。
- `javac` 固定 `--release 11`；d8 最低 API 24。

## 品牌/型号数据更新（无需发版）

`data/brands.json` 结构：

```json
{
  "schema": 1,
  "dataVersion": 20261119,
  "dataVersionName": "2026.11.19",
  "updatedAt": "2026-11-19",
  "supportedApp": { "minVersionCode": 40, "maxVersionCode": 0 },
  "brands": { "digital": [...], "car": [...], "bike": [...] }
}
```

品牌条目为 `[显示名, id, 别名数组, [[组名, [型号...]], ...]]`。

发布流程：修改 `data/brands.json` → 提升 `dataVersion` / `dataVersionName` → commit 并 push `main`。App 启动时优先通过 GitHub Contents API（失败回退 raw + 时间戳）读取，校验 `schema` 与 `supportedApp` 版本区间，版本更新则缓存到 SharedPreferences 并即时生效；设置页「数据管理 → 品牌与型号数据」可手动检查。

- 构建时 `build.sh` 会把 `data/brands.json` 复制到 `assets/brands/brands.json` 作为离线回退。
- 仓库：https://github.com/HanYunChenLuo/xiaoman

## 修改约定

- 改 UI/功能主要改 `MainActivity.java`，弹层与选择器在 `DialogKit.java`；保持与原 App 的圆角、字号、灰阶风格一致。
- 暗色模式：表单图标用 `themedIcon()`；分段控件选中底 `#3F3F3F`；品牌 Logo 在暗色下需要白色圆角底板。
- 品牌数据只改 `data/brands.json`，不要手改 `assets/brands/brands.json`（构建时覆盖）。
- `.ias` 备份格式与密钥在 `Backup.java`；改动备份字段时同步导入与导出两侧，并与原 App 双向验证。
- 不要随意修改包名 `com.xiaoman.app`（会影响数据与远程更新）；升级版本时同步修改 `AndroidManifest.xml` 的 `versionCode` 与 `MainActivity.APP_VERSION_CODE`。
- 测试设备时避免破坏用户数据：验证前导出备份，验证后恢复；不要在未获允许时写入原 App `com.zhumeng.assets`。
- 本地无 GitHub HTTPS 直连，`origin` 使用 SSH：`git@github.com:HanYunChenLuo/xiaoman.git`。
