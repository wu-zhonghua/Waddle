# GitHub 上的安卓设备测试四种组合全部通过

- 状态：验收不通过
- 日期：2026-11-23
- 上一份：[2026-11-22-04-bundled-agents-and-accounts](2026-11-22-04-bundled-agents-and-accounts.md)（阶段 1 验收不通过）
- 涉及：安卓

## 现在的样子

04 阶段 1 的功能在模拟器上验收都做到了。本地 arm64 模拟器上，国际版、国内版各自选「允许」和「拒绝」通知，四轮设备测试都是 0 失败。

推到 GitHub 后（提交 `5859731d`），Android 工作流的两种构建都通过了，Desktop checks 也通过了。但设备测试没过：

- 国内版、通知「允许」这一组有 2 个导航用例失败：
  - 「文件优先导航保留终端和关闭确认」：抽屉没有关上。
  - 「文件夹卡片和当前文件夹打开新终端」：从文件页打开的新终端，目录停在上一级，没进到名字带中文、空格和引号的那个子文件夹。
- 这一组失败后，其余三组（国内版拒绝、国际版允许、国际版拒绝）都被自动取消，**国际版在 GitHub 上一次设备测试都没跑完**。

## 要做什么

让 GitHub 上 Android 工作流的设备测试稳定通过，四种组合（国际版 / 国内版 × 允许 / 拒绝通知）每一组都要真正跑完。

如果失败的原因是产品本身的问题，比如从文件页打开终端时进错了目录，就要修好，让用户在手机上看到的结果是对的；不能只改测试让它通过。

## 不做

- 04 阶段 2、阶段 3（Claude Code、Antigravity）。
- 改动 04 阶段 1 已经验收的功能和界面（修上面的问题需要的除外）。

## 怎样算做好

- GitHub 上 Android 工作流的两种构建和四组设备测试全部通过，每一组都跑完，没有被取消。Desktop checks 照常通过。
- 连续两次推送（或重跑一次）结果都一样，不是碰巧通过。
- 安卓模拟器上：在文件页进入名字带中文、空格和引号的文件夹，用「在当前文件夹打开终端」或文件夹卡片打开新终端，`pwd` 就是这个文件夹；原来的终端不受影响。
- 本地 arm64 模拟器上，四种组合的设备测试仍然 0 失败。

---

## 实现说明（实现者填写）

