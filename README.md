# Waddle

手机与电脑上的终端工作区。桌面端保留现有 Electron、React、Go 实现；安卓端使用 Kotlin、Compose 和原生 PTY，分别构建和发布。

Waddle 由南溟科技（Nanming Tech）出品。

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
- 多终端会话（界面最多 8 个）、切换、关闭、退出码与滚动历史；左侧抽屉统一访问文件与终端。
- 提示符显示当前路径；终端底部可向 AI 描述任务，结合当前会话生成可编辑的 Bash 命令，回车后在原会话运行。
- 标题左侧文件入口：先选手机存储、Waddle 文件或其他来源，再浏览文件夹；底部打开终端，目录卡片菜单也可新建独立会话。Android 11+ 由用户开启所有文件访问权限后，普通手机目录直接操作原文件；云盘等文档来源可明确复制到 Waddle，完成后进入副本。
- 软键盘、中文组合输入、Ctrl / Esc / Tab / 方向键、Ctrl-C。
- 长按选择复制、带预览的粘贴、双指调整字号。
- 会话由前台服务持有，Activity 重建和短暂后台切换不会主动重建 Shell；通知可关闭全部会话。

要求 Android 8+，默认打包 ARM64；x86_64 用于 CI 模拟器。本地终端使用 Waddle 私有目录、内置工具和 Android 系统命令，不依赖 Termux App；没有任意包管理器或 SSH。系统终止进程或重启设备后，Shell 不会恢复；保留在私有 home 中的文件仍然存在。

## 安卓 AI 终端（0.6.0，阶段 1）

国际版随 APK 带 Bash、Git、ripgrep、curl、Codex 0.170.0 及依赖。在任意终端目录敲 `codex`，首次选择「对话界面（推荐）」或原版「终端界面」，可记住选择。`codex --waddle-chat`、`codex --waddle-terminal` 临时切换；普通 CLI 参数仍传给原程序。对话可返回原终端，抽屉只列进行中的对话。对话页的「历史对话」可选择本机保存的 session，恢复消息并在原工作目录继续。

「设置与运行环境 → 账号」汇总 Codex 和内置模型状态。Codex 支持 ChatGPT 设备登录、API Key 和退出；终端与对话共享原有私有 `.codex` 登录。内置 AI 保留 DeepSeek、通义千问、Kimi、智谱和自定义 OpenAI 兼容配置，Key 用 Android Keystore 加密保存。「问 AI，写命令」「让 AI 看看」可选内置模型或 Codex，生成的命令可编辑，确认后才在原终端执行一次。

同一份源码通过 `waddleDistribution` 选择国际版或国内版；国内版不打包海外工具，账号页也不显示它们。阶段 1 只带 Codex，Claude Code、Antigravity 留给后续阶段。两种版本覆盖安装均保留用户文件，国内版也不删除旧 Codex 登录。

```sh
cd apps/android
python3 tools/prepare-runtime.py --distribution international --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
# 国内版：与国际版分别生成运行时
python3 tools/prepare-runtime.py --distribution domestic --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PwaddleDistribution=domestic
```

原始第三方包作为固定构建输入保存在 `apps/android/third-party/runtime-packages`，包括两个 Codex 包；准备运行时离线校验，不依赖滚动下载地址。首次安装 Gradle/SDK 依赖仍需联网。详细环境、使用与测试见[安卓说明](apps/android/README.md)，来源和许可证见[运行时记录](apps/android/runtime/PROVENANCE.md)。

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

当前实现范围是[内置工具与账号阶段 1](docs/dev/2026-11-22-04-bundled-agents-and-accounts.md)及[历史 session 入口补充](docs/dev/2026-11-22-05-codex-session-history.md)。后续按需求验收，再处理 Claude Code、Antigravity 和多步任务；手机和电脑的会话互联需要单独的服务与认证协议。

- [技术调研](docs/research.md)
- [安卓 MVP 计划](docs/mvp-plan.md)
- [仓库迁移说明](docs/workspace-migration.md)

## 许可

桌面端继续使用根目录的 [Apache-2.0 许可](LICENSE)。安卓端使用 [GPL-3.0-only](apps/android/LICENSE)，包含 Termux 终端组件及其上游 Apache-2.0 部分，来源和本地调整见 [组件记录](apps/android/third-party/termux/PROVENANCE.md)。安卓侧的许可不替换桌面项目原有许可。
