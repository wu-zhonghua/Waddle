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

- 真正的本地 PTY 和内置 Bash，支持交互程序、ANSI 颜色与屏幕 resize。
- 多终端会话（界面最多 8 个）、切换、关闭、退出码与滚动历史；左侧抽屉统一访问文件、终端和多个独立 Codex 对话。
- 提示符显示当前路径；终端底部可向 Codex 描述任务，结合当前会话生成可编辑的 Bash 命令，回车后在原会话运行。
- 标题左侧文件入口：先选手机存储、Waddle 文件或其他来源，再浏览文件夹；底部打开终端/Codex，目录卡片菜单也可新建独立会话。Android 11+ 由用户开启所有文件访问权限后，普通手机目录直接操作原文件；云盘等文档来源可明确复制到 Waddle，完成后进入副本。
- 软键盘、中文组合输入、Ctrl / Esc / Tab / 方向键、Ctrl-C。
- 长按选择复制、带预览的粘贴、双指调整字号。
- 会话由前台服务持有，Activity 重建和短暂后台切换不会主动重建 Shell；通知可关闭全部会话。

要求 Android 8+，默认打包 ARM64；x86_64 用于 CI 模拟器。本地终端使用 Waddle 私有目录、内置工具和 Android 系统命令，不依赖 Termux App；没有任意包管理器或 SSH。系统终止进程或重启设备后，Shell 不会恢复；保留在私有 home 中的文件仍然存在。

## 安卓 Codex（0.4.0）

Codex、执行 helper、Bash、Git、ripgrep、curl 和依赖库已打包到 Waddle。终端直接输入 `codex`，或打开 Codex 页面登录并发送任务；两个入口共用 Waddle 的 home、文件和登录。Codex 面板最多同时打开 4 个独立对话，每个有自己的工作目录、任务和审批；在抽屉新建时可用当前终端目录，也可输入其他本地目录。模型推理需要联网。

已移除 Termux 伴随 App、RUN_COMMAND 权限和本机 WebSocket 桥接。Waddle 直接启动自己的 Codex 子进程，通过标准输入/输出通信。保留原生只读系统设置工具：亮度、亮度模式、屏幕超时、飞行模式和辅助功能状态。新增亮度/自动模式修改工具，需要 Android 系统授权、开启修改开关并逐次确认，写入后读回核对。

当前运行时固定为 Codex 0.170.0，Android 的工作区隔离仍有限。内置命令和动态库的来源、许可证与重打包步骤见[运行时记录](apps/android/runtime/PROVENANCE.md)，使用方式见[安卓说明](apps/android/README.md)。

```sh
cd apps/android
python3 tools/prepare-runtime.py --ndk "$ANDROID_HOME/ndk/28.2.13676358"
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

Android 15 ARM64 模拟器上已跑通 Codex 本地执行路径，并实现 Waddle 客户端和原生设置工具。后续继续验证实体手机、审批与取消的边界、运行时隔离，再考虑 SSH。手机与电脑的会话互联需要单独的服务与认证协议。

- [技术调研](docs/research.md)
- [安卓 MVP 计划](docs/mvp-plan.md)
- [仓库迁移说明](docs/workspace-migration.md)

## 许可

桌面端继续使用根目录的 [Apache-2.0 许可](LICENSE)。安卓端使用 [GPL-3.0-only](apps/android/LICENSE)，包含 Termux 终端组件及其上游 Apache-2.0 部分，来源和本地调整见 [组件记录](apps/android/third-party/termux/PROVENANCE.md)。安卓侧的许可不替换桌面项目原有许可。
