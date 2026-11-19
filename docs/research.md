# Waddle 安卓端与 Codex 技术调研

调研日期：2026-11-19。结论：安卓终端可做，手机本地 Codex 有 Termux 适配路径；控制手机其他 App 则需要额外的设备控制能力。当前完成源码与文档核对，以及本机模拟器的 ADB 只读连通验证，尚未验证手机上的 Codex 安装、登录或推理。

## 现有 Waddle 桌面端架构

桌面源码现已整理至 `apps/desktop`，基准提交为 `643229a7b40739de17970593e916aaee18a857ba`，包版本为 `0.14.16`。

本地 `package.json`、`go.mod`、README 与相关实现显示：

| 部分 | Waddle 实现 | 对安卓项目的启发 |
| --- | --- | --- |
| 应用外壳 | Electron | 安卓改用原生应用外壳 |
| 界面 | React、TypeScript、Jotai | 借鉴终端、文件、AI 共用项目上下文 |
| 终端 | xterm.js；Go 侧 PTY | 显示、输入、会话执行分别建模 |
| 后端通信 | 自定义 wshrpc | 我们定义较小的会话接口 |
| SSH 持久会话 | remote job manager 与重连控制 | 会话存活与手机界面生命周期分离 |
| AI | 多模型聊天、终端上下文、文件工具 | 直接对接 Codex 的执行能力，不只做聊天框 |

关键代码位置：

- `frontend/app/view/term/termwrap.ts`：终端封装。
- `pkg/jobmanager/jobcmd.go`：通过 `pty.StartWithSize` 启动进程。
- `pkg/blockcontroller/durableshellcontroller.go`：远程持久会话启动与恢复；明确要求远程连接。
- `frontend/app/view/waveai/waveai.tsx`：AI 视图。

手机界面建议默认单终端全屏，下方提供 Ctrl、Esc、Tab、方向键，项目文件和 AI 以独立页面或抽屉出现。桌面的多块拖拽布局不宜直接缩小到手机。以上为产品建议，尚未做交互原型。

Waddle 的根 LICENSE 为 Apache-2.0；后续引入具体代码或依赖时分别核对相应许可和 NOTICE。本阶段仅克隆参考，没有复制实现。

