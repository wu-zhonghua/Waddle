# 安装包里带上 Codex、Claude Code、Antigravity，在终端里敲命令打开，设置里统一登录账号；国内版能一键去掉（3 个阶段）

- 状态：验收不通过（阶段 1）
- 日期：2026-11-22
- 上一份：[2026-11-22-01-ai-terminal](2026-11-22-01-ai-terminal.md)（阶段 1 验收不通过）；取代 2026-11-22-02、2026-11-22-03
- 涉及：安卓

## 现在的样子

2026-11-22-01 阶段 1 把 Codex 从安装包里去掉了，换成内置 AI（DeepSeek、通义千问、Kimi、智谱、自定义 OpenAI 兼容）。用户在 Fold7 上打开终端敲 `codex`，只看到 `command not found`；原来的 Codex 对话也没了。这不是用户要的：用户要的是**各种都能用**，不是用国内模型换掉 Codex。另外 01 的 GitHub Android 检查没过：构建时缺 Codex 包；全新模拟器上第一次打开时，弹出的通知权限询问挡住了设备测试（见 01 的验收记录）。

## 要做什么

Waddle 是 AI 终端，**能用的 AI 都带上，入口就是终端**：

- 安装包里直接带 Codex、Claude Code、Antigravity。在哪个目录的终端里敲 `codex`、`claude`、`agy`，它就在哪个目录工作。
- 01 做好的内置 AI（DeepSeek 等）保留。
- 设置里有一个统一的「账号」页，所有要登录或填 Key 的地方都在这里。

**终端里敲命令以后怎么打开**，三个工具都一样：

- 第一次敲时，问用户用哪种界面：
  - **对话界面**：Waddle 为手机做的界面，能看清它要执行的命令、要改的文件，点按钮同意或拒绝。
  - **终端界面**：工具原本的样子，就在这个终端里跑。
- 默认推荐对话界面，因为手机屏幕小，用软键盘操作原版终端界面不方便。
- 有「记住我的选择」，记住以后不再问；在设置里能改。不想用记住的那种时，也能临时用另一种打开。
- 对话界面里有「回到终端」。终端里能看出这个对话还在进行，点一下就能回到对话界面。在对话和终端之间来回切，对话内容不会丢。
- 抽屉里**只列出正在进行的对话**，方便切回去，不放“新建 Codex/Claude 对话”这种入口。文件夹菜单里也不放“在这里打开 Codex/Claude/Antigravity”：要在某个文件夹用，就在那里打开终端，再敲命令。

以后要出国内版，国内版里不带这些海外工具。所以这次就要做到**很容易拆掉**：同一份代码能出两种安装包，国际版带全部工具，国内版一个都不带，界面里也看不到它们。国内版这次不上架、不发布，只要能建出来、试过没问题。

（01 验收时用户定过“Codex 包不进仓库”，那是在“产品不带 Codex”的前提下定的。现在要带，仓库里放不放这些安装包由实现者定，但要满足下面“怎样算做好”里关于干净代码构建和 CI 的要求。）

### 阶段 1：Codex 回来，加上「账号」页，能出国内版，CI 通过

1. **Codex 回到安装包里，按上面说的方式从终端打开。**
   - 对话界面里，Codex 给的图片、文件路径能直接看。
   - 可以从不同的终端各开一个 Codex 对话，同时进行，互不影响。
2. **「账号」页：**设置里新增，一处看到所有账号的状态（已登录 / 没登录 / Key 已保存）。
   - Codex：用 ChatGPT 账号登录，或者填 API Key；能退出登录。
   - 内置 AI：01 做好的 DeepSeek、通义千问、Kimi、智谱、自定义 Key 和模型设置，挪到这里，或者从这里能直接进去。
   - 登录一次，对话界面和终端界面都能用，不用登两次。
   - 从 0.4.0 或 0.5.0 覆盖安装，原来的 Codex 登录还在，不用重新登。
3. **写命令、看报错可以选用哪个 AI：**“问 AI，写命令”和“让 AI 看看”可以选用内置模型（DeepSeek 等），也可以选用已登录的 Codex。
4. **国内版：**同一份代码能建出国内版安装包。
   - 国内版里没有 Codex、Claude Code、Antigravity 这些程序，终端里敲这些命令都提示没有。
   - 「账号」页和设置里看不到它们。
   - 内置 AI 和终端功能照常能用。
