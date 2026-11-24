# 安卓界面整体重做：看起来像一个专业的终端产品，不像 AI 拼出来的（2 个阶段）

- 状态：已实现（阶段 1、2）
- 日期：2026-11-23
- 上一份：无（在 [2026-11-23-01-android-ci-green](2026-11-23-01-android-ci-green.md) 之后做，在 [2026-11-22-04](2026-11-22-04-bundled-agents-and-accounts.md) 阶段 2 之前做）
- 涉及：安卓

## 现在的样子

同事看了说"这个界面太 AI 了"。对照 04 阶段 1 验收时的截图，问题主要有这些：

- **到处是绿色文字按钮**：「回到终端 / 账号 / 历史对话 / 结束对话」「设置与运行环境」「帮助」「上一级」「更多」都是一样的绿字，分不出哪个重要、哪个是导航。
- **典型的 AI 味元素**：「✦ 问 AI，写命令…」的星星图标和大圆角胶囊、浅绿色大圆角按钮、所有东西都放进同样大小的圆角卡片里（文件页一个 WELCOME.txt 占半屏）。
- **说明文字太多**：标题下面有「选择位置，打开文件夹」，对话框里有「在这个文件夹中工作」，问 AI 面板里有「发送当前路径和最近终端内容。生成后先预览，回车才运行」。专业产品通常不用一句话解释界面。
- **露出内部细节**：路径到处写全称 `/data/data/dev.waddle.android/files/home/demo`；终端底部有调试一样的「RUNNING」「Bash · Waddle」；会话叫「Shell 1」「Codex 3」。
- **样式不统一**：账号是一个弹窗，里面是「DeepSeek · 没登录」这样的纯文字列表；抽屉里终端项有三行、Codex 项一行，大小、缩进、选中样式都不一样；问 AI 面板里输入框、按钮、链接各一种风格。
- **对话页很空**：只有一行状态文字和一个按钮，看不出这是一个能读文件、跑命令、改代码的工具。

## 要做什么

把安卓版所有界面按一套统一的设计重做，让人一看就是一个认真做的终端产品。**功能一个都不能少**：现在能做的事，重做后都还在，而且入口不能更难找。

方向参考这些产品，取它们的做法，不照抄外观：

- **Blink Shell**（iOS）：终端就是主角，开屏就是全屏终端，没有多余菜单，靠手势切换会话。
- **Termius**（安卓）：键盘上方一条可自定义的快捷键栏；Android 版专门适配折叠屏，展开时用大屏布局，合上时自动变紧凑。
- **Warp**：终端和 AI 对话分开，AI 的控件平时藏起来，进入对话才出现；命令和它的输出是一块一块的，一眼能看出哪块是哪条命令。
- **Codex / Claude Code 的手机端**：对话里清楚地显示要执行的命令、要改的文件（用 +/- 高亮的改动），允许 / 拒绝是明确的两个按钮。

具体要求：

