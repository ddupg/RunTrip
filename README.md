# RunTrip

RunTrip 是一个个人使用的 Android 马拉松行程记录应用，用来集中管理比赛计划、参赛状态、赛事等级和酒店信息。应用不需要账号，数据仅保存在本机。

## 功能

- 按月份浏览即将到来和历史比赛
- 按参赛状态筛选，并在列表中快速更新状态
- 新增、编辑、查看和删除比赛记录
- 记录中国田协等级与 World Athletics Label
- 记录酒店预订状态、酒店、平台、总价和备注
- 支持系统浅色与深色模式

## 开始开发

需要 JDK 17、Android SDK 36 和 Android SDK Build-Tools 36.0.0。

```bash
./gradlew test lint assembleDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/`。

## 文档

- [文档导航](docs/README.md)
- [产品范围与体验规范](docs/product.md)
- [开发指南](docs/development.md)
- [发版指南](docs/release.md)