5. **第一次打开：**全新安装后第一次打开，会询问通知权限。不管选“允许”还是“不允许”，终端、文件浏览、AI 入口都能正常用。

### 阶段 2：Claude Code

1. 安装包里带 Claude Code，终端里敲 `claude` 打开，界面选择和切换跟 Codex 一样。
2. 「账号」页里能登录 Claude 账号，或者填 Anthropic API Key；也能退出登录。
3. “写命令、看报错”也可以选用 Claude。
4. Codex 和 Claude 可以同时开、同时用，对话和登录互不影响。

### 阶段 3：Antigravity

1. 安装包里带 Antigravity 的命令行工具，终端里敲 `agy` 打开，界面选择和切换跟 Codex 一样。
2. 「账号」页里能用 Google 账号登录，也能退出登录。
3. 如果确认 Antigravity 在安卓上跑不起来，在实现说明里写清楚原因和试过什么，然后停下等用户决定，不要硬做一个半能用的。

## 不做

- 抽屉里的“新建对话”入口，文件夹菜单里的“在这里打开 Codex/Claude/Antigravity”。
- 国内版的上架和发布（这次只要能建出来）。
- 扩展商店，或者装好以后再下载新工具。
- 桌面端。
- 01 阶段 2（AI 一步步做事），等这份做完再做。
- Waddle 代用户开通、购买这些账号。

## 怎样算做好

### 阶段 1

- 在安卓模拟器和 Fold7 上（国际版），第一次在终端敲 `codex`：
  - 会问用对话界面还是终端界面，默认推荐对话界面。
  - 两种都能用，并且都在这个终端当前的目录里工作。
  - 勾「记住我的选择」后，再敲不会问；设置里能改回来；也能临时用另一种打开。
- 对话界面：
  - 能看到 Codex 要执行的命令、要改的文件，拒绝的就不执行；Codex 给的图片能直接看。
  - 「回到终端」以后，终端里能看出对话还在进行，点一下回到对话，内容还在。
  - 抽屉里能看到正在进行的对话，没有“新建对话”入口；文件夹菜单里没有“在这里打开 Codex”。
  - 在真机上用真实账号，让 Codex 完成一个读文件、写文件的小任务。
- 「账号」页能看到 Codex 和内置 AI 的状态：
  - Codex 能登录、退出。
  - 登录一次，两种界面都能用。
  - 在 Fold7 上从 0.5.0 覆盖安装，原来的 Codex 登录还在，用户文件不丢。
- “问 AI，写命令”和“让 AI 看看”能在内置模型和 Codex 之间切换；选 Codex 时能真实生成命令，确认后才执行。
- 国内版装到模拟器上：
  - 终端里 `codex` 提示没有这个命令；「账号」页和设置里看不到 Codex、Claude Code、Antigravity。
  - 终端和内置 AI 照常能用。
  - 国内版安装包明显比国际版小。
- 在全新模拟器上新装，第一次打开时的通知询问选“允许”或“不允许”，之后终端、文件浏览、AI 入口都正常。从刚 clone 的干净代码能按 README 构建出国际版和国内版，构建不依赖随时可能消失的外部下载地址。GitHub 上的 Android（构建和设备测试）和 Desktop checks 都通过。

### 阶段 2

- 在模拟器和 Fold7 上，终端里敲 `claude`，界面选择、记住选择、两种界面、回到终端都和 Codex 一样能用。
- 「账号」页能登录、退出 Claude。在真机上用真实账号让 Claude 完成一个读文件、写文件的小任务；用户拒绝的命令和改动不执行。
- “写命令、看报错”能选用 Claude。
- Codex 和 Claude 各开一个对话同时用，内容不会串；退出其中一个的登录，另一个不受影响。
- 国内版里仍然看不到 Claude。GitHub 上的 Android 和 Desktop checks 都通过。

### 阶段 3

- 在模拟器和 Fold7 上，终端里敲 `agy`，界面选择、记住选择、两种界面、回到终端都和 Codex 一样能用。
- 「账号」页能用 Google 账号登录、退出。在真机上用真实账号完成一个读文件、写文件的小任务。
- Codex、Claude、Antigravity 三个同时用，互不影响。
- 国内版里仍然看不到 Antigravity。GitHub 上的 Android 和 Desktop checks 都通过。
- 如果 Antigravity 在安卓上跑不起来：实现说明里有原因和试过的办法，前两个阶段的东西不受影响。