- 文件页「打开终端」与「切换当前终端到此目录」在点击时读取控制器的当前目录，并拒绝加载中/错误目录；不再使用上一帧捕获的父目录。文件夹条目操作仍使用明确选中的条目，新建终端不会打断原会话。
- 终端 cwd 改为直接读取 `/proc/<pid>/cwd`，避免 `File.getCanonicalPath()` 对可变链接的缓存返回旧目录；第三方组件变更已记录在 provenance。
- 导航设备测试等待无障碍事件静止，让 Compose 的点击回调、抽屉动画与 BackHandler 完成提交后再操作。保留真实 shell cwd、文件落点、会话数量、原会话运行状态、关闭确认等全部断言。
- 额外修复文件预览设备测试的跨文件竞态：先等新文件名出现再切换原文/排版，并等待旧预览真正消失，避免点击上一页同名按钮。国际拒绝组首次暴露该问题，修复后完整重跑通过。
- 最终 UI 回归另暴露一次视频切换到音频后无播放控件的超时。纯音频原先复用依赖 SurfaceHolder 的 VideoView（参照 [Android 源码](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/master/core/java/android/widget/VideoView.java)）；现改用独立 MediaPlayer，保留播放/暂停/进度拖动，并处理音频焦点、后台暂停与页面关闭释放。设备测试连续两轮切换视频和音频，并等待前一个查看器实际消失，不删断言或提高超时来掩盖失败。
- Android 构建与设备测试矩阵均设置 `fail-fast: false`。某组失败不再取消其他组，设备测试上传 HTML 和 XML 结果以便逐组检查。
- 试用：进入名称含中文、空格和单引号的子目录，分别从目录操作和底部「打开终端」新建 shell；运行 `pwd`、写入文件，再从抽屉回到原终端检查原命令与输出。左边缘滑出抽屉后按返回，应只收起抽屉。
- 本地仅使用 **Youzai_API_35 / emulator-5556 / Android 15 API 35 / arm64-v8a**。与 CI 使用相同 Gradle 任务、distribution 和 notification 参数；CI 的 Linux/KVM x86_64 与本机 Apple Silicon 架构不同，不声称本机运行过 x86_64 模拟器。
- 原提交的国内允许组在本地通过，说明 CI 的时序差异没有在本机基线稳定重现。修复后完整四组合及最终界面的回归结果见下方记录。
- 第一轮本地结果（修界面前，API 35 arm64）：国内允许 **57 测试 / 0 失败 / 11 跳过**，国内拒绝 **57 / 0 / 11**；国际允许 **57 / 0 / 7**，国际拒绝重跑 **57 / 0 / 7**。跳过项为版本不适用、真实账号/网络/共享存储 opt-in，以及默认不执行的截图采集。运行时固定输入校验 **3/3** 通过。Gradle 日志会重复统计 skipped 回调，以上以 JUnit XML 为准。
- Desktop 本地既有检查：**200/200 测试**、TypeScript 检查、8 GiB Node heap 的 `build:dev` 均通过，未修改桌面源文件。平台与执行细节见 [23-02 验证说明](2026-11-23-02-product-ui-redesign.md#验证与限制)。
- 未推送、未重跑 GitHub 工作流，因本次明确禁止 push。**GitHub 两次全绿及 Desktop checks 仍须验收者提交后验证**，本地结果不替代该验收项。



### 最终设备回归（23-02 全部界面与播放器修复后）

| 发行版 | 通知 | 测试数 | 失败 / 错误 | 跳过 |
|---|---|---:|---:|---:|
| 国际版 | 允许 | 61 | 0 / 0 | 7 |
| 国际版 | 拒绝 | 61 | 0 / 0 | 7 |
| 国内版 | 允许 | 61 | 0 / 0 | 11 |
| 国内版 | 拒绝 | 61 | 0 / 0 | 11 |

两种发行版的 ARM64 / x86_64 APK、测试 APK、`testDebugUnitTest`、`lintDebug` 全部构建通过；终端组件 **145/145 宿主单测**通过，app lint **0 error / 36 warning / 3 hint**。x86_64 验证编译与打包，设备运行使用 ARM64。

四组均完整运行 `connectedDebugAndroidTest`。数字取自 JUnit XML；`NotificationPermissionIntegrationTest` 检查真实系统通知权限与矩阵参数一致。新增的三项产品 UI 测试在四组中都通过。跳过仅为已有 opt-in 的真实服务、网络/手机存储测试、发行版不适用项，以及默认跳过的截图采集；没有删除或跳过原失败用例。

### 本地复现命令

在 `apps/android` 目录下，用 JDK 17、项目锁定的 SDK/NDK；`ANDROID_SERIAL` 明确指向 Youzai_API_35，避免连接其他设备：

```sh
export ANDROID_SERIAL=emulator-5556
python3 -m unittest discover -s tools -p 'test_*.py'
for distribution in international domestic; do
  python3 tools/prepare-runtime.py --abi arm64-v8a --distribution "$distribution" --ndk "$ANDROID_HOME/ndk/28.2.13676358"
  ./gradlew assembleDebug testDebugUnitTest lintDebug -PwaddleDistribution="$distribution" --console=plain
  for notification in allow deny; do
    ./gradlew connectedDebugAndroidTest -PwaddleDistribution="$distribution" -Pandroid.testInstrumentationRunnerArguments.waddleNotifications="$notification" --console=plain
  done
done
```

CI 的 x86_64 路径仍用相同脚本加 `--abi x86_64`，Gradle 加 `-PwaddleAbi=x86_64`。本地另外验证该 ABI 的 APK/测试 APK 编译；设备执行限定 ARM64 模拟器。最终界面回归还检查新建/切换终端后抽屉必须关闭，顶部与抽屉的「新建终端」统一关闭导航，避免动画期间两个入口产生不同结果。

## 验收记录（验收者填写）

验收日期 2026-11-24，验收者 Claude。对照的是 Mac mini 上未提交的工作区（基于 `91b8800f`）。所有检查都由验收者重新跑过，没有直接采用实现说明里的数字。

**本地环境**：模拟器 `Waddle_API_35`（emulator-5554，API 35，arm64）。`Youzai_API_35` 当时没有开着，另一个项目的 Codex 任务也在用这台机器，所以没有用它，避免互相干扰（04 验收时就因为共用出过问题）。验收期间 Mac mini 负载很高（load average 约 70，8 核），主要来自几个已经连续跑了 5 天、各占约 100% CPU 的 Chrome / Cursor 辅助进程。

1. **CI 配置（做到）**：Android 工作流的构建矩阵和设备测试矩阵都加了 `fail-fast: false`。一组失败不会再取消其他组。设备测试会上传 HTML 和 JUnit XML 结果。

2. **文件页在中文、空格、引号文件夹里打开终端（做到）**：
   - 在 `~` 下建了文件夹 `验收 '引号' 目录`，在文件页「Waddle 文件」里进入它，点底部「打开终端」。新终端名为「验收 '引号' 目录 · 2」，`pwd` 输出 `/data/data/dev.waddle.android/files/home/验收 '引号' 目录`，提示符显示 `~/验收 '引号' 目录`。
   - 从抽屉切回原来的「终端 · 1」，之前的 `echo first-shell` 和输出都还在，目录仍是 `~`。
   - 截图：[新终端 pwd](screenshots/2026-11-23-01-accept-folder-terminal-pwd.png)。
   - 设备测试 `folderCardAndCurrentFolderOpenNewShellsHereWithoutInterruptingOriginal` 和 `fileFirstNavigationPreservesShellsAndCloseConfirmation`（原来 CI 上失败的两个用例）在四组里都通过。

3. **本地四种组合设备测试（做到，第一轮有 1 个失败，详见下面）**。数字取自 JUnit XML：

   | 发行版 | 通知 | 测试 | 失败 | 跳过 |
   |---|---|---:|---:|---:|
   | 国际版 | 允许 | 61 | **1**（第一轮）→ 0（重跑两次） | 7 |
   | 国际版 | 拒绝 | 61 | 0 | 7 |
   | 国内版 | 允许 | 61 | 0 | 11 |
   | 国内版 | 拒绝 | 61 | 0 | 11 |

   - 第一轮「国际版 · 允许」有 1 个失败：`NotificationPermissionIntegrationTest.requestedNotificationChoiceMatchesSystemPermission`。它期望通知权限是「允许」，15 秒内读到的仍是「未允许」。这一轮正好在机器负载最高时跑，用了 22 分钟。
   - 负载降下来后，同一组合完整重跑两次，都是 61 / 0 失败 / 7 跳过，每次约 2.5 分钟。
   - 判断：这是测试在系统通知弹窗上的时序问题，机器很慢时会出现。它不影响产品功能，其他 60 个用例和另外三组都通过。但它说明这个新用例在慢机器上可能不稳定，记下来，留意 GitHub 上的表现（见 CI）。

4. **构建、单元测试、lint（做到）**：
   - 两种发行版的 `assembleDebug testDebugUnitTest lintDebug` 都通过。
   - 运行时固定输入校验 `tools/test_*.py` 通过。
   - 终端组件宿主单测强制重跑，**145/145** 通过。
   - app lint **0 error / 36 warning / 3 hint**，和实现说明一致。
   - x86_64 构建只看 CI，本地没有跑。

5. **Desktop（做到）**：
   - 桌面源码没有改动。
   - 第一次跑 `vitest` 时，有 5 个用例在 5 秒超时（`electron-builder-config.test.ts`、`mockwaveenv.test.ts`），当时机器负载约 70。
   - 负载降下来后完整重跑 **37 个文件 / 200 / 200** 通过。
   - `tsc --noEmit` 通过。

6. **终端组件第三方代码改动（做到）**：
   - 改动：`TerminalSession.getCwd()` 原来用 `File.getCanonicalPath()` 解析 `/proc/<pid>/cwd`，改为每次用 `Os.readlink` 直接读内核链接，只改了一个方法。
   - 理由成立：canonical path 有缓存，`cd` 之后可能返回旧目录，正是「打开终端停在上一级」的原因之一。
   - `PROVENANCE.md` 的「Local integration changes」里加了一条，说明改了什么、为什么。上游出处、固定提交、GPL-3.0 / Apache-2.0 许可说明都保留。
   - 小问题，不影响验收：`import java.io.File` 已经没有用到了，下次碰这个文件时可以顺手删掉。

- CI（`d86eb49a`）：[Desktop checks #38](https://github.com/wu-zhonghua/Waddle/actions/runs/45418027281) **通过**。[Android #19](https://github.com/wu-zhonghua/Waddle/actions/runs/45418027132) **失败**：
  - 通过的：两种构建；国内版允许、拒绝两组设备测试。四组都跑完了，没有被取消。
  - **国际版允许、拒绝两组失败**：两组都有 `ProductUiIntegrationTest.accountsAreFullPageAndEachProviderHasConfiguration` 和 `NavigationIntegrationTest.accountsAndHelpAreReachableWithoutCreationEntries` 失败。原因是在账号页找不到「AI 模型 · 配置 Key 和模型」：GitHub 模拟器屏幕较小，国际版多了「编程工具 · Codex」一组，这一项被挤出屏幕，测试又不会往下滑。
  - 验收者把本地模拟器调成 360×640dp 复现：这一项在屏幕外，往上滑一下就能看到、能点。用户能用，问题在测试。
  - 国际版允许组还有 `FileViewingIntegrationTest.commandChoiceChatReturnRotationAndImageViewerPreserveConversation` 超时，超时时图片其实已经打开了。
  - 本机和浏览器都没有登录 GitHub，没法重跑。账号页的失败每次都会出现，重跑也不会变绿。
- 结论：**验收不通过**。本地各项都做到了：中文 / 空格 / 引号文件夹打开终端进对目录，四组设备测试、构建、单测、桌面都通过。但 GitHub 上国际版两组设备测试失败，「四组全部通过、连续两次一样」没做到。代码已在 main（`d86eb49a`），没做到的部分另提 [2026-11-24-01](2026-11-24-01-android-ci-green-international.md)。
