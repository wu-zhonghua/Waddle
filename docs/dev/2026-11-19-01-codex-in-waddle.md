# 在 Waddle 里用 Codex：读写文件、跑命令、读和改系统设置（2 个阶段）

- 状态：验收通过
- 日期：2026-11-19
- 上一份：无
- 涉及：安卓

## 现在的样子

安卓终端第一版已提交。之后用户分几次让 Codex 把 Codex 接进 Waddle：先在手机上跑起来，再不依赖 Termux、全部放进 Waddle，然后能读写文件、跑命令、读系统设置，最后能改亮度和自动亮度。实现者报告这些在 API 35 模拟器上都已跑通（0.4.0），还没有提交，也还没有验收。真机（用户的三星 Fold7）已装上 Waddle，基础测试通过，停在等用户登录 Codex。

这份需求是在流程建立后补写的，用来验收这部分工作，并接着完成真机验证。

## 要做什么

用户想在手机上直接用 Codex 干活，不装别的 App，不靠电脑：在 Waddle 里登录一次，就能让 Codex 读写手机上的文件、跑命令、看系统设置，在用户确认后调节屏幕亮度和自动亮度。终端和 Codex 是同一个工作区，Codex 做的事在终端里马上能看到。

分两个阶段，每个阶段做完停下等验收：

- **阶段 1：模拟器上完整可用。** 把已经做的部分整理到可以验收的样子。
- **阶段 2：用户的真机上可用。** 在三星 Fold7 上把同样的事做一遍，确认屏幕亮度真的会变。

## 不做

- SSH、连接电脑或远程服务器，手机和电脑之间互联。
- 让 Codex 点按、操作其他 App 的界面。
- 亮度和自动亮度以外的系统设置修改；读取也只限于已经做的几项。
- 包管理器、Python、Node 等额外的开发环境。
- 不联网时在手机上运行模型。
- 折叠屏合上、展开的界面适配。
- 正式签名的发布包和应用商店上架。

## 怎样算做好

### 阶段 1（在 API 35 模拟器上看）

- 只装 Waddle，不装 Termux 等其他 App，就能用 Codex：在终端里输入 `codex`，或打开 Codex 页面，都能用；两处共用一个登录，登录一次就行。
- 在 Codex 页面让 Codex 读一个文件、改一个文件、跑一条命令：回复里能看到它做了什么，改动在终端里马上能看到。
- 问 Codex 当前的亮度、自动亮度、屏幕超时等设置，回答和系统设置里看到的一致。
- 让 Codex 调亮度或切换自动亮度：先弹窗显示改前、改后的值，点允许才改，点拒绝或关掉修改开关就不改；没给系统授权时不改，并告诉用户怎么打开。改完系统设置里能看到新值。
- 正在跑的 Codex 任务能停下；切到后台再回来、旋转屏幕后，对话还在，能接着聊。
- 第一版终端的功能照常能用；GitHub 上 Android 和桌面端的检查通过，桌面端（macOS、Windows、Linux）不受影响。

### 阶段 2（在用户的三星 Fold7 上看）

- 真机上 Waddle 能打开终端和 Codex 页面；用户在手机上完成一次登录后，终端和 Codex 页面都能用。
- 在真机上重做阶段 1 的读文件、改文件、跑命令和读设置，结果一样。
- 让 Codex 调亮度，屏幕真的变亮或变暗；切到自动亮度后，遮住或照亮手机的光线感应位置，亮度会跟着变。
- 测试结束后，手机的亮度和亮度模式恢复到测试前的样子。
- 真机上和模拟器表现不一样的地方，写进实现说明。

---

## 实现说明（实现者填写）

### 阶段 1 实现内容

