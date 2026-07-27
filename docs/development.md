# 开发指南

## 环境要求

- JDK 17 或更高版本
- Android SDK 36
- Android SDK Build-Tools 36.0.0
- Android 16 设备或模拟器

项目固定 `minSdk = 36`、`targetSdk = 36`、`compileSdk = 36`。

## 本地构建

运行测试、Android Lint 并构建 Debug APK：

```bash
./gradlew test lint assembleDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/`。

## 技术结构

- Kotlin
- Jetpack Compose + Material 3
- Room
- ViewModel + Repository
- 单 Activity 导航

应用不申请网络权限。

主要代码目录：

```text
app/src/main/java/com/ddupg/runtrip/
├── data/       # Room、本地数据模型与 Repository
├── feature/    # 首页、比赛表单和详情页
├── navigation/ # 页面路由
└── ui/         # 应用入口、公共组件与主题
```

## 数据与变更约定

- 持久化状态和类型使用稳定的英文 code，中文仅用于 UI 展示。
- Room 数据结构变化需要提供迁移，并提交 `app/schemas/` 中的 schema。
- 行为变化需要同步测试和相关文档。
- 保持应用为本地存储的单机应用，不引入账号、后端、联网、云同步、通知或多端能力。

## 持续集成

向 `main` 推送或提交 Pull Request 时，GitHub Actions 会：

1. 运行单元测试和 Android Lint。
2. 构建 Debug 与 Release APK。
3. 检查 Room schema 是否已提交。
4. 保存 Debug APK 和 Room schema，保留 14 天。

发版流水线见[发版指南](release.md)。
