# CI 与发版验证

向 `main` 推送、提交 Pull Request 或推送发版 tag 时，CI 运行单元测试、Android Lint，构建 Debug 与 Release APK，并检查 `app/schemas/` 中已跟踪的修改和未跟踪的新文件。schema 有变化时必须提交，避免遗漏数据库迁移记录。

独立的模拟器任务在 Android 16（API 36）上运行 `connectedDebugAndroidTest`，覆盖地区选择、训练和「我」页面的 instrumentation 测试。失败时上传测试报告，保留 14 天。Debug APK 和 Room schema 同样保留 14 天。

发版复用以上验证；验证全部通过后才构建签名 APK 和发布 GitHub Release。模拟器测试不会接收签名 secrets。本地连接 Android 16 设备或启动模拟器后，可运行：

```bash
./gradlew connectedDebugAndroidTest
```