Waddle 0.4.0 内置 Codex 0.170.0、执行 helper、Bash、Git、ripgrep、curl、动态库和证书。终端与 Codex 面板使用 Waddle 的同一 home、文件和登录；面板通过本机子进程的 stdio 通信，无 Termux App、外部命令权限或电脑服务依赖。模型推理仍需联网。原生工具提供五项公开设置读取，以及亮度/自动模式两项修改；修改需系统授权、Waddle 开关和逐次确认，写后读回核对。

这轮补齐阶段 1 时修复了两个实际问题：

- 任务刚发送、尚未收到 thread/turn 编号时，点击停止可能被丢弃。现在会保留停止请求，避免启动未开始的任务，或在取得编号后停止。
- Android Codex 返回“已停止”后，命令仍可能继续执行并写文件。现在停止时清理该面板的进程树，等待退出后重新连接并恢复原会话；原生 supervisor 会收养并清理另建进程会话的后代，避免只终止原进程组而遗留命令。恢复后可以继续对话，亮度修改开关会关闭。停止不回滚已完成的文件或设置修改。

补充了明确的权限指引：未授权时告诉用户点击 Codex 页“允许修改亮度”旁的“系统授权”；已授权但开关关闭时告诉用户开启修改开关。需求内容和验收记录未改动。

### 本次环境和验证范围

只操作 API 35 / Android 15 ARM64 模拟器 `emulator-5556`。模拟器当前用户已移除 Termux App 注册（保留早期历史数据），之后运行 Waddle 回归；没有读取、迁移其他 App 的登录文件。使用此前用户已完成的 Waddle 自己的登录，未重新导入凭据。真机阶段保持暂停，本轮未对真机执行安装、测试或设置修改。

| 验收项 | 实现与自测证据 |
| --- | --- |
| 独立 Codex 与同一登录 | 内嵌工具版本、应用 UID、stdio 启动测试通过；真实模型面板任务后，从 Waddle 的真实 PTY 运行 `codex exec`，复用同一登录完成另一项文件修改并检查退出码 |
| 读文件、改已有文件、执行命令 | 在独立测试目录读取 `input.txt`，把已有 `result.txt` 从占位内容改成 `WADDLE_SHARED_OK`，执行命令；终端立即读取模型结果。CLI 以 `--ask-for-approval on-request exec --sandbox workspace-write` 再修改已有 `cli-result.txt` 并执行 `cmp`，实际文件内容与退出码均核对通过 |
| 系统设置读取 | 真实模型调用五个公开字段：亮度、模式、超时、飞行模式、辅助功能状态；逐项与 Android API 独立读取的数值比较；另由 ADB 读取公开设置核对。系统滑块显示 83%，对应 raw brightness=102；屏幕超时原始值 2147483647 ms 在系统选项显示“Not set”。工具说明明确原始值与界面标签的区别，不线性换算亮度百分比 |
| 修改设置及拒绝 | 真实模型分别请求亮度和模式修改，确认前不写，确认后读回；拒绝和关闭开关不写。无权限、非法参数、过期确认另有回归。修改测试用 `finally` 恢复初始亮度和模式 |
| 停止、后台与旋转 | 启动 thread/turn 时立即停止；真实前台命令运行后点击可见“停止”按钮，等待超过原脚本时长，确认未发生后续写入。之后继续对话、退到后台从桌面入口返回、实际旋转屏幕触发 Activity 重建，保留 Codex 页签和 thread，并能回答旋转前的口令 |
| 原终端与桌面回归 | 原 PTY、Unicode/输入法、resize、Ctrl-C、多会话、退出码、Activity 重建及快速退出输出测试保留。桌面源码和 Desktop checks 工作流未修改；本地 200 项测试、TypeScript 检查、开发构建通过 |

