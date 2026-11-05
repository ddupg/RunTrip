# 开发指南

## 环境与构建

需要 JDK 17、Android SDK 36、Android SDK Build-Tools 36.0.0，以及 Android 16 设备或模拟器。项目的 `minSdk`、`targetSdk` 和 `compileSdk` 均为 36。

```bash
./gradlew test lint assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/`。连接设备或启动模拟器后可运行 `./gradlew connectedDebugAndroidTest`。自动化检查见 [CI 与发版验证](ci-validation.md)。

## 代码结构

项目只有一个 Gradle module：`app`。主要代码位于 `app/src/main/java/com/ddupg/runtrip/`：

- `data/`：Room、数据模型、Repository、地区目录和外部数据来源。
- `feature/`：比赛、训练、数据来源和工具等页面及状态逻辑。
- `navigation/`：页面路由。
- `ui/`：应用界面、公共组件、主题和展示格式。

应用使用 Kotlin、Compose、Material 3、Room 和 ViewModel。`RaceRepository` 是比赛持久化边界；比赛草稿、校验与保存由表单逻辑管理，`RacePresentation` 统一中文展示。运动大类各自维护详情、草稿与展示，公共流程负责分派。比赛和工具行为见[专项文档](race-tools-behavior.md)。

应用仅在用户主动授权高驰后访问网络；连接和训练导入分别见[高驰连接可靠性](coros-connection.md)与[训练数据导入](training-data.md)。比赛、工具和已保存的训练记录可离线使用。

## 数据与兼容

- Room 当前为 schema v6。生产入口提供 v2 → v3 → v4 → v5 → v6 的连续迁移；v1 不在支持范围内，即使仓库保留其 schema 文件也不表示提供迁移。
- 已支持的数据库版本需保留通往当前版本的迁移路径和测试；个人设备完成升级不足以证明其他受支持数据库均已升级。调整支持基线前须明确兼容策略。
- 比赛以 `races` 和各运动大类的一对一详情表保存；训练记录及导入进度按来源和账号保存。持久化状态、类型和训练子类型使用稳定英文 code，中文用于展示。
- 数据结构变更必须提供迁移、迁移测试，并提交 `app/schemas/` 中的 schema；行为变化同步测试和相关文档。
- 旧自由文本 `city` 在未选择结构化地点的旧记录中继续使用。未来考虑在 2.0 移除时，先确定支持基线、迁移和旧地点保留方案，不能静默丢弃数据。

## 地区数据

使用 `@province-city-china/level` 版本化数据包，MIT 许可随 APK 保留。Gradle 的 `prepareChinaRegions` 解包并校验数据，运行时不联网，也不需要 Node.js。升级时修改 `gradle.properties` 中的 `chinaRegionDataVersion`，运行本地构建与测试，不手工修改生成的 JSON 或提交 `app/build/generated/chinaRegions/`。

`RaceLocation` 保存省、市、区县编码、名称快照和数据包版本，历史名称不随数据包升级自动改写。地区层级不能假设总是三层；直辖市、省直辖县级单位和无区县的城市按实际数据处理。地区选择的 instrumentation 测试位于 `app/src/androidTest/`。

## 变更约定

- 保持 Android 16、中文、本机单用户的产品边界；不增加 RunTrip 账号、后端、云同步、通知或多端能力。
- 高驰访问限于用户授权的既有连接与训练读取。凭据加密保存于应用私有的非备份目录，不写入 Room 或日志。
- 持久化生命周期通过 Repository 和真实 Room 数据库测试；ViewModel 测试通过测试 Repository 控制公开操作的结果。