1. **一套设计规范**：定好颜色（深色为主，**只用一个强调色**，并且只用在真正重要的地方，比如主要按钮、当前选中项）、字体和字号层级、间距、圆角、图标（统一一套线性图标，大小粗细一致）。所有界面都按它来。
2. **去掉 AI 味**：不用 ✦ 星星、发光、渐变；不要"什么都放进圆角卡片"；不在标题下写解释性副标题；按钮分清主次（主要操作一个实心按钮，其余用图标或普通文字）。
3. **不露内部细节**：Waddle 自己的家目录显示成 `~`（例如 `~/demo`），只在需要时才显示完整路径；去掉「RUNNING」「Bash · Waddle」这类调试文字；会话名用文件夹名或用户能看懂的名字。
4. **终端页**：终端占满屏幕；顶部栏精简（会话名 + 当前文件夹 + 少量图标）；快捷键栏样式统一；「问 AI」入口要能找到，但不能比终端本身还显眼；正在进行的 AI 对话在终端里有一个安静、一看就懂的提示，点它回到对话。
5. **AI 对话页**（Codex，以后的 Claude、Antigravity 共用）：消息、AI 要执行的命令、要改的文件、改动内容、图片分别有清楚的样式；需要用户确认时，允许 / 拒绝两个按钮明确；顶部操作（回到终端、历史对话、结束对话、账号）收成图标或菜单，不再是一排绿字。没登录时给一个像样的空状态，而不是一行字加一个按钮。
6. **抽屉**：终端和 AI 对话用同一种列表项（图标 + 名字 + 文件夹 + 状态），当前项的选中样式统一；文件、设置、帮助的位置合理。
7. **文件页**：默认用列表（图标、名字、大小或修改时间），可以切换成网格；文件夹和文件的图标风格和全应用一致；「打开终端」等操作不再占一整条大按钮，除非它确实是这页最主要的操作。
8. **设置和「账号」页**：账号做成一个完整的页面，不再是弹窗；每个服务一行（图标、名字、状态、操作），已登录 / 没登录一眼能看出来；设置按分组排列。
9. **弹窗和底部面板**：只在需要用户做决定时弹窗（比如第一次敲 `codex` 时选界面）；样式统一；问 AI 面板精简，只留输入框、模型选择和生成按钮。
10. **折叠屏**：Fold7 展开时用大屏布局（例如抽屉常驻在左边，或者对话和终端并排），合上时是紧凑布局，切换时不丢内容。

分两个阶段：

### 阶段 1：设计规范 + 三个核心界面

1. 定好设计规范，写进仓库（颜色、字号、间距、图标的规则，后面的界面和新功能都照它做）。
2. 按新规范重做 **终端页、AI 对话页、抽屉**。
3. 在实现说明里放这三个界面在模拟器上的 **改前 / 改后截图**（手机竖屏，加上 Fold7 尺寸的展开 / 合上）。

用户 2026-11-23 要求一次做完：阶段 1 做完直接接着做阶段 2，不用停下等确认；所有截图最后一起给用户看。

### 阶段 2：其余所有界面

1. 按同一套规范重做文件页、设置、「账号」页、问 AI / 看报错面板、所有弹窗、帮助页、第一次打开时的通知询问。
2. 国内版同样用新界面，并且仍然看不到海外工具。
3. 实现说明里放每个界面的改前 / 改后截图。

## 不做

- 增加或删除功能、改功能流程（只改长什么样、放在哪里；为了放得更合理挪动入口可以，但不能删）。
- 桌面端。
- 浅色主题（这次只做深色；规范里可以预留）。
- 应用图标和启动图（另提需求）。
- 04 阶段 2、阶段 3（Claude Code、Antigravity）。它们在这份之后做，直接用新的设计规范。

## 怎样算做好

### 阶段 1

- 模拟器上，终端页、AI 对话页、抽屉里：
  - 看不到 ✦ 星星、渐变、发光；只有一个强调色，而且只用在主要按钮和当前选中项上；
  - 路径显示成 `~/demo` 这种形式；看不到「RUNNING」「Bash · Waddle」；
  - 对话页顶部不再是一排绿字；命令、文件改动、需要确认的操作各有明显不同的样式；
  - 抽屉里终端和 AI 对话的列表项样式一样。
- 04 阶段 1 和 05 能做的事都还能做（敲 `codex` 选界面、记住选择、回到终端再切回、历史对话、结束对话、去账号页），已有的设备测试全部通过（需要随界面改的测试可以改，但测的事情不能少）。
- 设计规范已经写进仓库，实现说明里有三个界面的改前 / 改后截图，包括折叠屏展开和合上两种尺寸。
- GitHub 上 Android 和 Desktop checks 都通过。

### 阶段 2

- 文件页默认是列表、能切换成网格；「账号」是一个完整页面，每个服务一行，状态一眼能看出；设置分组清楚；问 AI 面板没有解释性长句。
- 全应用（包括国内版）没有 ✦ 星星和解释性副标题，按钮主次分明，图标风格一致。
- 在模拟器上用 Fold7 尺寸，展开时是大屏布局，合上时是紧凑布局，切换过程中终端和对话内容不丢。
- 所有原有功能都还在，设备测试全部通过；GitHub 上 Android 和 Desktop checks 都通过。
- 实现说明里有每个界面的改前 / 改后截图。

