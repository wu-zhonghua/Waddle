# Waddle 安卓端与 Codex 技术调研

调研日期：2026-11-19。当前实现为 **Waddle 0.4.0 内嵌运行时与亮度修改工具**，已移除 0.2.0 的 Termux 伴随 App 依赖。下面先记录当前验证，再保留旧阶段的历史证据；旧版权限和连接步骤不适用于当前版本。

## 阶段 2 三星 Fold7 真机验证（2026-11-19）

用户明确要求继续真机测试后，在 SM-F966B（设备报告 Android 17 / API 37，ARM64，4 KB 页）保留数据安装阶段 1 包。已复用真机 Waddle 自己的登录，按用户要求非必要不登出。15 项实际执行的设备测试通过，另 2 项因运行条件跳过；面板及终端真实模型修改已有文件、执行命令、读取设置及原生亮度确认/拒绝均通过。

用户亲自观察并确认手动亮度 64 → 160 明显变亮；恢复原始值 103 并开启自动模式后，遮挡/移开光线传感器会自动调整。只读采样期间模式始终为 1，亮度出现 35、102、65、105、174。与模拟器不同，真机自动模式的数值会随环境变化。结束时保留自动模式、关闭 Waddle 写入开关，保持 Codex 登录和当前会话。

安装时还发现三星已有 DUAL_APP 分身用户 95：未限定用户的 ADB 安装使 Waddle/测试包也注册进该空间，产生副本。已保留数据移除分身内两份尚未启动的注册；测试结束移除主用户的临时测试包。最终只有主用户 0 安装 Waddle。后续开发安装/测试命令明确使用 `--user 0`，不删除其他分身应用或空间。详细结果、差异、状态恢复及验收边界见[实现说明](dev/2026-11-19-01-codex-in-waddle.md#阶段-2-真机验证结果2026-11-19)。以下为较早阶段的历史记录。

## 阶段 1 补齐验证（2026-11-19）

本轮按 [2026-11-19-01 需求](dev/2026-11-19-01-codex-in-waddle.md) 只处理 API 35 模拟器，真机保持暂停。模拟器当前用户移除 Termux 注册并保留旧数据后，Waddle 独立运行回归继续通过。

实际停止测试暴露了两项问题并已修复：启动中未取得 turn ID 时停止会丢失；收到停止回执后，部分 shell 后代仍会执行。现在保留早期停止请求，停止活动任务时回收该面板的进程树并恢复原会话。Waddle 自有 supervisor 使用 child subreaper 清理独立会话的后代；测试等待超过原脚本时长，未出现停止后的哨兵写入。真实后台返回、旋转重建、同一 thread 上继续回忆测试口令通过。

新增/强化的自测还包括：修改已有文件、终端 CLI 与面板复用登录、五项系统设置逐项比对、独立子会话回收。终端 `codex exec` 写入测试显式使用 `--sandbox workspace-write` 和 `--ask-for-approval on-request`，核对实际文件和 `cmp`，而非只检查退出码。设备用例增加到 17 项，分授权前后与定向重测覆盖；Android 145 单元测试、桌面 200 测试、TypeScript 和开发构建通过。详细日志对应关系、失败后的修正与当前改动尚未运行 GitHub CI 的限制，见需求文档的实现说明。

系统界面核对发现 raw brightness=102 对应系统滑块 83%，不能线性换算成 40%；超时原始值 2147483647 ms 对应系统选项摘要“Not set”。Waddle 输出原始值，工具说明明确这些差异。下面保留较早的 0.4.0 亮度验证及 0.3.0 历史记录，设备连接/Termux 状态以本节为准。

## Waddle 0.4.0 系统亮度修改验证

新增 `waddle_set_display_setting`，只接收亮度原始值 1–255，或模式 0（手动）/1（自动），每次调用只修改一个字段。通过 Waddle 自己的 `Settings.System` API 写入，再读回核对。执行位置仍是 Waddle 普通应用 UID 10168，不依赖 Termux、root 或 ADB shell 写入。ADB 仅用于安装、操作测试界面与独立核对。

Codex 页提供“系统授权”入口，打开 Android 的“修改系统设置”页面；返回后需开启“允许修改亮度”，模型请求再弹出逐次确认。拒绝、关闭开关、缺少系统授权、请求过期或确认期间相关值已变化时，本次不写入。固定亮度只在手动模式下接受，不会暗中关闭自动亮度。旧会话保留原有工具列表，升级后请新建会话使用新增工具。

Android 15 ARM64 模拟器 `emulator-5556` 实测，Termux 保持 disabled-user，使用 Waddle 已有登录：

| 项目 | 实测结果 |
| --- | --- |
| 真实模型设置亮度 | 102 → 96，确认前保持 102，确认后原生读回与独立系统读取一致 |
| 真实模型切换模式 | 手动 → 自动 → 手动，每次分别确认并核对 |
| 拒绝模型请求 | 原值不变，成功写入计数不增加 |
| 关闭开关后确认旧请求 | 待确认请求取消，旧确认无法写入，原值不变 |
| 缺少系统授权 | 授权前验证 `plan` 和 `apply` 均拒绝，原值不变 |
| 参数与过期确认 | 拒绝其他设置、额外字段、非法值，以及确认期间已变化的亮度 |
| 实际界面流程 | 点击示例，弹窗显示 102 → 128；确认后变为 128，再让 Codex 恢复 102，界面结果与系统值一致 |
| 测试结束状态 | 已恢复原始亮度 102、手动模式 0，并关闭 Waddle 修改开关 |
| 构建与回归 | 145 单元测试通过，组装与 lint 通过；15 项设备测试分授权前/后覆盖通过，包含已有真实模型文件与命令任务 |

测试代码见 [DisplaySettingsIntegrationTest](../apps/android/app/src/androidTest/java/dev/waddle/android/DisplaySettingsIntegrationTest.kt)。修改设置与真实模型测试需显式参数启用，`finally` 恢复原始亮度和模式。授权后的完整运行有 1 项“缺少权限”测试跳过，该项在授权前单独环境已通过。日志、公开结果 JSON 与确认/结果截图只保存在被忽略的 `artifacts/display-settings`，不提交原始设备输出或凭据。

本次证明了系统设置值能被真实模型通过 Waddle 修改，不证明实体手机的光线传感器或厂商自动调光算法有效。当前没有连接真机；下一步在用户连接的 ARM64 手机验证安装、独立登录、终端文件/命令、亮度实际变化与自动模式。Android 的系统授权范围比此工具更广，这里的确认机制约束此原生工具，不是同一 UID 内任意代码的隔离边界。

## Waddle 0.3.0 独立运行验证

按用户要求，Codex 0.170.0、code-mode helper、Bash、Git、ripgrep、curl、动态库、CA 证书与终端数据随 Waddle APK 打包。Waddle 直接启动自己的子进程，使用 stdio 通信；清除 RUN_COMMAND 权限、Termux 包查询、启动 Intent、WebSocket 监听及外部配置目录选择。终端与 Codex 均使用 `files/home`，配置位于同一个 `files/home/.codex`。

执行文件由 Android 安装到 APK 的 nativeLibraryDir，home 中只有指向它们的命令软链接。保留 target SDK 35，通过重命名库依赖、调整 RUNPATH、helper 名称和环境变量适配。完整改动与包校验值见[运行时来源记录](../apps/android/runtime/PROVENANCE.md)。这是重新打包 Android 原生工具，并非复制 Termux App、搬移账号目录或降级 SDK。

测试在 Android 15 ARM64 模拟器进行：`emulator-5556` 上 Termux 已停止并设为 disabled-user，保留原数据；另启动 AVD 的临时只读实例 `emulator-5558`，确认未安装 Termux 后执行最终干净安装回归。没有操作实体手机。Waddle 真实 PTY 输出确认：

| 项目 | 证据 |
| --- | --- |
| 工作目录 | `/data/data/dev.waddle.android/files/home` |
| UID / SELinux | Waddle UID 10168，`untrusted_app`；不是 Termux UID 或 ADB shell |
| 本地工具 | Codex 0.170.0、Git 2.56.1、ripgrep 15.2.0、Bash、curl 均启动成功 |
| 基础文件工作 | Git init/add/status 与 rg 搜索自己的测试文件通过 |
| 网络工具 | Waddle 终端 curl HTTPS 返回 200；Git HTTPS ls-remote 得到仓库 HEAD |
| Codex 协议与执行 | stdio initialize 与 command/exec 通过；后者输出 Waddle UID/home、退出 0 并创建测试文件；终端可读取该文件 |
| 执行 helper | 内嵌 code-mode helper 能独立启动并返回帮助 |
| 进程退出 | 设备测试中停止后 supervisor 正常退出 |
| 最终设备回归 | 无 Termux 的临时实例上 10 项通过；独立登录后另运行真实模型测试 1 项通过。合计 11 项，覆盖网络、stdio、PTY 和模型工作流 |
| PTY 快速退出 | 修复 waitpid 先于输出读取完成时丢最后输出的竞态；新增连续 12 次快速退出测试通过 |
| 单元与构建 | 145 个单元测试通过；assembleDebug 和 lintDebug 通过（仍有警告） |
| Waddle 独立登录 | 用户完成新设备码授权，Waddle 显示已就绪；登录状态在重连后保留 |
| 真实模型读取设置 | 模型实际调用 Waddle 原生工具，工具执行计数、来源和结果断言通过 |
| 真实模型文件任务 | 读取 input.txt 并创建 result.txt；独立断言内容为 WADDLE_SHARED_OK |
| 真实模型执行命令 | 命令事件记录 completed，包含 cat、cmp、pwd、id -u；输出 Waddle 测试目录及 UID 10168 |
| 共享终端 | PTY 中 cat 读到面板模型创建的文件；直接运行 codex exec 也完成独立模型文件任务，复用同一登录 |
| 会话生命周期 | Activity 重建保留同一 controller/thread；断开后恢复同一 thread 与历史消息 |

用户完成 Waddle 独立登录后，通过已安装的 instrumentation APK 运行 `liveWaddleConnectsAuthenticatesAndCallsNativeSettings`，真实测试 1/1 通过（23.194 秒）。这轮调用的 CLI、helper、命令和文件全部属于 Waddle；Termux 仍为 disabled-user。模型在新建 `model-probe-*` 目录执行 `cat input.txt > result.txt`、`cmp input.txt result.txt`、`cat result.txt`、`pwd` 和 `id -u`。模型输出、独立文件断言及真实终端读取相互验证；测试目录随后自动清理。

Waddle 的登录缓存由内嵌 Codex 自己使用，未复制或读取 Termux 认证文件，也没有把令牌写到文档或日志。另外从 Waddle 的真实 Bash PTY 直接运行 `codex exec --json`，复用了相同登录并完成另一轮模型请求。CLI 的 `command_execution` 事件记录了 `cp input.txt result.txt`、`cmp input.txt result.txt` 和 `id -u`，命令退出 0、UID 为 10168；CLI 整体退出码也为 0，独立 `cmp` 检查通过，结果文件为 `WADDLE_CLI_OK`。通过管道向 Bash 注入测试脚本时显式使用 `codex exec </dev/null`，避免 CLI 消耗脚本后续检查语句。界面手动查询也返回了亮度 102、手动亮度和飞行模式关闭。真实测试日志及界面截图保存在被忽略的 `artifacts/embedded-runtime`。

原生设置工具仍限定只读五个公开字段。之前的 Android 工作区隔离风险仍需保守对待；本次搬入同一 UID 不建立额外的 Agent 文件隔离。未验证真机、API 26、16 KB 页设备、长期后台保活和完整审批/取消流程；也未包含任意包管理器、SSH、Python/Node/编译器生态。默认交付 ARM64，另提供 x86_64 包锁和 CI 构建路径，其包下载、校验、转换和 supervisor 编译已通过；未在本机运行 x86_64 设备测试。

## Waddle 0.2.0 集成结果（历史，已被替换）

用户明确授权后，仅在当前测试模拟器 `emulator-5556` 开启 Termux `allow-external-apps` 并授予 Waddle `RUN_COMMAND` 权限。通过设置已有 Codex 配置目录复用设备登录状态，没有复制或提取账号令牌。Waddle 的本地终端与 Termux Codex 工作目录仍相互独立。

Waddle 通过公开 RUN_COMMAND Intent 启动 `127.0.0.1` 上的 app-server。每次连接生成随机 256 位令牌，仅将 SHA-256 校验值传给服务端；客户端通过 Bearer 认证连接。WebSocket 和 dynamic tools 属于实验性协议，本版固定适配 0.170.0。客户端提供流式对话、设备码登录入口、停止、审批、断开与恢复；前台服务持有会话，Activity 重建不会新建运行时。

原生工具 `waddle_read_system_settings` 仅允许亮度、亮度模式、屏幕超时、飞行模式、辅助功能启用状态五个字段，通过 Waddle 自己的 Android `Settings` API 读取。它不修改设置，不接收任意 URI，不读取其他 App 界面；每次调用验证字段、当前 thread/turn 和界面开关。

| 检查 | 实测结果 |
| --- | --- |
| Waddle 启动并连接手机本地 Codex | 成功，复用原有已登录配置 |
| 未携带令牌连接同一端口 | 认证拒绝 |
| 模型调用 Waddle 原生设置工具 | 成功，工具执行计数及真实结果均通过断言 |
| Waddle 可见界面中的查询 | 亮度 102、手动模式、飞行模式关闭，随后得到 Codex 中文回复 |
| Activity 重建 | 同一个 controller 与 thread 保留 |
| 断开后重新连接与恢复 | 同一个 thread 及历史消息恢复 |
| 非法工具参数、配置路径 | 拒绝未知字段、额外参数、重复字段和路径注入等输入 |
| Gradle 验证 | 145 个单元测试、9 个设备测试全部通过；assembleDebug、lintDebug 通过，仍有 lint 警告 |

Waddle 中的新设备码登录界面、审批接受/拒绝与主动取消已实现，但本轮没有完成这些交互的真实端到端验证；登录复用了前一阶段已完成的设备码授权。历史隔离探测仍然有效：不能将 Android 上的 `workspace-write` 当成可靠目录边界，也不能保证所有命令都会弹出审批。设置开关仅控制原生设置读取工具。真机、其他 Android 版本、后台长期运行及运行时内嵌仍待验证。

测试代码见 [CodexIntegrationTest](../apps/android/app/src/androidTest/java/dev/waddle/android/CodexIntegrationTest.kt)，操作说明见 [Android README](../apps/android/README.md#use)。日志、截图和 APK 仅保存在被忽略的 `artifacts` 目录。以下各节保留前期可行性验证与调研过程。

## Android 本地 Codex 实测

使用现有模拟器的临时只读实例 `emulator-5556`，没有连接实体手机。Termux 为官方 GitHub 发布的 `v0.118.3` ARM64 Debug APK，下载后与同次发布的 sha256sums 核对，SHA-256 为 `72fdb596045116bf5ba1b5bdf5b26fddb9acc0bd074ad9f2da9eb0ae85e83a4e`。Codex 来自 Termux 的签名 APT 仓库 `https://packages-cf.termux.dev/apt/termux-main`，实际安装版本 `0.170.0`，包括 `codex-code-mode-host`，另安装 ripgrep。

测试脚本从 Termux 的可见终端执行；ADB 仅用于安装、输入命令和提取测试结果。实际进程为普通应用 UID `u0_a157`，SELinux 域 `untrusted_app_27`，不是 ADB shell 或 root 身份。Termux 运行环境的可执行文件路径依赖其应用包名，不能直接复制到 Waddle 私有目录作为正式集成。

| 检查 | 实测结果 |
| --- | --- |
| `codex --version`、帮助与协议 schema 生成 | 成功，版本 0.170.0 |
| app-server stdio `initialize` | 成功，返回 `platformOs: android` |
| `account/read` 与设备码登录 | 初次返回未登录；随后用户完成设备码授权，CLI 确认登录成功 |
| app-server `command/exec` 运行 `id`、`pwd` | 成功，命令实际在 Termux 应用 UID 下执行 |
| `readOnly` 请求写入测试文件 | **写入成功，不具备预期只读隔离** |
| `workspaceWrite` 请求写入工作区内文件 | 成功 |
| `workspaceWrite` 请求写入工作区父目录测试文件 | **写入成功，不具备预期目录边界** |
| `codex sandbox` | 返回当前操作系统不支持 |
| 真实模型请求、模型驱动读写与命令执行 | 成功：读取任务及脚本，修复拼写，运行检查，Codex 与独立检查均退出 0 |
| 会话恢复 | 成功：恢复同一 thread，准确记住上轮修改，再运行检查通过 |
| 审批拒绝、取消、模型工具的越界阻断 | 尚未验证 |

全部哨兵文件位于新建测试目录中。未使用 `danger-full-access`、`--dangerously-bypass-approvals-and-sandbox`，也未修改系统安全设置。上述隔离结果直接证明的是 app-server `command/exec` 路径；后续模型任务成功证明了工作区内读写能力，但没有验证模型工具的越界阻断。Android 应用 UID 沙箱与 Codex 的工作区隔离是不同边界。

### 真实模型任务与恢复

用户在浏览器中完成 ChatGPT 设备码授权后，仍从 Termux 可见终端启动 CLI。在独立 `model-workspace` 内准备三个文件：`TASK.txt` 定义任务、`greeting.sh` 初始输出 `Helo Waddle`、`check.sh` 要求输出严格等于 `Hello Waddle`。测试显式保留 `--ask-for-approval on-request --sandbox workspace-write`，通过 `codex exec --json --skip-git-repo-check` 提交任务；这些选项不消除上述 Android 隔离限制。

实际事件记录包含文件读取、修改 `greeting.sh` 和执行 `/system/bin/sh check.sh`，检查输出 `WADDLE_ANDROID_CODEX_TEST_OK`。Codex 退出 0，测试脚本再次独立执行检查也退出 0；提取实际文件后确认拼写已修正，任务和检查脚本未变。

随后通过 `codex exec resume <thread-id>` 恢复同一会话，要求不修改文件、回忆上轮改动并再次执行检查。恢复结果正确指出补上了 `Helo` 缺失的 `l`，检查再次通过，恢复命令退出 0。此处模型推理由联网服务完成，文件读写与 shell 命令在 Android 的 Termux 应用进程内执行。

两轮 JSONL 事件及独立检查结果保存在本地被忽略目录 `artifacts/codex-android/model-validation`。没有提取认证文件、把一次性码写入项目文档或复制电脑登录缓存。

可复现脚本：[probe-codex-termux.sh](../apps/android/tools/probe-codex-termux.sh)。在一次性 Termux 环境安装 Codex 后，以 `bash probe-codex-termux.sh` 执行。脚本使用新的独立 Codex 配置目录，不登录、不请求模型、不开放网络监听；退出码 2 表示能执行但检测到工作区隔离不生效。本次脚本已在 Android Bash 中复测。原始日志、公开协议 schema 和截图保存在被忽略的 `artifacts/codex-android`，不提交设备原始输出。

当时的集成建议是通过受认证的桥接层连接 Android 本地 app-server，展示流式输出、审批和恢复会话。现已采用仅监听 localhost、带认证的 WebSocket 完成 0.2.0 集成，详见上节。不能将 `readOnly` 标签当成已验证的安全保证。内嵌运行时还需单独解决目标 SDK 执行限制、固定路径、依赖和升级问题。

本次用户选定的 ChatGPT 设备码登录已成功；其他账号或工作区仍需允许该方式。凭据仅留在测试设备私有目录，不写入仓库。CLI 和工具本地运行不表示模型离线运行。

来源：[Termux 发布](https://github.com/termux/termux-app/releases/tag/v0.118.3)、[Codex 包配方](https://github.com/termux/termux-packages/blob/master/packages/codex/build.sh)、[官方 app-server 文档](https://learn.chatgpt.com/docs/app-server)、[官方认证文档](https://learn.chatgpt.com/docs/auth)。以下部分保留早期调研背景，当前验证状态以上表为准。

## Android 读取能力实测：文件、设置与界面

在同一 Android 15 ARM64 模拟器上，由已登录的 Codex 执行固定只读脚本 [probe-android-access.sh](../apps/android/tools/probe-android-access.sh)。工具子进程仍为 Termux 的普通应用 UID，没有 root、ADB shell 身份或新授予的系统权限。没有读取账号、认证文件、联系人、照片内容，也没有修改系统设置。

| 对象 | 当前 Codex + Termux Shell 的结果 |
| --- | --- |
| 自己的测试文件 | 可读取 |
| Android 版本、CPU 架构、时区 | 可读取：15、arm64-v8a、Asia/Singapore |
| 公开系统文件 `/system/etc/mkshrc` | 可读取；不代表任意系统文件都可读 |
| 亮度、自动亮度、飞行模式、辅助功能启用状态 | `settings get` 被拒绝；显式选择应用所属 Android 用户后仍被拒绝 |
| 通过 `content` 命令读取亮度 | 修正 PATH 后，明确报缺少 `ACCESS_CONTENT_PROVIDERS_EXTERNALLY`；命令退出 0 也不代表读取成功 |
| `/sdcard/Download` | 当前未授予共享存储访问，返回 Permission denied |
| 另一个 App（Waddle）的私有测试文件 | 已独立确认存在；Codex 实际读取返回 Permission denied、退出 1 |
| `/data/system` | Permission denied |
| 当前窗口诊断信息 | `dumpsys window` 返回 Can't find service: window，未取得界面内容 |
| 查询 Waddle 包信息 | `pm list packages` 在本次环境返回空，不能据此判断应用不存在 |

首次跨 App 文件探测时 Waddle 已不在模拟器，返回的是文件不存在，不能作为权限证据。后续安装本地 Waddle Debug APK，仅创建公开字符串样本 `files/access-probe-public.txt`，由 Waddle 自己的 UID 确认可读取，再让 Codex 单独读取同一路径，才得到有效的 Permission denied 结果。

### 原生 API 与 Shell 命令不同

额外新增 [AndroidReadAccessTest](../apps/android/app/src/androidTest/java/dev/waddle/android/AndroidReadAccessTest.kt)，用 Gradle wrapper 在普通 Waddle 应用 UID 下读取公开设置。测试确认没有 DUMP、WRITE_SECURE_SETTINGS、INTERACT_ACROSS_USERS 权限，也未采用 shell 身份。通过 `Settings.System/Global/Secure.getString` 成功读到：亮度 102、手动亮度模式 0、屏幕超时 2147483647 ms、飞行模式 0、辅助功能启用状态 0。该设备测试 1/1 通过。

这证明**一些系统设置可以通过普通 App 的原生 API 读取**，不能从 Shell 命令失败推断所有设置不可读。该独立测试完成时尚未接入 Codex；现已在 0.2.0 中将上述五个字段接入 `waddle_read_system_settings`，并验证真实模型调用。读取设置值、修改设置、读取设置页面的画面是三种不同能力，本次只实现和验证了前者。

Android 的跨应用 UID 沙箱仍然阻止 Termux 读取 Waddle 私有目录。这与前面 Codex `readOnly/workspaceWrite` 的工作区边界未生效并不矛盾：前者由 Android 管理不同 App，后者应限制同一 App 内 Agent 的文件活动范围。

原始输出位于被忽略的 `artifacts/codex-android/access-validation`；原生设置测试日志为 `artifacts/codex-android/native-settings-test.log`。来源：[Android Settings.System 读取 API](https://developer.android.com/reference/android/provider/Settings.System#getString(android.content.ContentResolver,%20java.lang.String))、[应用私有存储边界](https://developer.android.com/training/data-storage/app-specific)。

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