---

## 实现说明（实现者填写）

### 2026-11-22 · 阶段 1

只实现本需求的阶段 1。当前分支、当前目录内完成；没有 commit、push、tag、分支或 worktree 操作。没有实现 Claude Code、Antigravity，也没有做 01 的多步任务阶段。

**做了什么**

- 版本为 `0.6.0-international` / `0.6.0-domestic`（versionCode 6，同一个 application ID）。国际版恢复固定版本 Codex 0.170.0、code-mode helper 和进程 supervisor；国内版在生成运行时前排除这些包，APK 没有这些程序，运行时清单及界面也不提供它们。
- 运行时由 `--distribution international|domestic` 分开生成，Gradle 用 `-PwaddleDistribution=domestic` 切换；默认国际版。两种构建的运行时目录独立，构建前校验分发类型和 ABI，国内版拒绝误混入 Codex 可执行文件。
- 终端裸命令 `codex` 会带着发起终端的当前目录请求界面选择，默认推荐对话界面，可记住选择。在「账号 → 管理 Codex 账号与界面」可改回每次询问；`codex --waddle-chat`、`codex --waddle-terminal` 临时覆盖。普通 CLI 参数（如 `codex --version`、`codex exec ...`）直接交给原 CLI。
- 对话由终端前台服务持有，各有独立进程、thread、工作目录、消息、草稿和审批队列，最多同时 8 个。「回到终端」、终端的进行中提示、抽屉进行中对话可来回切换；Activity 重建后仍恢复页面和内容。抽屉没有新建 Codex 入口，文件夹菜单没有 Codex 入口。
- 恢复命令/文件改动展示、允许/拒绝、停止、路径链接、图片缩略图和完整文件查看器。新对话使用 `untrusted` 审批策略和只读 sandbox 请求，写操作需要审批；未知交互拒绝，停止时拒绝未处理的审批并结束该对话的进程树。Android 仍不是严格的工作区目录隔离环境，不能把这称作完整系统级沙箱。
- 新增统一「账号」页，先汇总 Codex 与五个内置模型服务商状态，再进入具体管理。Codex 支持 ChatGPT 设备码登录、API Key、刷新和退出；沿用旧 `files/home/.codex`，对话、CLI、命令建议共享登录。已打开的空闲 app-server 检测到其他入口更新登录文件时会重连并恢复 thread，避免继续使用旧缓存；进行中的任务不被刷新强行打断。内置模型沿用 01 的 Keystore 加密保存、配置、测试连接和删除功能。退出 Codex 会停止 Waddle 对话，文件保留；独立运行的 CLI 应先退出再更换账号。
- 「问 AI，写命令」「让 AI 看看」可切换内置模型/Codex，并直接进入账号配置。Codex 建议使用独立的临时 thread，不提供执行环境或工具；结果仍走原会话、cwd、prompt epoch 校验，用户确认前不会运行。修复了面板动画可能抢回问题框焦点、影响硬件回车的情况。
- 全新安装只询问一次通知权限。允许或拒绝都不影响终端、文件和 AI 入口；拒绝后不会在 Activity 重建/重开时反复询问。设备测试 runner 在模拟器上实际点击通知弹窗，支持 allow/deny；不自动处理真机系统权限。CI 矩阵覆盖国际/国内两种构建及两种通知选择。
- 两个用户提供的原始 Codex `.deb` 决定作为离线构建输入保留，和原有 37 个包一起校验原锁定 SHA-256；没有改动包内容或锁，也不依赖会过期的下载地址。**验收者提交时需要把这两个目前未跟踪的 `.deb` 和新增源码一起加入 Git**，否则干净 clone 仍会缺国际版原料。每个文件均小于 GitHub 100 MiB 限制，无需 LFS。运行时来源/许可证记录已更新；没有改 vendored Termux 终端组件源码。

**怎么构建和试用**

环境仍为 JDK 17、SDK 36、build-tools 36.0.0、NDK 28.2.13676358，设置 `JAVA_HOME` / `ANDROID_HOME`。从仓库根目录进入安卓工程：