---

## 实现说明（实现者填写）

本次连续实现阶段 1、2，未进入 04 阶段 2、3，未修改桌面源码。

### 设计与实现

- 统一规范见 [Android 界面规范](android-design-system.md)，代码入口为 `ui/WaddleDesign.kt`：深灰色背景、单一蓝色强调、14/16/20sp 排版层级、4dp 间距基准、8dp 控件圆角、24dp 统一描边图标。错误与 diff 删除使用语义错误色，用户程序的 ANSI 配色保留。
- 终端去掉星星、胶囊、RUNNING 和品牌尾栏。目录名用于会话名称，标题、抽屉、对话和文件路径缩写成 `~`。同时将 Bash HOME 规范化为实际 cwd 使用的路径别名，终端自己的默认提示符也能显示 `~/demo`。快捷键、粘贴预览、中文输入、复制、缩放与多会话全部保留。
- 终端与对话共用同一导航列表项，均有图标、名称、短目录和状态；当前项使用相同选中样式。窄屏保留左边缘手势和导航按钮，文件仍有顶部直接入口，设置与帮助固定在导航底部。
- 对话顶部保留返回终端、历史、账号、结束四个可访问的图标操作。未登录时有工作区空状态和登录主操作；普通消息平铺，命令/输出使用等宽块，文件变更从协议中提取文件名与 diff，并按 `+`/`-` 高亮；文件链接、图片缩略图和预览继续使用原访问逻辑。审批是明确的实心「允许本次」及次要「拒绝」。
- 文件默认紧凑列表，可切换网格并在 Activity 重建后保留选择；目录菜单紧邻条目，当前目录打开终端改为紧凑主要按钮。位置、隐藏文件、输入路径、完整路径、刷新、手机授权、其他来源、复制、分享/保存和原有各类预览都保留。
- 设置按服务、终端、关于分组；账号、各服务配置和帮助改为完整页面。账号每行有服务图标、状态和操作，点击服务直接选择对应模型配置。国内版沿用同一界面且没有 Codex、Claude Code、Antigravity 入口。
- 问 AI/看报错面板去掉解释性段落及会话路径副标题，保留任务输入、后端/模型、生成，以及次要的上下文检查和账号配置入口。生成后的命令仍可编辑，只有明确确认才运行。弹窗共用主题与主次按钮；权限、复制、粘贴和关闭等决策所需说明仍保留。
- Android 原生通知授权、文件选择器及系统授权页由系统绘制，不能由应用替换其按钮或配色。本次保留原系统询问流程和允许/拒绝行为，更新其后的应用画面；没有另加一层自制权限弹窗。截图中的系统控件颜色不代表应用新增浅色主题。

### 试用与折叠模拟

