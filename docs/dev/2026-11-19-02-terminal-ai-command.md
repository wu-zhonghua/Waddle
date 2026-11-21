# 终端显示当前路径，并用 Codex 生成待执行命令

- 状态：验收不通过
- 日期：2026-11-19
- 上一份：无（基于 2026-11-19-01 已实现能力）
- 涉及：安卓

## 现在的样子

Waddle 已有本地 Bash 终端和独立 Codex 面板，两处共用登录。终端提示符固定为 `waddle $`，看不出当前路径；想请 Codex 帮忙写命令时，要切换页面，当前终端会话也没有自动作为上下文。

## 要做什么

终端提示符前面显示当前路径，切换目录后立即更新。终端底部提供可展开的 AI 对话输入框，用户直接问“去哪里”或“该打什么命令”；Codex 根据当前终端会话的路径、近期输入输出和用户问题，给出可编辑的命令及简短说明。支持很长的命令和多行 Bash 内容。

生成结果先展示，用户检查或修改后按回车一键运行。命令在原来的终端会话中执行，结果仍显示在终端里，`cd` 等操作会改变该会话的实际状态。沿用已有 Codex 登录，非必要不登出。

## 不做

- 桌面端修改、远程 SSH、额外的包管理器或开发环境。
- AI 生成时自动执行建议，或自动代替用户处理终端里的交互程序。
- 默认附带其他终端会话、账号文件或整部手机内容。

## 怎样算做好

- 在安卓模拟器和 Fold7 上，提示符能看见当前路径；新建会话、`cd` 到含空格/中文的目录、在多个会话间切换时显示正确。
- 底部可以展开输入自然语言、查看本次使用的路径和近期终端上下文，生成、编辑或取消建议。生成期间不执行建议命令、不修改工作文件。
- 建议与当前会话有关；未登录或连接失败时能看懂原因，已有登录可直接复用。
- 单行、长命令和多行 Bash 内容都能预览和编辑；准备好后按回车（或手机键盘执行键/运行按钮）只执行一次，并在原会话看到输出和目录变化。
- 切换或关闭会话、目录已变化、前台程序仍在运行时，不会把过期建议误送给别的会话或交互程序；取消生成不会影响其他终端与 Codex 面板。
- 更新保留用户文件和 Codex 登录；Android 构建及相关终端回归通过，实现说明记录边界与试用步骤。

---

## 实现说明（实现者填写）

已实现，等待验收。2026-11-19 完成 Android 15 / API 35 ARM64 模拟器验证，2026-11-20 完成 Fold7 的 13 项真机自动化测试（首次受锁屏影响的 3 项解锁后重跑通过，详见下方）。没有清除应用数据、注销 Codex、提交或推送代码。

### 使用方式

1. 打开「终端」，默认提示符显示 `~ $` 或 `~/目录 $`，上方显示完整当前路径。以前自动生成的固定 `waddle $` 配置会迁移；用户自己修改过的 `.waddlerc` 原样保留。
2. 点底部「✦ 问 Codex，写命令…」，输入自然语言，例如「进入刚才列出来的项目，查找所有 TODO」。可展开查看本次的路径和最近终端内容。
3. 点「生成命令」。沿用已有登录，生成期间不执行命令；结果包含命令和简短说明。信息不够时显示需要补充的内容。
4. 可编辑单行或多行命令，然后按硬件回车、软键盘执行键或「运行命令 ↵」。Shift+回车换行。执行后回到终端，输出、目录和变量留在同一 Bash 会话。
5. 取消/停止生成只结束这个建议请求。切换或关闭会话、离开抽屉也会取消；目录/提示符状态改变后要求重新生成。前台程序、`read` 等交互输入和未完成的引号/多行输入不会接收建议命令。

### 实现与边界