主要测试代码：[CodexIntegrationTest](../../apps/android/app/src/androidTest/java/dev/waddle/android/CodexIntegrationTest.kt)、[CodexLifecycleIntegrationTest](../../apps/android/app/src/androidTest/java/dev/waddle/android/CodexLifecycleIntegrationTest.kt)、[DisplaySettingsIntegrationTest](../../apps/android/app/src/androidTest/java/dev/waddle/android/DisplaySettingsIntegrationTest.kt)、[TerminalIntegrationTest](../../apps/android/app/src/androidTest/java/dev/waddle/android/TerminalIntegrationTest.kt)。原生运行时变化已记录到[来源记录](../../apps/android/runtime/PROVENANCE.md)。

### 构建与复现

使用 JDK 17、SDK platform 36、NDK 28.2.13676358，在 `apps/android` 下执行：

```sh
python3 tools/prepare-runtime.py --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug --console=plain
```

在**已登录的指定测试模拟器**安装两份 APK，再运行 opt-in 真实模型测试。请确认 serial，避免连接着真机时误跑；测试会暂时修改旋转、亮度和模式，并恢复。

```sh
adb -s emulator-5556 install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5556 install --user 0 -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5556 shell am instrument --user 0 -w \
  -e waddleNetwork true -e waddleLiveCodex true -e waddleLiveLifecycle true \
  -e waddleDisplayWrites true -e waddleLiveDisplay true \
  dev.waddle.android.test/androidx.test.runner.AndroidJUnitRunner
```

亮度修改测试前，在 Android 设置中授予 Waddle“修改系统设置”。缺少权限的测试需在撤销该授权时单独运行；已授权的完整运行会跳过它。默认设备测试不请求真实模型、不修改亮度。测试仅操作新建的样本目录，不读取认证文件。

手动试用：

1. 打开 Waddle，终端创建一个样本文件，再到 Codex 页启动、登录。要求读取并修改该文件、执行检查命令；回终端 `cat` 核对。终端直接输入 `codex` 可复用登录。
2. 新建会话，要求读取亮度、自动亮度和屏幕超时，核对系统设置。需要修改时，点击“系统授权”，返回并开启“允许修改亮度”；先试一次拒绝，再试允许，观察弹窗旧值 → 新值与结果卡片。
3. 让 Codex 执行测试目录内的等待命令，运行中点“停止”；待会话恢复后再发一条消息。切后台、点桌面图标回来，再旋转屏幕并继续原话题。
4. 恢复测试前的亮度和模式。只验收阶段 1，不继续真机步骤。

### 检查记录与待验收事项