1. 使用 `apps/android/gradlew` 构建国际版（默认）或加 `-PwaddleDistribution=domestic` 构建国内版；运行时分别由 `tools/prepare-runtime.py --distribution …` 准备，不使用任何账号或 Key。
2. 打开应用看终端短路径；顶部文件入口进入 `Waddle 文件`，尝试列表/网格、目录菜单及当前目录打开终端。左侧导航切换会话，进入设置、账号、模型与帮助。
3. 国际版终端输入 `codex`，选择并可记住界面；对话页查看空状态、历史、账号，返回终端后通过进行中提示回到同一对话。真实登录、生成和历史继续任务需验收者自行提供账号。
4. 全部设备操作限定 **Youzai_API_35 / emulator-5556 / API 35 / arm64-v8a**，没有操作真机或另一个 AVD。使用 **`adb shell wm size / density` 切换**，没有新建 AVD：合上为 `1080x2520 / 420dpi`，展开竖屏为 `1968x2184 / 360dpi`。像素规格参照 [三星 Fold7 规格](https://news.samsung.com/global/samsung-galaxy-z-fold7-raising-the-bar-for-smartphones)，density 是本次布局测试设置，不是宣称复刻 One UI 的逻辑密度。
5. 宽度达到 840dp 使用常驻 256dp 左导航，更窄时用抽屉。`ProductUiIntegrationTest` 在展开→合上→展开之间检查同一 PTY、cwd、终端输出、对话消息和未发送草稿；测试结束恢复原 size/density。配置变化不重建服务持有的 shell 或对话。对话草稿也移到服务持有的 `CodexController`，修复初次动态折叠测试发现的页面切换丢草稿问题。

### 验证与限制

- 本地构建平台为 macOS 26.3.1 / Apple Silicon / JDK 17；设备为上述 Android 15 API 35 ARM64 模拟器。桌面检查使用已有依赖与本机 Node 25，CI 的 Linux / Node 22 环境仍需远端验收。
- 最终 UI 回归另暴露一次视频切换到音频后无播放控件的超时。纯音频原先复用依赖 SurfaceHolder 的 VideoView（参照 [Android 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/widget/VideoView.java)）；现改用独立 MediaPlayer，保留播放/暂停/进度拖动，并处理音频焦点、后台暂停与页面关闭释放。设备测试连续两轮切换视频和音频，并等待前一个查看器实际消失，不删断言或提高超时来掩盖失败。
- 构建矩阵 **国际 / 国内 × ARM64 / x86_64** 全部通过 `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`；终端组件宿主单测 **145/145** 通过。四次 app lint 均为 **0 error / 36 warning / 3 hint**，未声称零警告；warning 主要为依赖更新、API/兼容性建议和代码风格。x86_64 只验证构建，未在 Apple Silicon 上运行 x86_64 模拟器。运行时生成目录最后恢复为 ARM64。
- 最终四组合设备回归全部通过：每组 **61 测试 / 0 失败 / 0 错误**；国际允许、拒绝各 **7 跳过**，国内允许、拒绝各 **11 跳过**。统计与复现命令见 [23-01 最终回归记录](2026-11-23-01-android-ci-green.md#最终设备回归23-02-全部界面与播放器修复后)。
- 完整回归保留原来的 shell、文件、AI、审批、历史与多媒体检查；只调整与新导航名称/图标对应的查找方式，并补充列表/网格、完整账号页、真实通知权限和动态折叠状态检查。
- 截图来自模拟器真实运行的 Compose 页面。示例目录为 `~/demo`；命令、diff、审批中的示例内容由截图测试注入，明确写有「演示内容」，不代表真实账号完成的任务。
- 桌面端仅做既有检查：`npm test -- --run` 为 **37 个文件 / 200 测试通过**，`npx tsc --noEmit` 通过，`NODE_OPTIONS=--max-old-space-size=8192 npm run build:dev` 通过。开发构建首次用默认 Node heap 内存不足，按工作流的 8 GiB 设置重跑成功；没有修改桌面代码。
- 本次没有真实 Codex 登录、DeepSeek 等服务 Key，因此真实服务调用、登录后任务和真实历史 session 继续聊天未执行；保留原 opt-in 测试和原产品功能。未填写或使用真实 Key；既有离线测试沿用隔离的模拟凭据，不写入用户账号。
- 未 push、未 commit、未切换或创建分支。GitHub Android 与 Desktop checks 的远端结果以及连续两次全绿需提交后验收，本地构建和测试不能冒充远端结果。

### 截图

改前图片是在修改界面源文件前采集，文件名均以 `2026-11-23-02-` 开头，保存在 [screenshots](screenshots/)；手机默认竖屏另有 Fold7 两种模拟尺寸。部分改前图片保留了当时 Android 的剪贴板浮层，未修图。


### 核心界面对照

| 尺寸 / 界面 | 改前 | 改后 |
|---|---|---|
| 手机竖屏 · 终端 | <img src="screenshots/2026-11-23-02-before-phone-terminal.png" width="220" alt="改前 终端"> | <img src="screenshots/2026-11-23-02-after-phone-terminal.png" width="220" alt="改后 终端"> |
| 手机竖屏 · AI 对话（演示命令 / diff） | <img src="screenshots/2026-11-23-02-before-phone-chat-messages.png" width="220" alt="改前 AI 对话（演示命令 / diff）"> | <img src="screenshots/2026-11-23-02-after-phone-chat-messages.png" width="220" alt="改后 AI 对话（演示命令 / diff）"> |
| 手机竖屏 · 导航（终端 + 对话） | <img src="screenshots/2026-11-23-02-before-phone-drawer-conversation.png" width="220" alt="改前 导航（终端 + 对话）"> | <img src="screenshots/2026-11-23-02-after-phone-drawer-conversation.png" width="220" alt="改后 导航（终端 + 对话）"> |
| Fold7 合上 · 终端 | <img src="screenshots/2026-11-23-02-before-folded-terminal.png" width="220" alt="改前 终端"> | <img src="screenshots/2026-11-23-02-after-folded-terminal.png" width="220" alt="改后 终端"> |
| Fold7 合上 · AI 对话（演示命令 / diff） | <img src="screenshots/2026-11-23-02-before-folded-chat-messages.png" width="220" alt="改前 AI 对话（演示命令 / diff）"> | <img src="screenshots/2026-11-23-02-after-folded-chat-messages.png" width="220" alt="改后 AI 对话（演示命令 / diff）"> |
| Fold7 合上 · 导航（终端 + 对话） | <img src="screenshots/2026-11-23-02-before-folded-drawer-conversation.png" width="220" alt="改前 导航（终端 + 对话）"> | <img src="screenshots/2026-11-23-02-after-folded-drawer-conversation.png" width="220" alt="改后 导航（终端 + 对话）"> |
| Fold7 展开 · 终端 | <img src="screenshots/2026-11-23-02-before-unfolded-terminal.png" width="220" alt="改前 终端"> | <img src="screenshots/2026-11-23-02-after-unfolded-terminal.png" width="220" alt="改后 终端"> |
| Fold7 展开 · AI 对话（演示命令 / diff） | <img src="screenshots/2026-11-23-02-before-unfolded-chat-messages.png" width="220" alt="改前 AI 对话（演示命令 / diff）"> | <img src="screenshots/2026-11-23-02-after-unfolded-chat-messages.png" width="220" alt="改后 AI 对话（演示命令 / diff）"> |
| Fold7 展开 · 导航（终端 + 对话） | <img src="screenshots/2026-11-23-02-before-unfolded-drawer-conversation.png" width="220" alt="改前 导航（终端 + 对话）"> | <img src="screenshots/2026-11-23-02-after-unfolded-drawer-conversation.png" width="220" alt="改后 导航（终端 + 对话）"> |

### 全部页面、面板和决策弹窗（手机竖屏）

| 界面 | 改前 | 改后 |
|---|---|---|
| 对话未登录 | [改前](screenshots/2026-11-23-02-before-phone-chat-empty.png) | [改后](screenshots/2026-11-23-02-after-phone-chat-empty.png) |
| 返回进行中的对话 | [改前](screenshots/2026-11-23-02-before-phone-terminal-conversation.png) | [改后](screenshots/2026-11-23-02-after-phone-terminal-conversation.png) |
| 文件位置 | [改前](screenshots/2026-11-23-02-before-phone-locations.png) | [改后](screenshots/2026-11-23-02-after-phone-locations.png) |
| 文件默认视图 | [改前](screenshots/2026-11-23-02-before-phone-files.png) | [改后](screenshots/2026-11-23-02-after-phone-files.png) |
| 文件网格 | [改前](screenshots/2026-11-23-02-before-phone-files.png) | [改后](screenshots/2026-11-23-02-after-phone-file-grid.png) |
| 文件夹操作 | [改前](screenshots/2026-11-23-02-before-phone-folder-actions.png) | [改后](screenshots/2026-11-23-02-after-phone-folder-actions.png) |
| 文件菜单 | [改前](screenshots/2026-11-23-02-before-phone-file-menu.png) | [改后](screenshots/2026-11-23-02-after-phone-file-menu.png) |
| 输入路径 | [改前](screenshots/2026-11-23-02-before-phone-path-dialog.png) | [改后](screenshots/2026-11-23-02-after-phone-path-dialog.png) |
| 手机存储授权说明 | [改前](screenshots/2026-11-23-02-before-phone-storage-permission.png) | [改后](screenshots/2026-11-23-02-after-phone-storage-permission.png) |
| 复制确认 | [改前](screenshots/2026-11-23-02-before-phone-import-confirmation.png) | [改后](screenshots/2026-11-23-02-after-phone-import-confirmation.png) |
| 文件预览 | [改前](screenshots/2026-11-23-02-before-phone-viewer.png) | [改后](screenshots/2026-11-23-02-after-phone-viewer.png) |
| 设置 | [改前](screenshots/2026-11-23-02-before-phone-settings.png) | [改后](screenshots/2026-11-23-02-after-phone-settings.png) |
| 账号 | [改前](screenshots/2026-11-23-02-before-phone-accounts.png) | [改后](screenshots/2026-11-23-02-after-phone-accounts.png) |
| Codex 账号与界面 | [改前](screenshots/2026-11-23-02-before-phone-codex-account.png) | [改后](screenshots/2026-11-23-02-after-phone-codex-account.png) |
| AI 模型 | [改前](screenshots/2026-11-23-02-before-phone-models.png) | [改后](screenshots/2026-11-23-02-after-phone-models.png) |
| 问 AI | [改前](screenshots/2026-11-23-02-before-phone-command-sheet.png) | [改后](screenshots/2026-11-23-02-after-phone-command-sheet.png) |
| 看报错 | [改前](screenshots/2026-11-23-02-before-phone-diagnosis-sheet.png) | [改后](screenshots/2026-11-23-02-after-phone-diagnosis-sheet.png) |
| 帮助 | [改前](screenshots/2026-11-23-02-before-phone-help.png) | [改后](screenshots/2026-11-23-02-after-phone-help.png) |
| Codex 界面选择 | [改前](screenshots/2026-11-23-02-before-phone-interface-choice.png) | [改后](screenshots/2026-11-23-02-after-phone-interface-choice.png) |
| 历史对话 | [改前](screenshots/2026-11-23-02-before-phone-history.png) | [改后](screenshots/2026-11-23-02-after-phone-history.png) |
| 命令审批 | [改前](screenshots/2026-11-23-02-before-phone-approval.png) | [改后](screenshots/2026-11-23-02-after-phone-approval.png) |
| 结束对话确认 | [改前](screenshots/2026-11-23-02-before-phone-end-conversation.png) | [改后](screenshots/2026-11-23-02-after-phone-end-conversation.png) |
| 关闭终端确认 | [改前](screenshots/2026-11-23-02-before-phone-close-terminal.png) | [改后](screenshots/2026-11-23-02-after-phone-close-terminal.png) |
| 粘贴预览 | [改前](screenshots/2026-11-23-02-before-phone-paste.png) | [改后](screenshots/2026-11-23-02-after-phone-paste.png) |
| 首次通知授权（系统） | [改前](screenshots/2026-11-23-02-before-phone-notification.png) | [改后](screenshots/2026-11-23-02-after-phone-notification.png) |

国内版补充：[账号](screenshots/2026-11-23-02-after-domestic-phone-accounts.png)、[终端](screenshots/2026-11-23-02-after-domestic-phone-terminal.png)、[设置](screenshots/2026-11-23-02-after-domestic-phone-settings.png)、[模型](screenshots/2026-11-23-02-after-domestic-phone-models.png)、[文件](screenshots/2026-11-23-02-after-domestic-phone-files.png)。国内版没有海外工具入口；这组共 19 张，按 `after-domestic-phone` 查找。

本次共归档 **82 张改前 / 104 张改后**；改后含国际版三种尺寸的 84 张、系统首次通知询问 1 张和国内版 19 张。截图测试在三个尺寸及国内版均通过。最后清理截图用演示数据与测试 APK，模拟器恢复默认 `720x1520 / 320dpi`，保留最终国际版供试用。

其余页面也保留了合上和展开截图，可在 screenshots 中按 `before/after-folded`、`before/after-unfolded` 查找相同后缀。截图采集入口为 `UiScreenshotTest`（默认跳过），运行方法见 [Android README](../../apps/android/README.md#android-design-and-responsive-layout)。

## 验收记录（验收者填写）

验收日期 2026-11-24，验收者 Claude。对照未提交的工作区（基于 `91b8800f`）。

做了三件事：

- 看了实现说明里的改前 / 改后截图。
- 在模拟器 `Waddle_API_35`（API 35，arm64）上装了当前工作区的国际版，自己走了一遍主要功能。
- 重跑了四组设备测试，结果见 [23-01 验收记录](2026-11-23-01-android-ci-green.md#验收记录验收者填写)。

本次截图在 Mac mini 的 `artifacts/acceptance-2026-11-24/ui/`（不进仓库），挑了几张放进 `docs/dev/screenshots/2026-11-23-02-accept-*`。

### 阶段 1

- **看不到 ✦、渐变、发光，只有一个强调色（做到）**：
  - 终端页、对话页、抽屉的截图和实机上都没有星星、渐变、发光。安卓源码里也搜不到 `✦`、`RUNNING`、`Bash · Waddle`、Gradient。
  - 强调色是一种蓝色，只用在主要按钮（打开、允许本次、去账号页登录、打开终端）和当前选中项（抽屉当前会话、单选框）上。
- **路径显示 `~`，没有调试文字（做到）**：
  - 顶部、抽屉、提示符都显示 `~`、`~/demo`、`~/验收 '引号' 目录`。
  - 终端底部没有「RUNNING」「Bash · Waddle」。
- **对话页顶部和消息样式（做到）**：
  - 顶部是「回到终端 / 历史对话 / 账号 / 结束对话」四个图标，不再是一排绿字。
  - 命令块（等宽，带「命令」标签）、文件修改（文件名 + 红 `-` / 蓝 `+` 行）、审批（实心「允许本次」+ 文字「拒绝」）三种样式明显不同（演示截图）。
  - 没登录时有一个像样的空状态：图标、标题、三项能力、主按钮「去账号页登录」。
- **抽屉列表项统一（做到）**：终端和对话都是「图标 + 名字 + 目录 · 状态」，当前项是同一种蓝底选中样式。
- **04 阶段 1 和 05 的功能都还在（做到，实机走了一遍）**：
  - 终端敲 `codex` → 弹「用哪种界面打开 Codex？」→ 勾「记住我的选择」→ 打开对话界面。
  - 再敲 `codex`，直接打开对话，不再询问。
  - 账号页 → Codex「管理」里能看到并改回「每次询问」。
  - 「回到终端」后，终端底部有「工作区 · Codex (1) · 对话进行中 · 返回对话」，点它回到同一个对话。
  - 「历史对话」能打开（没登录时显示「还没有历史对话」，有刷新和关闭）。
  - 「账号」打开完整账号页。
  - 「结束对话」有确认，结束后对话从抽屉里消失，终端不受影响。
- **设计规范和截图（做到）**：
  - 规范在 `docs/dev/android-design-system.md`，写了颜色、字号、间距、圆角、图标、布局和文案规则。
  - 实现说明里有终端 / 对话 / 抽屉在手机、Fold7 合上、Fold7 展开三种尺寸下的改前 / 改后截图。

### 阶段 2

- **文件页、账号页、设置、问 AI 面板（做到）**：
  - 文件页默认是列表（图标、名字、大小），右上角能切换网格。「打开终端」是底部一个紧凑按钮，不再占整行。
  - 账号是完整页面，分「编程工具」「内置模型」，每个服务一行（图标、名字、状态、操作）。
  - 设置分「服务 / 终端 / 关于」三组。
  - 问 AI 面板只有模型切换、模型名、「查看本次上下文」、输入框、生成 / 取消，没有解释性长句。
- **全应用无 ✦ 和解释性副标题，按钮主次分明（做到）**：
  - 看了 25 组手机竖屏改后截图和实机页面，没有星星。
  - 主操作都是一个实心按钮，其余是文字或图标。
- **国内版（做到）**：
  - 国内版截图 19 张，账号页只有「内置模型」，没有 Codex、Claude Code、Antigravity。
  - 国内版两组设备测试都通过，其中有断言账号页和导航里不出现 Codex / Claude / Antigravity。
  - 国内版运行时是 26 个原生程序，国际版 28 个，海外工具没有打进国内版。
  - 国内版没有在实机上手动走，靠测试和截图。
- **Fold7 尺寸展开 / 合上（做到，模拟器）**：
  - 用 `wm size` 切到 1968×2184 / 360dpi：左侧常驻导航，右边终端。
  - 切回 1080×2520 / 420dpi：恢复抽屉布局。
  - 两次切换中，终端输出、会话列表、进行中的对话都还在。
  - `ProductUiIntegrationTest.foldResizeKeepsPtyMessagesAndUnsentDraft` 在四组里都通过。
  - 截图：[展开](screenshots/2026-11-23-02-accept-unfolded.png)。
- **原有功能、设备测试（做到）**：
  - 文件夹里打开终端、问 AI、看报错（`退出码 127 · 让 AI 看看` → 「分析报错」面板）、文件、多会话、关闭确认都还在。
  - 四组设备测试的结果和第一轮那次通知用例失败，见 23-01 记录。
- **每个界面的改前 / 改后截图（做到）**：82 张改前、104 张改后（含国内版 19 张）。最大的一张 333 KB，总共约 21 MB，单个文件远低于 GitHub 限制。

### Fold7 真机

没有看成。Fold7 已经连上（`RFCY71BWLPD`），但处于锁屏、合上状态。按约定不输入解锁图案，所以跳过了。只读确认了两点：

- 用户 0 装的是 `0.6.0-international`（更新于 2026-11-23 21:33）。
- 用户 95 没有安装 Waddle。

没有卸载、清数据或安装。**需要用户在 Fold7 上自己看一下展开和合上两种布局。**

### 小问题（不影响验收，另有打磨需求会处理）

- 有消息时，对话页底部的「去账号页登录」看起来像普通文字，不像按钮。没有消息的空状态里它是实心按钮，两处不一致。
- 终端底部会叠起两到三行提示：「对话进行中 · 返回对话」「退出码 … · 让 AI 看看」「问 AI，写命令」。前两行居中，第三行左对齐，看起来散。[截图](screenshots/2026-11-23-02-accept-terminal-hints.png)
- 账号页内置模型（DeepSeek、通义千问、Kimi、智谱、自定义）全用同一个人形图标，区分不出。
- 会话名「demo · 2」「工作区 · Codex (1)」读起来别扭：编号是全局序号，第一个 demo 终端也可能叫「· 2」。
- 「用哪种界面打开 Codex？」弹窗里，目录单独一行只显示一个「~」。「记住我的选择」的勾选框和上面的单选项没有对齐。[截图](screenshots/2026-11-23-02-accept-codex-choice.png)
- 「Codex 账号与界面」页：
  - 右上角有文字「返回账号」，和左上角返回箭头重复。
  - 底部直接露出 `codex --waddle-chat / --waddle-terminal` 这行命令参数。
  - 「刷新登录状态」的缩进和上面的按钮不一致。
- 「让 AI 看看 · 解释报错」面板的输入框标签还是「想去哪里，或想做什么？」，那是问 AI 写命令时用的，放在这里不对。
- 无障碍名称：账号页返回按钮读作「返回账号」，设置页读作「返回设置与运行环境」。读起来像是「返回到账号」，但实际是返回上一页。
- 用实体键盘时，从设置页返回终端后，焦点停在导航按钮上，直接打字不会进终端，回车会打开抽屉，要先点一下终端。软键盘用户不受影响。

- CI：待推送后补。
- 结论：阶段 1、2 在模拟器上全部做到，Fold7 真机待用户看。是否通过要看 GitHub 上 Android 和 Desktop checks 的结果。
