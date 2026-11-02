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

应用申请 `INTERNET` 权限用于用户主动发起的高驰连接、授权结果领取、令牌续期、账号昵称及训练记录读取。比赛、工具与地区数据仍可离线使用。

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

Room v6 的比赛数据使用公共 `races` 表和一对一的 `road_running_details`、`triathlon_details`、`trail_running_details` 表。主表保存大类，扩展表保存项目及专属字段；关联读取和完整保存使用事务，切换大类时删除旧详情，删除比赛时外键级联清理。DAO 的单表写入为受保护方法，只向 Repository 暴露完整记录的保存操作。

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
- 保持应用为本地存储的单机应用；仅按用户授权访问外部数据来源，不引入 RunTrip 自身账号、后端、云同步、通知或多端能力。

### 数据来源连接

- `DataSourceConnector` 是来源连接边界，提供当前连接、发起授权、完成授权和断开操作；当前仅注册高驰。来源标识使用稳定的 `coros` code，具体授权协议保留在 `data/source/coros`。
- 高驰采用官方登录会话流程和 PKCE：创建会话后打开浏览器；「我」页面恢复到前台时检查会话，不设置自定义 URI 回跳或本机监听服务，不在后台定时轮询。
- 当前操作尚未结束时收到的前台恢复事件会合并为一次待执行检查，在操作及状态读取完成后执行，避免浏览器返回事件被忙碌状态丢弃；没有新的恢复事件时不重复检查。
- OAuth `/userinfo` 提供账号标识；官方 MCP 的 `queryUserInfo` 提供昵称。只提取昵称，不保存或展示其中的身高、体重等资料；训练页面通过独立的训练读取边界查询记录。账号名非空时 UI 只显示账号名。
- 会话中间结果（ticket、code）和领取后的凭据分别保存，避免账号资料读取失败后重新领取已使用的会话。进程重建可以继续当前阶段；无法恢复的失效授权需重新连接。
- 凭据使用 Android Keystore 的 AES-256-GCM 密钥加密，通过 `AtomicFile` 保存到 `noBackupFilesDir/data-sources`，不进入 Room、备份或日志。加密格式当前为 v1，后续格式变化须显式迁移；解密或版本校验失败不会静默重建。
- 凭据按来源分别存储。令牌轮换成功后原子保存；`invalid_grant` 清理本地连接并要求重新授权。解除绑定清除本地凭据，不调用高驰运动写入操作。
- 授权和账号资料实现使用 Android/JDK 与已有协程能力，无新增第三方运行时依赖。训练记录通过 Room 保存，不把授权凭据写入数据库。
- 单元测试覆盖 PKCE、state 校验、会话恢复、昵称、续期、解绑和加密存储；Android UI 测试位于 `feature/me/MeScreenTest.kt`，连接 Android 16 设备后运行 `./gradlew connectedDebugAndroidTest`。

### 训练记录

- `TrainingDataSource` 扩展连接边界，提供带预期账号标识的训练摘要读取；高驰的连接与训练读取共用同一个实例、凭据存储及互斥锁，避免重复续期和账号切换时串号。
- `CorosTrainingParser` 校验官方 MCP `querySportRecords` 的 JSON 编码文本：请求日期、声明数量、序号、来源标识、运动类型、距离单位及时长均需有效；响应变化或字段损坏会报告失败，不静默丢行或填零。高驰的记录标识由运动代码与 LabelId 组成。只保存子类型、来源日期、可空的开始时间/距离及时长；不保存地点、心率、配速等本版不用的字段。
- 跑步代码为 100–103；骑行为 200–205、299；游泳为 300–301。代码到稳定子类型的映射留在高驰 adapter，UI 只依赖统一的 `TrainingSport` 与 `TrainingSubtype`。
- `LocalTrainingRepository` 先读取本年度，再按不超过五个日历年的窗口逆序扫描到 1970 年。每次请求上限 1000 条，达到上限则二分日期区间；单日仍达上限时明确报告未获取完整，不推进该窗口的进度。不能把官方文本摘要的距离精度视为原始传感器精度。
- 每个窗口的记录和下一历史窗口终点在同一事务内写入。失败后从窗口检查点继续，手动更新从上次成功更新日期（含当天）读取至今天；不重扫已经完成的更早历史，也不传播来源删除操作。
- Room v5 → v6 新增 `training_activities` 和 `training_imports`，两表都以来源和账号作为键的一部分；比赛表不变，保留 v2 起的连续迁移路径。凭据继续使用原来的加密存储，schema 版本为 6。
- `TrainingViewModel` 管理连接检查、按账号订阅本地记录、首次导入、手动更新及失败状态；恢复前台重新检查绑定，重复事件和更新不会启动并行导入。网络错误保留本地记录，失效授权引导重新连接。
- `TrainingPresentation` 统一运动名称、单位、时长和日期投影。有时间戳时，日期和时间都使用手机当前时区；缺少时间戳时使用来源日期并显式显示时间缺失。筛选与日期分组不读取网络。
- 测试覆盖三类全部子类型、文本响应校验、空历史间隔、条数上限拆分、失败续传、去重、账号隔离、真实 Room 持久化以及 v5 → v6 的生产入口迁移。训练 UI 测试位于 `feature/training/TrainingScreenTest.kt`。

训练详情和周/月统计留到后续独立变更。

## 持续集成

向 `main` 推送或提交 Pull Request 时，GitHub Actions 会：

1. 运行单元测试和 Android Lint。
2. 构建 Debug 与 Release APK。
3. 检查 Room schema 是否已提交。
4. 保存 Debug APK 和 Room schema，保留 14 天。

发版流水线见[发版指南](release.md)。