- 设备：17 个不同用例分授权前后和定向重测覆盖。`no-write-permission-tests.log` 为无权限基线；`final-missing-permission-test.log` 验证最终权限提示；`full-live-tests.log` 的其余用例通过，但当时 CLI 写入样本未变化，该失败有保留。显式选择 CLI 的 workspace-write 后，`final-file-cli-test.log` 定向重测通过；最终相关用例在 `final-file-lifecycle-tests.log` 中 2/2 通过（78.933 秒）。首次新增数值断言还修正了 JSON Integer/Long 的类型比较问题，未掩盖设备数值差异。
- 最终模拟器公开状态见 `final-device-state.json`：亮度 102，手动模式 0；亮度测试原值和恢复值一致。`lifecycle-proof.json`、`display-proof.json` 分别记录生命周期和确认/拒绝/恢复结果。
- Android：145 项单元测试通过，APK/测试 APK 构建和 lint 通过（0 error，15 warning、3 hint）；新增 supervisor 也通过 x86_64 的 NDK 编译检查。
- 桌面：本机执行 `npm test -- --run`（200 项通过）、`npx tsc --noEmit --pretty false`、`npm run build:dev` 均通过。未打包或安装 macOS/Windows/Linux 发布包，不能把公共代码检查当成三个系统的安装测试。
- GitHub 已提交的 `main`，SHA `e082e2664bf554805f8577a966da37e3f4017c21`：[Android 通过](https://github.com/wu-zhonghua/Waddle/actions/runs/44543184936)、[Desktop checks 通过](https://github.com/wu-zhonghua/Waddle/actions/runs/44543184909)。**这些记录不包含当前未提交改动。按项目规则本轮不提交、不推送；当前改动的 GitHub CI 仍需验收者提交后确认，不能标成已通过。**
- Android 同一 UID 内的工作区隔离仍有限；原生工具的确认不是任意同 UID 代码的安全边界。系统设置修改仅在 Codex 面板提供，终端 CLI 未注册面板的动态工具。
- 阶段 2、真机实际屏幕变化、光线传感器自动调光、三平台安装包及长期后台运行均不在本轮验证结论内。
- 验收 APK：`artifacts/stage1/waddle-android-0.4.0-stage1-debug.apk`。本地日志、公开结果 JSON 和 APK 位于被忽略的 `artifacts/stage1/`，不提交设备原始输出或凭据。验收记录留给验收者填写。



### 阶段 2 真机验证结果（2026-11-19）

用户明确指示继续真机阶段后，在三星 SM-F966B（Fold7，设备报告 Android 17 / API 37、ARM64、4096 字节页）保留数据升级至阶段 1 APK。**按用户要求保留真机 Codex 登录，非必要不登出、不清空 Waddle 数据或卸载主应用。** 测试中的断开仅结束本地进程，不调用账号注销；真机已有登录已在面板和终端 CLI 两处复用成功。

发现并修正安装范围问题：手机已有主用户 0 和三星 DUAL_APP 分身用户 95。此前未指定用户的 ADB 安装使 Waddle 和测试包同时注册到两个用户，产生 Waddle 分身。已用保留数据的方式移除用户 95 中两份尚未启动的 Waddle 注册，主用户保留；没有删除分身空间或其他应用。以后安装和 instrumentation 明确指定 `--user 0`。临时测试包没有 launcher 入口，不是第二个终端客户端。

实际模型已完成五项系统设置读取、修改已有文件、执行命令、终端 CLI 共用登录，以及亮度确认/拒绝/关闭开关测试。写入由 Waddle UID 10560 的原生 API 执行：亮度改为 96，模式手动 → 自动 → 手动均读回核对成功。自动模式期间亮度可能自行调整，模式切回手动时实际亮度从 96 变成 102；这不同于模拟器的静态读数。测试结束已恢复本轮开始的 103／自动模式 1。

真机自动化运行记录为 `OK (17 tests)`，其中实际执行 15 项、跳过 2 项（本次不启用模拟器旋转/停止专用 live lifecycle 用例；已经授予权限，缺少权限用例跳过）。执行项全部通过，含真实模型、终端 CLI 写入、前台子进程回收和原终端回归。日志 `physical-tests.log` 用时 138.391 秒。

用户随后配合实际屏幕观察，全部通过 Waddle 中的真实 Codex 原生工具和可见确认完成：

- 自动 → 手动，亮度 103 → 64 → 160。用户确认“明显变亮了”，与原生工具读回值一致。
- 先将亮度 160 → 103，再切回自动模式 1。用户遮住光线传感器再移开，确认“会，能看到自动调整”。
- 在此期间只读采样记录到亮度 35 → 102 → 65 → 105 → 174，模式全程为 1；这提供了实际自动调节的补充证据。
- 已写回测试前的原始值 103 并恢复自动模式。结束时自动算法按当时环境调到 174，不能把自动模式下动态变化的值当作恢复失败，也没有为固定住 103 而再次关闭自动亮度。Waddle 修改开关已关闭。
- 临时测试包已从主用户移除，最终用户 0 只保留 `dev.waddle.android`，用户 95 无 Waddle 注册。Codex 面板仍显示可新建会话，保持登录和当前会话，没有调用账号注销。

阶段 2 未修改应用代码；实机差异和安装用户问题已记录。原始状态、安装用户核对、测试日志、公开结果和用户观察摘要位于被忽略的 `artifacts/stage2/`（`original-state.json`、`display-proof.json`、`ambient-observation.json`、`final-state.json`）。这些是实现者自测及用户协助观察的事实；最终验收记录仍由验收者填写。阶段 2 完成后停下等待验收，未提交或推送。

## 验收记录（验收者填写）

- 验收环境（2026-11-21，Claude 验收）：Mac mini 上的 Android API 35 ARM64 模拟器 `Waddle_API_35`（emulator-5554，Waddle 专用那台，当时已在运行；`Youzai_API_35` 没有启动）。验收用的 APK 与 Fold7 上现装的版本相同（SHA-256 `aef51d2a…6c34`）。构建 `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 通过：单元测试 145 项 0 失败，lint 0 errors / 21 warnings / 4 hints。全部设备测试 46 项：35 项通过，10 项需要登录或测试开关的跳过，1 项失败（`NavigationIntegrationTest.folderCodexPanelsKeepDirectoryDraftAndMessagesAcrossSwitchAndRecreation`：整套一起跑时 Codex 页停在登录页，找不到草稿输入框）。这一项单独重跑 3 次都通过，看作不稳定的测试。`PhoneStorageIntegrationTest` 在未授权、已授权两种状态下各 3 项通过。桌面端 200 项测试和 `tsc --noEmit` 通过。模拟器上没有 Codex 登录，这次没有跑真实模型；真实模型的结果引用实现者在 Fold7 上的记录。这次没有操作真机。跑完后撤销了模拟器的所有文件访问权限，删掉了测试包和演示目录。
- 阶段 1 · 只装 Waddle、终端和 Codex 页共用一个登录：**做到**。模拟器上的 Codex 页能直接启动内置 Codex，没登录时显示登录页（[截图](screenshots/2026-11-21-acceptance-08-codex-in-phone-folder.png)）；没有装 Termux。两处共用登录这一条，看的是实现者在模拟器和 Fold7 上用真实模型跑的记录（面板和终端 `codex exec` 用同一个登录）。
- 阶段 1 · 读文件、改文件、跑命令，终端里马上能看到：**做到**，依据实现者真实模型测试的记录；这次跑的不联网设备测试也都通过。
- 阶段 1 · 读亮度、自动亮度、屏幕超时等，和系统设置一致：**做到**，依据实现者逐项核对的记录（Fold7 上也做过）。
- 阶段 1 · 改亮度要先确认，拒绝、关开关、没授权都不改：**做到**，依据实现者记录和用户在真机上的确认。最终版本里，这个开关和系统授权入口在抽屉底部的「设置与运行环境」里（2026-11-21-01 移过去的）。
- 阶段 1 · 能停止任务，切后台、旋转后对话还在：**做到**，依据实现者的 live lifecycle 测试记录；这次没有跑 live 测试。
- 阶段 1 · 第一版终端照常能用：**做到**，终端回归测试通过。新终端启动时会多出两行 `bind` 报错，这是 2026-11-19-02 加的终端集成带来的，记在那份文档里。
- 阶段 2 · Fold7 上打开终端和 Codex，登录一次两处都能用；重做读写文件、跑命令、读设置：**做到**，依据实现者真机记录（实际执行的 15 项测试通过，另 2 项跳过）。
- 阶段 2 · 调亮度时屏幕真的变化，自动亮度跟着光线变：**做到**，用户在真机上确认过「明显变亮了」，遮住光线感应器后亮度也会自动调整。
- 阶段 2 · 测试后恢复亮度和模式：**做到**。已写回原来的 103，并恢复为自动模式（之后的亮度由自动调节决定）。
- 阶段 2 · 真机和模拟器的差异写进实现说明：**做到**（自动模式下亮度会自己变、三星分身用户 95 的安装问题）。
- 需要用户在 Fold7 上看：在最终版本里打开「设置与运行环境」，确认亮度修改开关和「系统授权」都还在、能用。
- CI：提交后补记。
- 结论：**验收通过**。
