# 小满 · Xiaoman

简体中文 | [English](README.en.md)

「小满」是一款**本地优先**的 Android 资产记录应用，以原生 Java 复刻《小满》的核心体验：记录你拥有的物品、心愿单与日均成本，让每一件物品的价值清晰可见。默认按付费版呈现，无会员页与功能锁；所小满据仅保存在本机。

- 包名：`com.xiaoman.app`　版本：1.6.0（versionCode 40）
- 最低系统：Android 7.0（API 24），适配平板 / 手机竖屏
- 技术栈：原生 Java、单 Activity、自绘图表，不使用 Kotlin / AndroidX / WebView

## 功能

- **资产与心愿**：总价、日均成本、服役天数，支持退役 / 卖出 / 不计入总资产
- **分类与标签**：不限量，可拖动排序、重命名、删除
- **细节记录**：备注、主图与备注图片、二手估值与残值抵扣、到期提醒、应用锁
- **趋势统计**：日均成本曲线、分类与状态筛选
- **品牌与型号**：数码 / 汽车 / 自行车 70+ 品牌、2000+ 型号，按产品线分组；支持自动识别品牌与实物图标
- **备份与恢复**：支持官方《小满》`.ias` 备份双向导入导出（WinZip AES-256）

## 下载与安装

目前通过源码构建安装（APK 未随仓库分发）：

```bash
git clone git@github.com:HanYunChenLuo/xiaoman.git
cd xiaoman
echo "sdk.dir=/path/to/android-sdk" > local.properties   # 或设置 ANDROID_SDK_ROOT
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

要求：

- JDK 17+（开发环境使用 JDK 27）
- Android SDK Platform 34、Build-Tools 34
- Gradle 由 wrapper 自动下载（本机网络下使用腾讯镜像）

签名：若仓库根目录存在 `replica.keystore`，debug 与 release 都会使用它签名，便于覆盖升级；该文件不入库，首次缺少时 release 产物未签名。

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

## 目录结构

```
app/src/main/java/com/xiaoman/app/   # 应用源码
app/src/main/res/                    # 资源与图标
app/src/main/assets/                 # 内置图片、品牌 Logo（brands.json 构建时同步）
data/brands.json                     # 品牌/型号数据（远程更新入口）
AGENTS.md                            # 开发约定与维护说明
```

## 数据与隐私

- 资产、分类、标签、设置与图片均保存在应用本地（SharedPreferences + 应用私有目录）
- 无账号体系、无云端同步、不上传任何用户数据
- 备份文件由用户主动导出，加密格式与《小满》官方 `.ias` 双向兼容

## 第三方资源与商标

- 品牌 Logo 与商标归各自权利人所有，仅用于本地个人展示；部分 Logo 来自 Simple Icons、worldvectorlogo、logo.wine 及品牌官网
- 物品图标由本机 macOS Apple Color Emoji 渲染，仅限个人本地使用，详见 `app/src/main/assets/apple/NOTICE.txt`
- 本项目与《小满》原作者及上述品牌无关联，仅供学习与个人使用
