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

比赛表单使用 `RaceDraft` 表示尚未保存的比赛草稿。比赛编辑 module 集中负责草稿转换、校验、加载和保存；Compose 只渲染状态并提交完整的草稿变化。

`RaceRepository` 是比赛持久化生命周期的公开边界。`OfflineRaceRepository` 与 Room adapter 集中负责输入规范化、实体映射、创建/更新时间和 `recordVersion` 推进；上层功能不直接依赖 DAO。

`RacePresentation` 集中负责比赛日期、星期、距离、金额、缺省值和各类 code 的中文展示语义。Home、Detail、Form adapter 只决定布局和 full/compact 展示密度，不在本地重写文案或格式规则。

首页浏览 module 集中负责日期驱动的分组投影、分段与状态筛选、快捷状态选择，以及 mutation 的保存中/失败结果。`RaceRepository` 和 `DaySource` 是可替换 adapter；Compose 只渲染 `HomeUiState` 并发送用户动作。

## 数据与变更约定

- 持久化状态和类型使用稳定的英文 code，中文仅用于 UI 展示。
- 数据 model 只保留稳定 code；中文标签和格式规则统一放在 `RacePresentation`。
- Room 数据结构变化需要提供迁移，并提交 `app/schemas/` 中的 schema。
- 持久化生命周期测试只通过 `RaceRepository` 操作真实的内存 Room 数据库，避免 Fake DAO 复制 SQL 行为。
- 行为变化需要同步测试和相关文档。
- 保持应用为本地存储的单机应用，不引入账号、后端、联网、云同步、通知或多端能力。

## 持续集成

向 `main` 推送或提交 Pull Request 时，GitHub Actions 会：

1. 运行单元测试和 Android Lint。
2. 构建 Debug 与 Release APK。
3. 检查 Room schema 是否已提交。
4. 保存 Debug APK 和 Room schema，保留 14 天。

发版流水线见[发版指南](release.md)。
