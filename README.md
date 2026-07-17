# RunTrip

RunTrip 是一个个人使用的 Android 马拉松行程记录应用，用来替代手机记事本，集中管理比赛日期、城市、项目、参赛状态、路程距离和酒店信息。

## 首版范围

- 按月份浏览即将到来和历史比赛
- 按参赛状态筛选，并在列表中快速更新状态
- 新增、编辑、查看和删除比赛记录
- 记录酒店预订状态、酒店、平台、总价和备注
- 所有数据仅保存在手机本地
- 支持系统浅色与深色模式

首版不包含账号、云同步、通知、交通安排、数据备份恢复和 Play Store 发布。

## 开发环境

- JDK 17 或更高版本
- Android SDK 36
- Android SDK Build-Tools 36.0.0
- Android 16 设备或模拟器

项目固定 `minSdk = 36`、`targetSdk = 36`、`compileSdk = 36`。

## 本地构建

```bash
./gradlew test lint assembleDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/`。

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Room
- ViewModel + Repository
- 单 Activity 导航

项目不申请网络权限。

数据库中的参赛状态、比赛项目和酒店状态使用稳定的英文 code；中文仅用于界面展示，便于后续迁移和调整文案。
