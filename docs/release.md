# 发版指南

本文面向 RunTrip 项目维护者。Android 后续版本必须使用同一签名，release keystore 和密码需要安全、分开备份，不能依赖 GitHub Secrets 作为备份。

## 自动化验证

向 `main` 推送或提交 Pull Request 时，仅修改 `docs/` 或根目录 Markdown 文件会跳过 Android CI；包含代码、构建配置或 workflow 改动时照常运行。发版 tag 始终执行完整验证。

验证包括单元测试、Android Lint、Debug / Release 构建，以及检查 `app/schemas/` 中已跟踪的修改和未跟踪的新文件；schema 变化必须提交。

独立任务在 Android 16（API 36）模拟器运行 `connectedDebugAndroidTest`，覆盖地区选择、训练和「我」页面。失败测试报告、Debug APK 和 Room schema 保留 14 天。本地构建与设备测试命令见[开发指南](development.md#环境与构建)。

发版复用以上验证，全部通过后才构建签名 APK 并发布 GitHub Release；验证任务不接收签名 secrets。

## 版本管理

版本号由 `gradle.properties` 中的两个属性管理：

- `runTripVersionName`
- `runTripVersionCode`

每次发版：

1. 通过独立 Pull Request 更新两个版本属性。
2. 合并 Pull Request，并确认 CI 通过。
3. 在合并后的 commit 上创建与 `runTripVersionName` 一致的 `vX.Y.Z` tag。
4. 推送 tag，等待 GitHub Release 流水线完成。
5. 下载 APK，并用随附的 SHA-256 文件校验完整性。

发版流水线会校验 tag 与代码中的版本号一致，避免从错误的 commit 发布。

## 首次配置签名

在 GitHub 仓库的 `Settings → Secrets and variables → Actions` 中配置：

- `RUNTRIP_KEYSTORE_BASE64`：release keystore 文件的 Base64 内容
- `RUNTRIP_KEYSTORE_PASSWORD`：keystore 密码
- `RUNTRIP_KEY_ALIAS`：签名 key alias
- `RUNTRIP_KEY_PASSWORD`：签名 key 密码

生成 keystore：

```bash
keytool -genkeypair -v \
  -keystore runtrip-release.jks \
  -alias runtrip \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

在 macOS 上复制 Base64 内容：

```bash
base64 -i runtrip-release.jks | pbcopy
```

不要提交 keystore 或密码。将 keystore 加密备份，并把密码保存在独立的密码管理器中。

## 创建发版

在版本 Pull Request 已合并且本地分支指向目标 commit 后执行：

```bash
git tag -a vX.Y.Z -m "RunTrip vX.Y.Z"
git push origin vX.Y.Z
```

以 `v` 开头的 tag 会触发 `.github/workflows/release.yml`。流水线会：

1. 运行上述自动化验证。
2. 通过后校验 tag 与 `runTripVersionName` 一致，构建并校验签名 Release APK。
3. 创建 GitHub Release 并上传：
   - `RunTrip-vX.Y.Z.apk`
   - `RunTrip-vX.Y.Z.apk.sha256`
