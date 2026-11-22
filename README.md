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

## 安卓 AI 终端（0.5.0）

安装包仅带 Bash、Git、ripgrep、curl 及其依赖，不再带 Codex、登录或对话入口。终端不配 AI 也能用；在「设置与运行环境」的「AI 模型」中选择 DeepSeek、通义千问、Kimi、智谱或自定义 OpenAI 兼容接口，填写自己的 Key 与模型并测试连接。Key 使用 Android Keystore 加密后仅保存在本机，可替换或删除，保存后不显示明文。

终端可根据当前路径、最近输入输出生成可编辑命令；命令出错后可点「让 AI 看看」解释原因和修法。所有命令都需确认后才在原会话执行一次。只实现单次建议与诊断，尚未实现多步任务或扩展。覆盖安装保留 home 文件；旧版 Codex 用户数据不删除，但不可通过本体访问。

内置命令和动态库的来源、许可证与重打包步骤见[运行时记录](apps/android/runtime/PROVENANCE.md)，配置模型与使用方式见[安卓说明](apps/android/README.md)。

```sh
cd apps/android
python3 tools/prepare-runtime.py --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew :app:connectedDebugAndroidTest
```

运行时的原始第三方包已作为固定构建输入保存在 `apps/android/third-party/runtime-packages`，准备运行时不联网，也不依赖本机旧缓存；首次下载 Gradle 和 Android 构建依赖仍需联网。环境要求和安装方式见 [安卓开发说明](apps/android/README.md)。

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

当前实现范围是[AI 终端阶段 1](docs/dev/2026-11-22-01-ai-terminal.md)。后续按需求验收，再处理可选扩展与多步任务；手机和电脑的会话互联需要单独的服务与认证协议。

- [技术调研](docs/research.md)
- [安卓 MVP 计划](docs/mvp-plan.md)
- [仓库迁移说明](docs/workspace-migration.md)

## 许可

桌面端继续使用根目录的 [Apache-2.0 许可](LICENSE)。安卓端使用 [GPL-3.0-only](apps/android/LICENSE)，包含 Termux 终端组件及其上游 Apache-2.0 部分，来源和本地调整见 [组件记录](apps/android/third-party/termux/PROVENANCE.md)。安卓侧的许可不替换桌面项目原有许可。