来源：[Waddle 固定提交](https://github.com/wu-zhonghua/Waddle/tree/643229a7b40739de17970593e916aaee18a857ba)。

## 安卓 Terminal 的实现边界

一个真正的 Terminal 至少需要终端转义序列解析、屏幕缓冲、输入法处理、PTY、Shell 和会话生命周期。只显示命令输出的 TextView 不能覆盖交互程序的需求。

建议使用 Kotlin + Compose 构建界面。终端核心在原生组件与 WebView + xterm.js 间做小规模对比，重点验证中文输入、组合输入、选择复制、软键盘缩放、Unicode、长输出以及全屏 TUI。优先评估成熟实现，避免从零编写终端协议解析器。

执行后端分三个阶段：

1. **独立 App 的本地 PTY + 系统 Shell**：建立真实终端，但系统自带命令集有限，不等同于完整 Linux 开发环境。
2. **Termux 适配**：复用已移植的工具链和 Codex 包，是手机本地开发的优先验证路径。独立 App 不能直接读取 Termux 私有目录，必须通过明确的进程通信或服务桥接。
3. **SSH 远程执行**：连接 Mac/Linux 工作区，使用已有 Codex 和开发工具链；适合作为后备方案，但远程文件和手机本地文件必须明确区分。

Termux 提供 RUN_COMMAND Intent，可在授权后启动命令并回传结果；它本身并不是我们界面所需的双向、持续 PTY 字节流协议。若采用伴随 App 方案，还需实现带鉴权的本地桥接服务，负责输入、输出、resize、退出状态与取消。

Android 10 起，对 target API 29+ 的应用限制从可写私有目录直接 execve 二进制。完整运行环境的打包、包安装、target SDK 与分发方式需要单独验证，不能把桌面 Linux 二进制直接下载到 App 目录就视为完成适配。先用系统 Shell 和 Termux 路线验证，不预先承诺任意包管理能力。

来源：[Android 执行限制](https://developer.android.com/about/versions/10/behavior-changes-10)、[Android 应用沙箱](https://source.android.com/docs/security/app-sandbox)、[Termux RUN_COMMAND](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)。

## 安卓能否使用 Codex

| 模式 | 当前判断 | 需要补齐的验证 |
| --- | --- | --- |
| 手机 SSH 到电脑运行 Codex | 可行的工程路线 | 连接、终端兼容、断线恢复 |
| 手机 Termux 本地运行 Codex | 已有 Termux 官方包构建配方，值得优先验证 | 包镜像是否发布、安装、登录、命令执行、权限、后台行为 |
| 自己的 App 内嵌 Codex 运行时 | 可研究，工作量更大 | Android 编译、依赖与 helper、运行环境、权限、升级维护 |
| App 的 AI 面板接 app-server | 官方提供产品集成协议 | 在选定运行环境中验证协议、鉴权、审批、恢复 |

### 已确认的 Android 适配证据

Termux 官方包仓库的 `packages/codex/build.sh` 在调研时标注版本 `0.170.0`，依赖 git、libc++、openssl，推荐 ripgrep，排除 arm 和 i686 架构。构建还会安装 `codex-code-mode-host` 辅助程序，不能只拷贝一个 codex 文件就假定可完整执行命令。

仓库另有 Android 专用补丁：处理不支持 Android 的依赖、关闭默认 daemon 自动启动，并处理 V8 构建。这表明它是维护中的适配路径，不等于所有桌面功能都能直接工作，也不等于 OpenAI 对 Android 提供完整官方平台保证。

这次没有在目标设备执行安装，因此包配方的存在不等于已验证可用。下一步先用 `pkg search codex` 检查实际仓库，再安装并做隔离目录中的读写和命令测试。不把关闭全部保护作为默认安装步骤。

来源：[Termux Codex 配方](https://github.com/termux/termux-packages/blob/master/packages/codex/build.sh)、[Android 适配文件](https://github.com/termux/termux-packages/tree/master/packages/codex)。

### Codex 产品集成

OpenAI 官方提供 app-server，支持会话、流式事件、审批和认证等丰富客户端能力。我们可先用 CLI 完成终端内体验，再用它实现独立 AI 面板。模型推理通常仍通过网络服务完成；CLI 在手机运行不表示模型离线运行。

建议首个产品集成通过受控桥接进程与 app-server 的 stdio 通信。官方文档把 TCP WebSocket 传输标为 experimental and unsupported，且远程暴露需单独配置认证，因此不把裸露公网 WebSocket 当默认架构。客户端协议应固定到实际安装版本生成的 schema。

认证可以沿用 Codex 支持的登录方式。设备码登录目前标为 beta，受账号和工作区设置影响，适合验证手机浏览器回调不便的场景。不要把某种 ChatGPT 账号的可用性推断为所有账户均可用。

来源：[Codex CLI](https://learn.chatgpt.com/docs/codex/cli)、[Codex App Server](https://learn.chatgpt.com/docs/app-server)、[认证说明](https://learn.chatgpt.com/docs/auth)。

## Codex 能否操作安卓手机

“手机使用 Codex”和“Codex 控制手机界面”是两个方向。

**开发阶段，电脑上的 Codex 可以通过设备工具操作已授权的模拟器或真机。** ADB 提供 shell、安装、日志等能力；UI Automator 提供跨应用界面操作和截图能力。Google 当前还提供包含 Android CLI 的 Codex 插件，覆盖虚拟设备管理、实时 UI 布局检查和自然语言测试流程。本次只调研该插件，没有安装它。

本机实际检查：

- Android SDK 位于 `/opt/homebrew/share/android-commandlinetools`。
- ADB 已连接 `emulator-5554`，状态 `device`。
- 实际读取系统版本为 Android 15，ABI 为 `arm64-v8a`。
- `pm path com.termux` 没有返回 APK 路径，当前默认用户下未发现 Termux 安装。
- 没有连接或操作用户的实体手机，没有执行 UI 点击或安装应用。

**产品阶段，让手机上的 Agent 控制其他 App，需要额外的设备工具层。** 可以先用开发环境的 ADB/UI Automator 验证观察、动作、再观察的闭环；面向普通用户的本机方案则需要单独评估授权机制与平台支持。AccessibilityService 具备读取界面和派发手势能力，但官方将它定位为辅助功能服务，不能未经评估就作为通用 Agent 的默认方案。

普通 Terminal 进程仍受应用 UID 沙箱限制，不能因为有 Shell 就自动获得其他 App 的私有数据或全机控制权限。首版把“开发调试我们自己的 App”与“终端运行 Codex”做好，通用手机 Agent 作为后续独立能力。

来源：[ADB](https://developer.android.com/tools/adb)、[UI Automator](https://developer.android.com/training/testing/other-components/ui-automator)、[Google Agent 工具](https://developer.android.com/tools/agents)、[AccessibilityService](https://developer.android.com/guide/topics/ui/accessibility/service)。

## 推荐决策

项目统一命名为 Waddle，覆盖手机和电脑终端；建议同仓库、分端构建。先做 Waddle 的原生安卓客户端，并尽早验证 Termux Codex。终端的显示与执行后端分离，保留本地系统 Shell、Termux 和 SSH 三种适配空间。产品内 AI 面板在 CLI 跑通后接 app-server。设备控制先服务于开发和测试，不把它误当成终端自带能力。

尚待确定的产品条件：目标手机与 Android 版本、是否接受依赖 Termux、是否需要 Google Play 分发、是否必须完全脱离电脑。这些条件会影响完整本地运行环境的最终方案，但不阻碍本地 PTY 原型和 Codex 可行性验证。

项目命名和仓库组织方向见 [项目说明](../README.md)。现已沿用原仓库历史，将桌面源码整理至 `apps/desktop`，安卓端位于 `apps/android`。本文中的“尚未实现”等表述反映调研时状态，首版进度以根 README 和验证记录为准。
