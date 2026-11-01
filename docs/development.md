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
├── feature/    # 首页、比赛表单、详情页和轻量工具
├── navigation/ # 页面路由
└── ui/         # 应用入口、公共组件与主题
```

新增表单以首页中的 `ModalBottomSheet` 承载，使用首页导航条目作用域内独立 key 的 `RaceFormViewModel`；旋转保留当前 ViewModel，取消或保存成功后显式重置新建会话。新增与编辑复用 `RaceFormContent`、日期选择器和地区选择器。

比赛表单使用 `RaceDraft` 表示尚未保存的比赛草稿。比赛编辑 module 集中负责草稿转换、校验、加载和保存；Compose 只渲染状态并提交完整的草稿变化。

`RaceRepository` 是比赛持久化生命周期的公开边界。`OfflineRaceRepository` 与 Room adapter 集中负责输入规范化、实体映射、创建/更新时间和 `recordVersion` 推进；修改与删除统一返回 `APPLIED` 或 `NOT_FOUND`，上层功能不直接依赖 DAO。

`RacePresentation` 集中负责比赛日期、星期、距离、金额、缺省值和各类 code 的中文展示语义。Home、Detail、Form adapter 只决定布局和 full/compact 展示密度，不在本地重写文案或格式规则。

首页浏览 module 集中负责日期驱动的分组投影、分段与状态筛选、快捷状态选择，以及 mutation 的保存中/失败结果。表单与详情页把保存、删除的进行中、完成和失败结果保存在各自的 `UiState`，避免导航回调丢失。`RaceRepository` 和 `DaySource` 是可替换 adapter；Compose 只渲染状态并发送用户动作。

比赛大类通过 `SportType` 区分，`RaceDetails` 和 `RaceCategory` 是公共契约。路跑、铁三、越野各自持有项目枚举、详情模型、`SportDraft` 实现和展示逻辑。`RaceDraft` 按大类保存编辑草稿，公共表单只编辑当前草稿；专属字段由 `SportFormFields` 分派给对应大类，首页及详情通过 `RacePresentation` 获取项目与字段投影。

Room v5 使用公共 `races` 表和一对一的 `road_running_details`、`triathlon_details`、`trail_running_details` 表。主表保存大类，扩展表保存项目及专属字段；关联读取和完整保存使用事务，切换大类时删除旧详情，删除比赛时外键级联清理。DAO 的单表写入为受保护方法，只向 Repository 暴露完整记录的保存操作。

v2 → v3 迁移将旧记录全部归入路跑，项目及赛事等级移入路跑扩展表，保留公共字段、ID 和生命周期元数据。迁移测试使用已提交的 v2 schema 创建真实文件数据库并通过生产入口打开。个人设备升级验证完成后，可单独清理该迁移和专属测试，保留当前数据库版本号。

v3 → v4 只新增越野扩展表，保留既有路跑与铁三数据，并保留 v2 起的连续迁移路径。越野使用 `CUSTOM` 项目 code，具体比赛距离和累计爬升分别存于 `distanceKm` 与 `elevationGainMeters`。越野数值校验由 `TrailRunningDraft` 负责，通过 `SportDraft.validationErrors` 向公共表单提供字段错误，公共校验不嵌入越野规则。

新增运动大类时，增加该类的项目、详情、草稿、专属字段和展示实现，并在大类分派入口注册；增加对应扩展表、Room 映射和迁移。公共比赛生命周期、首页分组和筛选算法不承载具体大类规则。密封类型的穷尽检查用于提示遗漏的分派分支。

配速计算不依赖 Room 或 `RaceRepository`。距离预设、配速计算、累计分段和格式化集中在纯 Kotlin module 中；Compose 页面只持有当前路由生命周期内的输入状态，离开页面后不保存。累计分段直接按总时间和距离比例计算，不能从已经取整的展示配速反推，以保证终点时间与输入完全一致。

### 比赛地点与地区数据

- 使用 `@province-city-china/level` 的版本化 npm 数据包，MIT 授权随 APK 保留在 `assets/regions/LICENSE`；只打包上游原始 `level.json`，不包含 JavaScript、乡镇、坐标或边界数据。
- Gradle 通过限定模块的 Ivy 仓库解析 `.tgz`，`prepareChinaRegions` 自动解包、检查字段类型和编码唯一性，并将原始 JSON 注册为各 variant 的生成 assets。无需 Node.js，应用运行时不联网。
- 升级只修改根目录 `gradle.properties` 的 `chinaRegionDataVersion`，随后运行 `./gradlew test lint assembleDebug`；不要手工编辑地区 JSON，也不要为更新维护另一套数据格式。构建产物在 `app/build/generated/chinaRegions/`，不提交到仓库。
- 地区点击测试位于 `app/src/androidTest/`；可用 `./gradlew assembleDebugAndroidTest` 检查编译，连接 Android 16 设备后运行 `./gradlew connectedDebugAndroidTest` 验证市级确认、区县选择、返回上级和取消。
- `RegionCatalog` 按上游字段识别行政级别和父子关系，不能假设所有地区都是三层。直辖市使用同一编码表示城市；省直辖县级行政区划容器不可作为城市保存；无区县的城市允许直接确认。
- `RaceLocation` 保存省、市、区县的编码、名称快照和数据包版本；市或区县至少选择一级，区县可为空，省直辖县级单位允许市为空。名称快照用于历史记录展示，不承担业务枚举职责，不随数据包升级自动改写。
- Room v4 → v5 只增加可空的 `location_*` 列，完整保留原有数据和连续迁移路径。旧自由文本不能可靠推断行政区，不自动匹配或回填。
- `Race.city`、`RaceInput.city`、`RaceDraft.city`、`RaceEntity.city` 已标记 `@Deprecated`，仅在旧记录尚未选择结构化地点时使用。新表单写入 `location`；重新选择后将旧 `city` 清空，保留非空列约束直到下一阶段迁移。
- **2.0.0 清理目标**：通过 Room 迁移删除旧 `city` 列，删除上述废弃属性、旧文本展示和表单兼容分支，并更新 schema、迁移测试与文档。发布前必须处理尚未重新选择地点的旧记录，不能静默丢弃其地点；本次不执行删除。

## 数据与变更约定

- 持久化状态和类型使用稳定的英文 code，中文仅用于 UI 展示。
- 状态和类型的 model 只保留稳定 code；中文标签和格式规则统一放在 `RacePresentation`。行政区名称作为历史快照随 `RaceLocation` 保存。
- Room 数据结构变化需要提供迁移，并提交 `app/schemas/` 中的 schema。
- 持久化生命周期测试只通过 `RaceRepository` 操作真实的内存 Room 数据库，避免 Fake DAO 复制 SQL 行为。
- Feature ViewModel 测试统一使用 `TestRaceRepository` 控制公开 mutation 的 gate、failure 和结果；存储规范化、SQL 与 migration 不由 test adapter 模拟。
- 行为变化需要同步测试和相关文档。
- 保持应用为本地存储的单机应用，不引入账号、后端、联网、云同步、通知或多端能力。

## 持续集成

向 `main` 推送或提交 Pull Request 时，GitHub Actions 会：

1. 运行单元测试和 Android Lint。
2. 构建 Debug 与 Release APK。
3. 检查 Room schema 是否已提交。
4. 保存 Debug APK 和 Room schema，保留 14 天。

发版流水线见[发版指南](release.md)。