```sh
cd apps/android
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/prepare-runtime.py --distribution international --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
# 保存国际版 APK 后再切换构建，默认输出路径相同。
python3 tools/prepare-runtime.py --distribution domestic --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PwaddleDistribution=domestic
```

x86_64 在准备时加 `--abi x86_64`，Gradle 加 `-PwaddleAbi=x86_64`。APK 路径为 `app/build/outputs/apk/debug/app-debug.apk`。两种版本使用同一包名，不能并排安装；切换版本用覆盖安装，不要为切换版本清数据。当前本机可试用产物在忽略目录 `artifacts/bundled-agents/waddle-{international,domestic}-arm64-debug.apk`，不把 APK 提交进 Git。

国际版试用：先在终端 `mkdir -p demo; cd demo`，敲 `codex`，选择推荐对话界面；到「账号」登录或使用覆盖安装保留的登录。让它在 demo 里读写文件，查看审批，尝试拒绝；给出图片路径后可直接点缩略图。返回终端，再点进行中对话；换一个终端和目录可开独立对话。勾选记住选择后再次敲命令；用 `--waddle-terminal` 临时打开原版 TUI。底部 AI 面板可选 Codex 生成命令或诊断错误，先编辑再确认执行。国内版只显示内置 AI，三个海外命令均不可用。

**验证记录**

