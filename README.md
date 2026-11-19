# Waddle

手机与电脑上的终端工作区。桌面端保留现有 Electron、React、Go 实现；安卓端使用 Kotlin、Compose 和原生 PTY，分别构建和发布。

## 仓库结构

```text
apps/android/   安卓客户端、终端组件源码、Gradle 工程与设备测试
apps/desktop/   现有桌面应用、Go 后端与桌面文档站
protocols/      未来跨端协议；首版尚未引入共享协议
.github/        两端 CI 与原有桌面发布流程
docs/           产品调研、MVP 计划与验证记录
```

此仓库沿用 `wu-zhonghua/Waddle` 的完整 Git 历史。目录整理基于提交 `643229a7b40739de17970593e916aaee18a857ba`，没有重新初始化或重写历史。

## 安卓第一版

- 真正的本地 PTY 和 Android 系统 Shell，支持交互程序、ANSI 颜色与屏幕 resize。
- 多终端会话（界面最多 8 个）、切换、关闭、退出码与滚动历史。
- 软键盘、中文组合输入、Ctrl / Esc / Tab / 方向键、Ctrl-C。
- 长按选择复制、带预览的粘贴、双指调整字号。
- 会话由前台服务持有，Activity 重建和短暂后台切换不会主动重建 Shell；通知可关闭全部会话。

要求 Android 8+，当前打包 ARM64 与 x86_64。首版使用 App 私有目录和系统命令，没有集成 Termux App、包管理器、SSH 或 Codex。系统终止进程或重启设备后，Shell 不会恢复；保留在私有 home 中的文件仍然存在。

```sh
cd apps/android
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew :app:connectedDebugAndroidTest
```

环境要求和安装方式见 [安卓开发说明](apps/android/README.md)。

## 桌面端

```sh
cd apps/desktop
npm ci
npm test -- --run
npx tsc --noEmit
# 安装 Go、Task 等依赖后启动完整桌面应用
task dev
```

也可在仓库根目录运行 `task desktop -- dev`。完整说明见 [桌面构建文档](apps/desktop/BUILD.md)。桌面 npm 和 Go 的依赖及相对路径仍由 `apps/desktop` 管理。

## 后续方向

先验证手机本地 Codex，再实现 AI 面板和 SSH。手机与电脑的会话互联需要单独的服务与认证协议。

- [技术调研](docs/research.md)
- [安卓 MVP 计划](docs/mvp-plan.md)
- [仓库迁移说明](docs/workspace-migration.md)

## 许可

桌面端继续使用根目录的 [Apache-2.0 许可](LICENSE)。安卓端使用 [GPL-3.0-only](apps/android/LICENSE)，包含 Termux 终端组件及其上游 Apache-2.0 部分，来源和本地调整见 [组件记录](apps/android/third-party/termux/PROVENANCE.md)。安卓侧的许可不替换桌面项目原有许可。
