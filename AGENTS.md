# AGENTS.md

本仓库是「小满」Android 应用（功能完整，无会员页与付费锁）。原生 Java 实现，单 Activity，自绘图表与对话框，**不使用 Kotlin / AndroidX / WebView**。

## 目录结构

```
README.md / README.en.md  # 中英文说明（顶部互相切换）
settings.gradle             # 单模块 :app
build.gradle                # AGP 9.5.1
gradle.properties
gradlew / gradle/           # Gradle 9.9.1 wrapper（distributionUrl 使用腾讯镜像）
app/build.gradle            # namespace/applicationId com.xiaoman.app，versionCode 40 / 1.6.0
app/src/main/AndroidManifest.xml   # 无 package/uses-sdk 属性，由 Gradle 注入
app/src/main/java/com/xiaoman/app/
  MainActivity.java         # 全部页面：首页/心愿/趋势/设置/新增编辑/详情/备份
  DialogKit.java            # 底部弹层、对话框、图标/品牌/型号选择器
  Ui.java                   # dp、字体、主题色、圆角、图标
  Store.java                # SharedPreferences JSON 数据层
  Backup.java               # 官方 .ias（WinZip AES-256）导入导出
  NotificationHelper.java   # 到期提醒 / AlarmManager
  Widgets.java              # 柱状图、折线图、书签等自绘 View
app/src/main/assets/        # 内置图片、图标、品牌 Logo；brands/brands.json 由 data/ 同步（已 gitignore）
app/src/main/res/           # 应用图标等资源
data/brands.json            # 品牌与型号数据唯一来源（远程更新入口）
reference/                  # 原 App 截图参考（本地，不入库）
```

## 构建与安装

```bash
./gradlew assembleRelease        # 产物：app/build/outputs/apk/release/app-release.apk
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.xiaoman.app/.MainActivity
```

- 需要 JDK 17+（本机为 JDK 27）与 Android SDK platform 34 / build-tools 34；`local.properties` 写 `sdk.dir`（不入库，默认 `/opt/homebrew/share/android-commandlinetools`）。
- `replica.keystore` 与签名口令均不入库；`local.properties` 提供 `signing.storePassword` / `signing.keyPassword` / `signing.keyAlias` 时 debug/release 共用签名，便于 `adb install -r` 覆盖升级。
- Gradle 发行包走腾讯镜像（GitHub 的 gradle-distributions 下载在本机不通）；AGP 依赖来自 `dl.google.com`。
- `./gradlew assembleDebug` 亦可；Release 未开启 minify。

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

- Gradle 任务 `syncBrandData` 在 `preBuild` 前把 `data/brands.json` 复制到 `app/src/main/assets/brands/brands.json` 作为离线回退，不要手改该副本。
- 仓库：https://github.com/HanYunChenLuo/xiaoman

## 修改约定

- 改 UI/功能主要改 `MainActivity.java`，弹层与选择器在 `DialogKit.java`；保持与原 App 的圆角、字号、灰阶风格一致。
- 暗色模式：表单图标用 `themedIcon()`；分段控件选中底 `#3F3F3F`；品牌 Logo 在暗色下需要白色圆角底板。
- 品牌数据只改 `data/brands.json`。
- `.ias` 备份格式与密钥在 `Backup.java`；改动备份字段时同步导入与导出两侧，并与原 App 双向验证。
- 不要随意修改包名 `com.xiaoman.app`（会影响数据与远程更新）；升级版本时同步修改 `app/build.gradle` 的 `versionCode` 与 `MainActivity.APP_VERSION_CODE`。
- 测试设备时避免破坏用户数据：验证前导出备份，验证后恢复；不要在未获允许时写入原 App `com.zhumeng.assets`。
- 本机 github.com HTTPS 不通，`origin` 使用 SSH：`git@github.com:HanYunChenLuo/xiaoman.git`。