| 验证 | 结果 |
| --- | --- |
| 运行时输入测试 | Python 3 项通过，离线包 SHA-256 校验通过。 |
| 本机 ARM64，国际/国内 | 两种版本的 `assembleDebug`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug` 全部通过。 |
| 干净源码副本，x86_64，国际/国内 | 从当前源码及待提交输入复制，不带 `.git`、`local.properties`、生成运行时或 build 目录；两种运行时离线生成，上述四项 Gradle 检查全部通过。本机没有跑 x86_64 设备测试。 |
| Youzai_API_35 ARM64 国际版，全新安装，通知允许 | 48 通过、5 跳过、0 失败；runner 确认实际选择 allow。 |
| 同模拟器国际版，全新安装，通知拒绝 | 48 通过、5 跳过、0 失败；runner 确认实际选择 deny。 |
| 同模拟器国内版，全新安装，通知拒绝 | 44 通过、9 跳过、0 失败；runner 确认实际选择 deny。 |
| 同模拟器国内版，全新安装，通知允许 | 导航与命令建议 15 通过、1 跳过、0 失败；runner 确认实际选择 allow。 |
| 同模拟器，国际版覆盖为国内版 | 8 通过、0 失败；三个海外命令不存在，旧 `.codex` 内的测试哨兵文件保留。 |
| Fold7 国际版最终全套回归 | 49 通过、4 跳过、0 失败。 |
| Fold7 登录跨进程刷新补充回归 | 13 通过、1 跳过、0 失败，覆盖已打开客户端看到另一客户端登录/退出、审批、终端桥接、图片和页面重建。 |
| Desktop checks 的本地命令 | Node 22：37 个测试文件、200 项测试通过；`tsc --noEmit` 通过；按 CI 的 `NODE_OPTIONS=--max-old-space-size=8192` 执行 `npm run build:dev` 通过。未改桌面源码。 |

Fold7 先前启用真实请求的完整运行中，`liveSavedLoginReadsWritesAndDraftRequiresConfirmation` 已通过：复用旧 ChatGPT 登录，在独立测试目录读写文件、拒绝写入并确认未落盘、真实生成命令且确认前不执行、用原 CLI 完成文件任务，以及真实报错诊断。该轮另有 3 项 UI 失败；随后修复了对话 Activity 重建、输入焦点及测试等待问题，最终全套回归零失败。最终回归不再重复真实请求，所以真实任务项显示跳过。

跳过项来自显式 opt-in 的真实服务请求、没有授权的共享存储路径，以及不适用的分发类型/设备状态；国内版跳过 Codex 专属用例。内置 AI 成功请求使用本机可控 HTTP 服务测试，不使用用户未提供的 Key。设备码网页授权没有替用户完成。最后的账号刷新改动在 Fold7 补充回归和两种 ABI/分发构建中验证，没有再次重复所有模拟器矩阵。

最终 ARM64 Debug APK：国际版 **174,686,034 字节（约 166.6 MiB）**，国内版 **23,355,287 字节（约 22.3 MiB，减少约 86.6%）**。检查 APK 的运行时清单和 native 库，国内版不含 Codex、helper、supervisor 或其他海外工具程序。

可见界面记录：[Fold7 账号页](screenshots/2026-11-22-04-fold7-accounts.png)、[首次界面选择](screenshots/2026-11-22-04-fold7-interface-choice.png)、[原版终端界面](screenshots/2026-11-22-04-fold7-terminal-interface.png)。本地详细测试输出和汇总位于忽略目录 `artifacts/bundled-agents/`，不提交设备日志或构建产物。

**Fold7 数据与收尾**

所有主应用和测试应用安装都明确使用 `--user 0 -r`，没有卸载主应用、没有清除应用数据、没有向用户 95 安装。没有读取/打印真实 Key 或 token，也没有退出用户的真实 Codex 登录。API Key 保存/退出测试使用隔离的临时 `CODEX_HOME` 和明显假的 Key；真实测试复用原有 ChatGPT 登录。

第一次采集本轮安装前哈希时，`run-as` 参数顺序写错，采集失败后安装命令仍执行了，这是本轮操作上的疏漏。随后用上轮保存在本机的 0.5.0 验收后清单核对：安装后的 **2,993 个 home 文件与该基线完全一致，0 缺失、0 新增、0 内容变化**。后续每次覆盖安装均先成功保存快照再安装，安装前后用户文件无丢失。真实 Codex 使用后，其缓存、SQLite WAL/SHM、临时进程锁会正常新增/更新/清理；这与删除用户文件区分记录。哈希原始清单留在本机临时目录，不提交含私有路径的设备清单。

期间遇到锁屏先暂停；用户随后明确提供解锁方式并要求代为解锁，使用正常系统图案界面解锁，没有绕过系统认证或改动锁屏设置。测试期间临时启用 USB 常亮，结束恢复原值。

最终核对：`.codex` 以外原有 **34 个 home 文件全部存在**，唯一内容变化是正常使用产生的 `.bash_history`；真实 `auth.json` 哈希不变。后续四次交付覆盖安装的完整 home 清单，均在紧邻安装的前后逐文件哈希一致。测试运行后 `.codex` 中减少的文件仅为 SQLite WAL/SHM、临时目录和进程锁等运行数据。

Fold7 主用户 0 保留 `0.6.0-international` 主应用；测试应用 `dev.waddle.android.test` 已删除，用户 95 没有 Waddle 包。设备端测试 UI 转储已清理，USB 常亮恢复原值 `0`，主应用已正常启动。最终检查时系统已自动锁屏，`MainActivity` 仍为用户 0 的前台任务；解锁后可继续使用，没有关闭或绕过锁屏。

**需要知道的范围和限制**

- 用户本轮未提供 DeepSeek 等服务商的真实 Key；没有声称验证了这些服务商的成功连接或国内网络表现。保留的内置 AI 使用确定性 HTTP 测试验证配置、错误处理、取消、草稿和执行保护。Codex 则使用真机已有真实账号验证。
- 没有为了测试 ChatGPT 重新登录而退出用户账号。旧登录复用是真实验证；API Key 保存/退出使用隔离目录的假 Key 验证本机流程，不代表真实 API Key 的计费/模型权限。设备码登录仍需用户自己在浏览器授权。
- 对话在服务存活时保留；系统杀进程/重启后活动面板不会自动重开。磁盘上的旧 `.codex` 登录及历史不删除，可用 CLI 的 `codex resume` 访问。
- **GitHub checks 未对这批未提交代码运行。** 按要求没有 commit/push，因此不能把本地通过写成 GitHub 上已经通过；工作流和原料缺失已修正，本地执行相应构建、设备测试与 Desktop checks。验收者提交全部输入后仍须看实际 GitHub 结果。

## 验收记录（验收者填写）

### 2026-11-23 · 阶段 1（Claude 验收）

**范围和环境**：只验收阶段 1。今天 Fold7 没接，只在 Mac mini 的安卓模拟器上验收（API 35，arm64）。真机专属的几项沿用实现者在 Fold7 上的记录，下面逐条注明。验收者没有改代码、CI 配置或测试。截图和日志放在不提交的 `artifacts/acceptance-04/`（截图在 `shots/`）。

模拟器说明：一开始按要求用 `Youzai_API_35` 跑设备测试。这台模拟器当时正在被另一个项目的工具占用：每隔几秒有人在上面跑 `uiautomator dump`，导致测试进程的 UiAutomation 连接被挤掉。结果国际版「拒绝」那一轮全部通过（50 通过、6 跳过、0 失败），国际版「允许」和国内版两轮都被中途打断，崩溃日志里能看到外部的 dump 进程。这不是 Waddle 的问题。之后四轮全部改到当时空闲的 `Waddle_API_35` 上重跑，下面的结果都来自那台。

**自动检查（验收者自己跑的）**

| 检查 | 结果 |
| --- | --- |
| 运行时输入 Python 测试 | 3 项通过；离线包（含两个 Codex `.deb`）SHA-256 校验通过 |
| 国际版 `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` | 通过 |
| 国内版（`-PwaddleDistribution=domestic`）同上四项 | 通过 |
| 设备测试，国际版，全新安装，通知「允许」 | 56 项：50 通过、6 跳过、0 失败 |
| 设备测试，国际版，全新安装，通知「拒绝」 | 56 项：50 通过、6 跳过、0 失败 |
| 设备测试，国内版，全新安装，通知「允许」 | 56 项：46 通过、10 跳过、0 失败 |
| 设备测试，国内版，全新安装，通知「拒绝」 | 56 项：46 通过、10 跳过、0 失败 |
| 干净源码副本构建（国际版） | 只复制会进 Git 的文件（含两个 `.deb`），不带生成的运行时、build 目录和 `local.properties`；离线生成运行时，`assembleDebug testDebugUnitTest lintDebug` 通过 |
| Desktop checks 的本地命令（Node 22） | 37 个测试文件、200 项测试通过；`tsc --noEmit` 通过；`npm run build:dev` 通过 |
| APK 内容 | 国际版 174,382,825 字节，带 `libwaddle_codex.so`、helper、supervisor；国内版 23,367,895 字节（小约 86.6%），运行时清单只有 bash、curl、git、rg，没有 Codex、Claude、Antigravity |

跳过项：国际版跳过的是需要真实服务的 3 项（真实 Codex 任务、真实历史续聊、真实内置模型请求）、需要手动授权的 2 项共享存储，以及只适用于国内版的「未配 AI 引导」。国内版另外跳过 Codex 专属用例。

**逐条对照「怎样算做好」（阶段 1）**

1. 第一次在终端敲 `codex`（模拟器，国际版）：**做到**。
   - 弹出「用哪种界面打开 Codex？」，显示当前目录，默认选中「对话界面（推荐）」。
   - 对话界面标题栏的目录是敲命令的终端目录（`~/demo`）。终端界面在同一终端里跑原版 Codex，目录也是 `~/demo`。
   - 勾「记住我的选择」选终端界面后，再敲 `codex` 不再问，直接进原版界面。`codex --waddle-chat` 能临时用对话界面打开。在「账号 → 管理 Codex 账号与界面」改回「每次询问」后，再敲又会问。
   - Fold7 上的同样流程：沿用实现者记录（Fold7 全套回归 49 通过、0 失败）。
2. 对话界面：
   - 能看到要执行的命令和要改的文件，拒绝的不执行，图片能直接看：**设备测试做到**（审批、拒绝、图片查看、旋转和重建的用例在模拟器上通过）。真实 Codex 输出：**模拟器没测**（没有 ChatGPT 登录），**Fold7 上实现者的运行通过**。
   - 「回到终端」以后，终端底部显示「Codex 1 · 对话进行中 · 返回对话」，点一下回到对话，没发出的草稿还在：**做到**。
   - 抽屉只列出进行中的对话（「Codex 1 · ~/demo」「Codex 2 · ~/other」，两个终端各开一个），没有「新建对话」入口；文件夹菜单只有「打开终端」，没有「在这里打开 Codex」：**做到**。
   - 真机真实账号完成读写文件的小任务：**模拟器没测**；**Fold7 上实现者的运行通过**。
3. 「账号」页：
   - 能看到 Codex 和五个内置 AI（DeepSeek、通义千问、Kimi、智谱、自定义）的状态，并能进入 Codex 管理和 AI 模型设置：**做到**。
   - Codex 登录：点「用 ChatGPT 登录」能拿到设备码，「取消登录」可用。没有替用户完成网页授权。用 API Key 登录和退出、以及 CLI 和对话共用登录，在隔离目录里的设备测试通过。真实登录和退出、登录一次两种界面都能用：**模拟器没测**；**Fold7 上实现者的运行通过**（复用原有 ChatGPT 登录）。
   - 从 0.5.0 覆盖安装后登录还在、用户文件不丢：**Fold7 上实现者核对通过**。模拟器上另做了国际版覆盖安装成国内版：`.codex` 里的测试文件和 home 里的用户文件都还在。
4. 「问 AI，写命令」「让 AI 看看」：两个面板顶部都有「内置模型 / Codex」切换；没登录选 Codex 时提示先去账号页登录，没填 Key 选内置模型时提示填写 API Key；「账号与模型设置」能直接进账号页：**做到**。选 Codex 真实生成命令、确认前不执行：**模拟器没测**；**Fold7 上实现者的运行通过**。DeepSeek 等真实 Key：**没测**（没有 Key），只有本地模拟服务的设备测试通过。
5. 国内版：**做到**。
   - 覆盖安装和全新安装都试过。终端里 `codex`、`claude`、`agy` 都是 `command not found`。
   - 「账号」页只有内置 AI，AI 面板没有 Codex 选项。
   - 终端、文件浏览、AI 模型设置和未配 AI 的引导都正常。
   - 安装包约 22.3 MiB，国际版约 166.3 MiB。
6. 第一次打开的通知询问：**做到**。国际版和国内版全新安装都弹出询问。选「允许」或「拒绝」以后，系统权限状态和选择一致，终端和文件浏览都能用。拒绝后再打开不会重复询问。设备测试四种组合都通过。
7. 干净代码构建：**本地做到**（见上表；两个 Codex 包随本次提交进仓库，不依赖外部下载）。GitHub 上的 Android 和 Desktop checks：见下方 CI。

**其他**

- 工作区里同时有 2026-11-22-05（Codex 历史对话）的实现，和 04 的代码在同一批文件里，所以一起提交。05 没有在这次验收；它的历史对话用例在模拟器上通过，但真实续聊被跳过。
- 小问题（不影响结论）：模拟器上用 `adb input` 敲命令时，终端偶尔把同一行回显两遍。这和 Waddle 无关。

- CI（提交 `5859731d`）：
  - **Desktop checks 通过**。
  - **Android 没过**（[运行记录](https://github.com/wu-zhonghua/Waddle/actions/runs/45098496449)）：
    - `build (international)` 和 `build (domestic)` 都通过。这是在 GitHub 上从干净 checkout 离线构建的，两个 Codex 包已经在仓库里。
    - `device-tests (domestic, allow)` 失败了 2 个用例：
      - `NavigationIntegrationTest.fileFirstNavigationPreservesShellsAndCloseConfirmation`：抽屉没有关上（「Drawer did not close」）。
      - `NavigationIntegrationTest.folderCardAndCurrentFolderOpenNewShellsHereWithoutInterruptingOriginal`：用「在当前文件夹打开终端」打开的新终端，目录是上一级 `open-here-…`，不是子文件夹 `中文 ' folder`。
    - 另外三组设备测试（domestic/deny、international/allow、international/deny）都因为这一组失败被自动取消，没有跑完。
  - 这两个用例在本地 arm64 模拟器上四轮都通过，可能和 GitHub 上 x86_64 模拟器的速度有关。但第二个也可能是真的进错了目录，要由实现者查清楚。
- 结论：**验收不通过（阶段 1）**。
  - 功能在模拟器上都做到了：真实 ChatGPT 账号和 Fold7 的几项沿用实现者在 Fold7 上的记录，DeepSeek 等真实 Key 没有验证。
  - 按流程，CI 没过算没做到：GitHub 上 Android 设备测试失败，国际版的设备测试没有在 GitHub 上跑完。
  - 没做到的部分另提需求 [2026-11-23-01-android-ci-green](2026-11-23-01-android-ci-green.md)。
  - 代码已在验收通过的判断下随 `5859731d` 提交（当时 CI 还没出结果）。按流程不回滚、不强推，修复在新需求里做。
