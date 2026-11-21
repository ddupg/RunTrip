# 开发指南

## 环境与构建

需要 JDK 17、Android SDK 36、Android SDK Build-Tools 36.0.0，以及 Android 16 设备或模拟器。项目的 `minSdk`、`targetSdk` 和 `compileSdk` 均为 36。

```bash
./gradlew test lint assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/`。连接设备或启动模拟器后可运行 `./gradlew connectedDebugAndroidTest`。自动化检查见[发版指南](release.md#自动化验证)。

## 代码结构

项目只有一个 Gradle module：`app`。主要代码位于 `app/src/main/java/com/ddupg/runtrip/`：

- `data/`：Room、数据模型、Repository、地区目录和外部数据来源。
- `feature/`：比赛、训练、数据来源和工具等页面及状态逻辑。
- `navigation/`：页面路由。
- `ui/`：应用界面、公共组件、主题和展示格式。

应用使用 Kotlin、Compose、Material 3、Room 和 ViewModel。Repository 管理持久化，表单逻辑管理草稿、校验与保存；运动大类各自维护详情、草稿与展示，公共流程负责分派。领域术语见根目录 [CONTEXT.md](../CONTEXT.md)，用户行为见[产品约定](product.md)，高驰与训练实现约束见[数据接入](data-integration.md)。

## 数据与兼容

- Room 当前为 schema v6。生产入口提供 v2 → v3 → v4 → v5 → v6 的连续迁移；v1 不在支持范围内，即使仓库保留其 schema 文件也不表示提供迁移。
- 已支持的数据库版本需保留通往当前版本的迁移路径和测试；个人设备完成升级不足以证明其他受支持数据库均已升级。调整支持基线前须明确兼容策略。
- 比赛以 `races` 和各运动大类的一对一详情表保存；训练记录及导入进度按来源和账号保存。持久化状态、类型和训练子类型使用稳定英文 code，中文用于展示。
- 数据结构变更必须提供迁移、迁移测试，并提交 `app/schemas/` 中的 schema；行为变化同步测试和相关文档。
- 旧自由文本 `city` 在未选择结构化地点的旧记录中继续使用。移除前须确定支持基线、迁移和旧地点保留方案，不能静默丢弃数据。

## 地区数据

使用 `@province-city-china/level` 版本化数据包，MIT 许可随 APK 保留。Gradle 的 `prepareChinaRegions` 解包并校验数据，运行时不联网，也不需要 Node.js。升级时修改 `gradle.properties` 中的 `chinaRegionDataVersion`，运行本地构建与测试，不手工修改生成的 JSON 或提交 `app/build/generated/chinaRegions/`。

地点保存省、市、区县编码、名称快照和数据包版本，历史名称不随数据包升级自动改写。层级按实际数据处理，地区选择的 instrumentation 测试位于 `app/src/androidTest/`。

## 变更约定

- 保持[产品范围](product.md#范围与体验)，文档记录关键行为与维护约束，不逐项复述界面样式或保存临时验证过程。
- 持久化生命周期通过 Repository 和真实 Room 数据库测试；ViewModel 测试通过测试 Repository 控制公开操作的结果。
