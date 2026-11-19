# Waddle 仓库整理

原始仓库：`https://github.com/wu-zhonghua/Waddle.git`。

基准提交：`643229a7b40739de17970593e916aaee18a857ba`。现有 `.git` 已提升至项目根目录，历史和 origin 保留，开发分支为 `feat/android-terminal-mvp`。此前仅包含调研文件的空仓库已备份，没有替代桌面仓库历史。

## 目录归属

- 原桌面应用及其内部 docs、build、Taskfile、npm workspace、Go module 一起移到 `apps/desktop`，保持内部相对路径。
- GitHub 配置、根 LICENSE/NOTICE、SECURITY 和行为准则保留在仓库根目录。
- 原桌面 AGENTS 和技能位于 `apps/desktop`，仅作用于桌面工作；根 AGENTS 说明两个客户端的边界。
- 安卓 Gradle 工程位于 `apps/android`，不依赖桌面 npm/Go 构建。
- 根 docs 保留安卓技术调研与规划。`protocols` 暂只有说明，不人为抽象未实现的跨端通信。

## 路径适配

桌面 GitHub Actions 的 run 步骤从 `apps/desktop` 执行。缓存和上传路径仍按仓库根目录解析；retry action 命令显式进入桌面目录。版本更新 API 使用 `apps/desktop/package.json`；桌面更新包的 yml 和 blockmap 上传保持原有行为。

桌面开发命令需要在 `apps/desktop` 运行，或通过根 Taskfile 转发。Android 有独立构建和设备测试 workflow。

这次整理没有推送、创建标签或发布安装包，也没有执行桌面签名、自动更新或商店发布。相关远端流程需要后续 CI 验证。

## 本地验证

- 原仓库 1,157 个已跟踪文件均已按新路径保留。
- 桌面 Vitest：37 个测试文件、200 个测试通过；TypeScript 检查通过。发布流程专项测试另行复查，2 个文件、6 个测试通过。
- 桌面 Electron/Vite 开发构建通过，使用 Node 22 和 8 GB 堆上限；默认 Node 25 堆上限下首次构建内存不足。此次未执行完整 Go 后端构建和桌面安装包验收。
- Android Debug APK 构建成功；145 个终端单元测试和 5 个设备集成测试通过。设备测试覆盖 PTY、UTF-8、resize、Ctrl-C、多会话隔离、退出码、IME 组合输入和 Activity 重建。
- Android Lint 无错误；仍有依赖更新、上游兼容代码等警告，未为消除警告改写上游组件。
- 在 Android 15 / API 35 ARM64 模拟器的临时只读实例上，实际检查首次权限提示、拒绝通知后的命令输入、多会话和软键盘布局。尚未完成实体手机及最低支持版本 API 26 的验收。
- 构建日志与截图位于本地被忽略的 `artifacts/verification`；SDK 路径、构建产物和设备输出不进入 Git。