- `TerminalWorkspace` / `ShellIntegration`：默认 `PS1='\w \$ '`；每会话私有初始化脚本保留用户配置，使用带随机标识的 OSC 777 通知记录提示符代次。当前路径读取 shell 的实际 cwd；PTY 的 `tcgetpgrp` 确认前台进程组。Vendored 终端的改动已登记到 provenance。
- 执行时核对会话 ID、cwd、提示符代次和前台状态，再让 Readline 绑定替换未提交的输入并接受命令；Bash 内部还会复核 cwd/代次。命令保存为每次独立的私有脚本，在原 shell 中 `source`，避免长文本经过 PTY 逐字粘贴、保留 `cd`/export 状态。旧历史不会指向被新建议覆盖的脚本；会话关闭后清理这些临时脚本。
- `CommandSuggester`：单独的 app-server 进程、临时线程，复用 Waddle 的 `CODEX_HOME` 和登录，不接入普通 Codex 面板的动态工具。固定适配 0.170.0，在 thread/start 与 turn/start 都显式传入 `environments: []`；关闭 shell/code-mode、agents、apps、plugins、hooks 和 web search。自定义 MCP 启用时拒绝进入建议模式；客户端工具/审批请求也拒绝。不是依赖 Android 上不可靠的 read-only 文件隔离来阻止执行。
- 核对了固定版本的 ThreadStart/TurnStart 协议和环境工具解析源码：空环境列表禁用执行环境，文件补丁等工具没有可解析的环境会失败。接口参考：[官方 app-server 文档](https://learn.chatgpt.com/docs/app-server)。升级 Codex 时必须重新核验这些实验性字段。
- `CommandSheet`：底部抽屉，可查看上下文、生成、编辑、取消与执行。只附带当前会话的最近 12,000 字符；不主动读取其他会话、环境变量或账号文件。Codex 自身仍可正常更新登录缓存/运行日志。模型推理需要联网。
- 建议和编辑后命令上限为 UTF-8 64 KiB；拒绝换行/Tab 之外的控制字符。路径中的中文、空格受支持；路径名含换行暂不支持。Emacs/Vi Readline 均已覆盖；关闭 Readline 时停用建议执行。用户自定义或后续覆盖 shell 集成函数/按键绑定的配置不属于本轮保证范围。
- 命令编辑框关闭键盘自动纠错，避免输入法替换 Bash 文本；生成按钮使用终端配色。最后这两项 UI 调整后重新构建/lint，并重跑软键盘 Go 真实模型测试，通过（`build-ime.log`、`emulator-ime.log`）。
- 抽屉是临时 UI，Activity 重建时会关闭并取消生成；终端会话和 Codex 登录不因此清空。本轮没有修改桌面端或系统设置工具。

### 验证记录

- `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug` 通过；145 项终端组件单元测试无失败。
- 最终设备测试运行 `CommandSuggestionIntegrationTest`、`TerminalIntegrationTest`、`CodexIntegrationTest`：JUnit 报告 18 项，17 项实际执行并通过；1 项原有完整 Codex/设置真实模型测试未启用而跳过。本次新增的 7 项全部执行通过，其中 3 项启用真实模型。
- 新增覆盖：旧配置迁移且不覆盖自定义配置；中文/空格 cwd；约 16 KB 多行脚本及 heredoc；同一 shell 保留 cd/export；Vi 模式下替换光标位于中间的未提交输入；重复调用只执行一次；切换/关闭会话、改变 cwd、sleep 前台进程、read 内建命令、续行提示符和关闭 Readline 的拒绝路径；取消生成后其他终端仍可用、登录文件仍在。
- 真实模型从终端输出取得随机标记并生成命令。即使请求要求先用工具写文件，生成阶段仍未创建目标文件/目录、未改变终端路径或提示符代次；执行后文件内容和新 cwd 验证正确。
- 实际 UI 测试从底部入口展开、输入问题、调用 Codex、编辑成多行命令，分别通过硬件 Enter 和软键盘 Go 执行；验证只写入一次，并在原会话保留目录和变量。
- 构建/测试日志在忽略目录 `artifacts/terminal-ai/`：`build-final.log`、`emulator-final.log`。APK 为 `waddle-android-0.4.0-ai-command-debug.apk`，SHA-256：`e2f1d648e9f9a28c18f577b2098154605cdb1aeef48dfd5685fba29f1f7c001c`。未把凭据、原始设备内容或构建产物加入版本控制。
- **2026-11-19 当时待补测：Fold7 真机运行和用户验收；2026-11-20 自动化补测已完成，用户验收仍待进行。** 真机安装须限定 `--user 0 -r`，保留主应用数据与登录，避免再次安装到三星分身用户。之前亮度测试通过不等于本功能已通过真机测试。


### 2026-11-20 真机补测与退出排查

- 已识别三星 Fold7 / SM-F966B，系统报告 Android 17 / API 37。用当前源码重新构建，assemble、lint 与单元测试检查通过（145 项单元测试报告，本轮 Gradle 复用已有结果）。APK SHA-256 与上次最终版本相同。
- 已通过 `install --user 0 -r` 更新主用户 Waddle 和临时测试包；未清除数据、未注销登录。检查 user 95 没有 Waddle 或测试包，未产生应用分身。
- 本轮执行 `CommandSuggestionIntegrationTest` 和 `TerminalIntegrationTest`，开启 `waddleLiveSuggestions=true`：共 13 项，10 项通过，3 项未通过。原始日志在忽略目录 `artifacts/terminal-ai-phone-2026-11-20/device-tests.log`，构建日志为同目录 `build.log`。
- 通过项目包括：提示符配置迁移、中文/空格路径、约 16 KB 多行脚本、cd/export 保留、重复执行防护、前台程序与过期建议拒绝、取消生成、真实 Codex 使用终端上下文且生成阶段不执行、PTY/UTF-8、Ctrl-C、快速退出输出、resize 和独立会话。
- 3 项 UI 测试尚需重跑：硬件 Enter、软键盘 Go、输入法与 Activity 重建。前两项无法点击 AI 入口；后一项 Activity 重建时停在 STOPPED，未进入 RESUMED。同期系统窗口检查显示焦点为 NotificationShade，Waddle 未取得前台窗口。已请用户解锁、收起通知栏并保持 Waddle 在前台；此时不能把这些 UI 项目记为通过，也尚未据此认定产品代码缺陷。
- 用户解锁并将 Waddle 放到前台后，仅补跑上述 3 项，15.087 秒全部通过，日志为 `ui-retest.log`。因此本轮 13 个不同测试均已通过；真实模型复用了既有登录。正式验收状态保持待验收。
- 用户随后反馈“不断闪退”。检查 `dumpsys activity exit-info dev.waddle.android`：这几次退出的 reason 均为 USER REQUESTED / FORCE STOP，description 分别为 `due to start instr`、`due to finished inst`；崩溃缓冲区未发现 Waddle 的 Java/native 崩溃堆栈。这解释了测试期间的退出/重开现象，未据此改动应用业务代码。
- 已结束 instrumentation，并仅卸载主用户下的 `dev.waddle.android.test`，再正常启动主应用。主用户只保留 Waddle，user 95 仍无 Waddle；登录与文件保留。后续用户交互验收期间不再启动会强制停止目标进程的 instrumentation。
- 正常打开后持续观察，Waddle 始终为同一进程并保持前台，未新增退出记录；证据为忽略目录中的 `normal-launch-check.json` 和 `waddle-exit-info.txt`。用户随后实际操作并确认「现在正常，不再退出」，本次反复退出问题已确认消失；这不替代整个功能的正式验收。
- 今后真机自动化必须明确说明：instrumentation 启停会结束目标进程，ActivityScenario 测试还会关闭/重建页面；执行后清理临时测试包并正常重开应用。用户手动体验期间使用不会重启应用的观察方式。


## 验收记录（验收者填写）

- 验收环境（2026-11-21，Claude 验收）：Mac mini 上的 Android API 35 ARM64 模拟器 `Waddle_API_35`（emulator-5554，Waddle 专用那台，当时已在运行；`Youzai_API_35` 没有启动）。验收用的 APK 与 Fold7 上现装的版本相同（SHA-256 `aef51d2a…6c34`）。构建 `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 通过：单元测试 145 项 0 失败，lint 0 errors / 21 warnings / 4 hints。全部设备测试 46 项：35 项通过，10 项需要登录或测试开关的跳过，1 项失败（`NavigationIntegrationTest.folderCodexPanelsKeepDirectoryDraftAndMessagesAcrossSwitchAndRecreation`：整套一起跑时 Codex 页停在登录页，找不到草稿输入框）。这一项单独重跑 3 次都通过，看作不稳定的测试。`PhoneStorageIntegrationTest` 在未授权、已授权两种状态下各 3 项通过。桌面端 200 项测试和 `tsc --noEmit` 通过。模拟器上没有 Codex 登录，这次没有跑真实模型；真实模型的结果引用实现者在 Fold7 上的记录。这次没有操作真机。跑完后撤销了模拟器的所有文件访问权限，删掉了测试包和演示目录。
- 提示符显示当前路径，新建会话、`cd` 到中文/空格目录、切换会话时都正确：**做到**。模拟器上新终端的标题和提示符都显示路径，从文件页在 `Download/验收 Demo` 打开的终端，提示符和 `pwd` 都对（[截图](screenshots/2026-11-21-acceptance-06-terminal-in-phone-folder.png)）；中文、空格目录的测试通过。顺带记一下：实现说明说默认显示 `~ $`，实际家目录显示的是完整的 `/data/data/dev.waddle.android/files/home $`，手机上要占一整行（[截图](screenshots/2026-11-21-acceptance-01-terminal-start.png)）。
- 底部能展开、输入、查看路径和上下文、生成、编辑、取消；生成期间不执行：**做到**。抽屉里显示会话名、路径和「查看本次上下文」（[截图](screenshots/2026-11-21-acceptance-07-command-sheet.png)）；生成期间不执行由设备测试覆盖，真实模型那部分依据实现者在模拟器和 Fold7 上的记录。
- 建议和当前会话有关；没登录或连不上时看得懂原因：**做到**。模拟器上没登录时点「生成命令」，显示「请先在 Codex 页面登录，再回来生成命令。」（[截图](screenshots/2026-11-21-acceptance-10-suggest-not-signed-in.png)）。建议和会话相关这一点，依据实现者真实模型测试的记录。
- 单行、长命令、多行 Bash 能预览编辑，回车只执行一次，结果出现在原会话：**做到**。约 16 KB 的多行脚本、重复执行防护、`cd`/export 保留的测试都通过；硬件回车和软键盘执行键的真实模型 UI 测试依据实现者记录（Fold7 上解锁后重跑通过）。
- 切换/关闭会话、目录变了、前台有程序在跑时，不会把过期建议送错地方；取消不影响别的会话：**做到**，相关测试全部通过。
- 保留文件和登录；Android 构建和终端回归通过：**没做到**。构建和回归测试是通过的，但**每个新终端一启动都会先打出两行 `bash: bind: 'vi-insertion': invalid keymap name`**（[截图](screenshots/2026-11-21-acceptance-01-terminal-start.png)、[截图](screenshots/2026-11-21-acceptance-06-terminal-in-phone-folder.png)），来自这份需求加的终端集成。第一版终端没有这个问题，用户每开一个终端都会看到报错。实现说明也提到了，但没有处理。
- 需要用户在 Fold7 上看：用最终版本问一次 Codex 生成命令，编辑后回车执行，看结果是否出现在原会话；看看真机上新终端是否也有这两行报错。
- CI：提交后补记。
- 结论：**验收不通过**。没做到的部分另提 [2026-11-21-06-terminal-startup](2026-11-21-06-terminal-startup.md)。
