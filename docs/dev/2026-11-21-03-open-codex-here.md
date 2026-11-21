# 找到文件夹，在这个文件夹打开独立的 Codex 对话

- 状态：验收不通过
- 日期：2026-11-21
- 上一份：2026-11-21-02；补充 2026-11-20-01 阶段 1
- 涉及：安卓

## 现在的样子

文件夹已经可以直接打开新终端。「交给 Codex」目前只将路径附给现有对话，不改变工作目录，也不保留多个独立的 Codex 页面。

## 要做什么

- 本地文件夹卡片与当前目录提供「在此打开 Codex」。点击新建独立对话，其读写文件、执行命令从所选目录开始，原来的终端和 Codex 对话保留。
- 标题与左侧抽屉显示各 Codex 对话的目录和状态。来回切换不停止任务，草稿、消息和待审批请求不串到其他对话。关闭前确认，只关闭所选对话。
- 抽屉提供新建 Codex 入口，可以用当前终端目录、默认工作区或输入其他本地路径。继续已保存的对话时使用其原目录；目录不存在或无权访问时提示原因，不退回其他目录。
- 共享文件夹仍先明确导入副本，再「在副本中打开 Codex」，不假装直接操作手机原目录。单个文件仍可作为待发送路径交给当前对话。
- 共用已有 Codex 登录，不退出、不复制凭据、不另装应用。保留原系统设置工具和审批规则。

## 不做

- 桌面端、主题、共享目录自动写回、对话中途换目录、跨对话共享上下文。
- 本轮自动更新真机；先在独立模拟器验证，写说明后等待验收。

## 怎样算做好

- 模拟器在含中文、空格、单引号的文件夹打开 Codex，展示并传递正确的 cwd；协议执行可在此读写文件、运行命令。
- 打开两个不同目录的对话，切换及 Activity 重建后各自草稿、上下文和审批独立，关闭其中一个不终止另一个。
- 恢复对话保留原目录；失效目录和会话数量上限都有明确错误，不误发到其他目录。
- 共享目录仅在确认导入副本后提供本地 Codex 入口；旧终端、文件浏览和 Codex 功能回归通过。
- 构建、lint 和针对性设备测试通过；说明真实模型是否测试、真机是否更新，不将协议验证当成真实模型验证。

---

## 实现说明（实现者填写）

### 做了什么

- 本地目录卡片并排提供「打开终端 / 打开 Codex」，当前目录底部提供完整的「在此打开终端 / 在此打开 Codex」。Codex 入口新建独立对话并启动 Waddle 自带运行时；不会清空旧对话、向旧终端发送 cd 或复制登录凭据。
- `TerminalService` 持有 `CodexWorkspace`，最多同时打开 4 个 Codex 面板。每个面板独立拥有进程、控制器、thread、工作目录、消息、审批和待发送文件。Activity 重建由服务保留对象，草稿按面板 ID 保存。
- 所选目录实际传入 app-server 进程、`thread/start` 和 `turn/start`，不是仅在提示词中附带路径。路径使用 File/JSON 传递，含中文、空格、单引号无需拼接 shell cd。
- 面板清单、所选项、cwd 和各自最后 thread ID 存储在应用配置里；初始面板沿用已有 `codex_embedded_ui`，兼容之前保存的对话。重新连接会读取原 thread 元数据、验证原 cwd，再恢复该 thread。无效目录明确报错；不会静默改成 home 或在旧消息下另起新 thread。
- 抽屉支持切换、新建、确认关闭。新建可选默认工作区、当前终端目录或输入绝对路径；标题显示文件夹名与状态，抽屉显示目录，私有 home 缩写为 `~`，设置中可看完整路径。
- 审批增加「稍后处理 / 查看审批」；暂存不回复允许或拒绝，切换到别的会话后请求继续保留。关闭面板只停止其进程和请求。读取设置与亮度修改开关作用于当前 Codex 面板；原有 schema、权限和逐次审批规则保持。
- 手机授权目录仍需明确导入；完成后增加「在副本中打开 Codex」。原有单文件/路径「交给 Codex」仍只作为待发送附件，不改变对话目录。

### 怎么试用

1. 文件 → 找到本地文件夹 → 卡片「打开 Codex」，或进入文件夹后点底部「在此打开 Codex」。已有登录会复用；没有登录的环境才显示登录界面。
2. 发送「运行 pwd，读取现有文件，再创建 result.txt 并读回」，检查结果位于所选目录。
3. 再选择另一个文件夹打开 Codex；在抽屉切换两个对话，确认目录和内容各自独立。审批出现时可先点「稍后处理」，切换回来用「查看审批」处理。
4. 抽屉「＋ 新建 Codex」可直接选择当前终端目录。关闭某个对话前会确认，其他会话保留。
5. 共享文件夹先确认「导入副本」，再点「在副本中打开 Codex」；修改不会写回手机原目录。

### 验证与限制

- 独立 Android API 35 ARM64 模拟器；构建 `assembleDebug assembleDebugAndroidTest lintDebug testDebugUnitTest` 通过。现有终端单元测试通过/命中缓存；app lint 0 errors、20 warnings、4 hints，提示为现有依赖/SDK 与代码风格建议（新增三处 SharedPreferences KTX 建议），无阻塞问题。
- 设备回归 29 项中实际执行 27 项全部通过，2 项真实模型 opt-in 测试跳过，耗时 48.192 秒。覆盖 CodexFolderIntegrationTest、NavigationIntegrationTest、FileBrowserIntegrationTest、TerminalIntegrationTest、CodexIntegrationTest。
- 真实打包的 Codex 0.170.0 app-server：在含中文、空格、单引号的目录启动，thread 返回 cwd 一致，`command/exec` 不额外覆盖 cwd 也能在该目录执行 pwd、读取 existing.txt、相对路径写入 result.txt。此项无需登录，验证了实际运行时，未冒充模型请求。
- 控制器协议测试使用测试专用进程响应器，验证两个对话各自 thread/turn cwd、任务与审批隔离、关闭其中一个不影响另一个、恢复原目录、目录删除不回退。导航测试真实启动打包服务，仅为离线草稿显示设置测试内登录标志，不写账号状态、不发送模型请求；覆盖草稿/消息/审批、Activity 重建、关闭取消/确认及导入副本入口。
- 实现阶段使用的模拟器没有模型登录，当时未做真实模型端到端任务；提供 `-e waddleLiveFolder true` 的显式测试，后续可在有现有登录的隔离测试环境执行。实现阶段 Fold7 未连接，未安装、重启或操作真机；用户随后要求的真机验证见下文。
- 进程被系统终止后不承诺继续执行任务；重新连接可恢复已保存对话。未发送草稿仅保证切页和 Activity 重建，关闭面板后不保留草稿。关闭面板保留 Codex rollout 文件，但不提供全历史列表，可用 CLI 历史恢复能力。Android 的工作区权限隔离仍有限，cwd 不是权限边界。
- 原需求 2026-11-20-01 的主题和完整真机验收仍未完成，不将其整体标为已实现。本轮未改桌面、未提交、未推送。

### 最终布局与产物

- 窄屏走查发现目录卡片第二个操作需要额外滚动，已将两按钮并排；最终 APK 的 7 项导航回归再次全部通过，35.333 秒。实际触屏确认两个按钮均可见、点击后进入所选 `src` 目录的 Codex 页面、抽屉显示 `~/TerminalHereDemo/src`。模拟器临时测试包已移除，主应用正常打开。
- [目录入口截图](../../artifacts/open-codex-here/folder-actions.png)、[Codex 目录截图](../../artifacts/open-codex-here/codex-folder.png)、[抽屉截图](../../artifacts/open-codex-here/codex-drawer.png)。截图中的登录页属于无账号的隔离模拟器，不是用户真机退出登录。
- [测试 APK](../../artifacts/open-codex-here/waddle-android-0.4.0-open-codex-here-debug.apk)，SHA-256：`178557b57449dceeb9f789fa50e9a2955f5df44f9b881e78846987e6def2ba18`。
- 日志在忽略目录 `artifacts/open-codex-here/`：`build-final.log`、`device-final.log`、`navigation-final.log`。新增需求和索引已标记「已实现」，等待验收；未提交。

### 真机覆盖安装与真实模型验证（2026-11-21，用户随后明确要求）

- 已在 Fold7（SM-F966B）主用户 0 使用 `adb install --user 0 -r` 覆盖安装上述 SHA-256 对应的 APK。安装前后对比 home 下 2,946 个普通文件的哈希：0 丢失、0 原文件内容改变，`.codex/auth.json` 存在且完全一致；仅在内存中比对哈希，没有复制或输出凭据。
- 安装前后及模型测试后，主用户仅有 `dev.waddle.android`，用户 95 分身空间没有 Waddle。本轮没有安装 instrumentation 测试包、清空数据、卸载主应用、退出登录或更改手机系统设置；更新后正常启动应用。
- 在独立的 `~/FolderCheck-20261121-f57061/项目 ' demo` 中创建 `input.txt`（随机测试标记及数值 17、25）。从真机文件页点该卡片「打开 Codex」，生成 `Codex 2`，已有登录自动可用。
- 通过 Waddle 的真实任务输入框发送任务，只给相对文件名、不提供目标目录绝对路径。模型实际执行 `pwd`、读取 `input.txt`、计算和为 42、生成 `pwd.txt` 与 `result.json` 并读回。独立读取手机文件核对标记、数值、原输入未改、两个 cwd 均指向所选目录，所有检查通过；UI 显示「本轮完成」，无待审批或运行中任务。
- Fold7 上 `/data/data/dev.waddle.android/...` 与 `/data/user/0/dev.waddle.android/...` 为同一目录的两种路径。校验以设备号/inode 确认目录身份一致，未把 Android 路径别名误判为工作目录错误。
- 新建的验收文件夹与对话保留在手机，方便用户查看；原有项目与登录保留。此验证补齐此前未执行的真实模型目录任务，正式用户验收仍待完成，状态保持「已实现」，未提交代码。
- 安装核对：`artifacts/open-codex-here/phone-install-check.json`；模型文件核对：`phone-folder-result.json`；[真机结果截图](../../artifacts/open-codex-here/phone-codex-result.png)。这些产物均位于忽略目录。

## 验收记录（验收者填写）

- 验收环境（2026-11-21，Claude 验收）：Mac mini 上的 Android API 35 ARM64 模拟器 `Waddle_API_35`（emulator-5554，Waddle 专用那台，当时已在运行；`Youzai_API_35` 没有启动）。验收用的 APK 与 Fold7 上现装的版本相同（SHA-256 `aef51d2a…6c34`）。构建 `assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug` 通过：单元测试 145 项 0 失败，lint 0 errors / 21 warnings / 4 hints。全部设备测试 46 项：35 项通过，10 项需要登录或测试开关的跳过，1 项失败（`NavigationIntegrationTest.folderCodexPanelsKeepDirectoryDraftAndMessagesAcrossSwitchAndRecreation`：整套一起跑时 Codex 页停在登录页，找不到草稿输入框）。这一项单独重跑 3 次都通过，看作不稳定的测试。`PhoneStorageIntegrationTest` 在未授权、已授权两种状态下各 3 项通过。桌面端 200 项测试和 `tsc --noEmit` 通过。模拟器上没有 Codex 登录，这次没有跑真实模型；真实模型的结果引用实现者在 Fold7 上的记录。这次没有操作真机。跑完后撤销了模拟器的所有文件访问权限，删掉了测试包和演示目录。
- 在含中文、空格、单引号的文件夹打开 Codex，显示并传递正确的 cwd，能在那里读写文件、跑命令：**做到**。模拟器上从文件页打开的 Codex 标题和抽屉都显示所选目录（[截图](screenshots/2026-11-21-acceptance-08-codex-in-phone-folder.png)、[抽屉](screenshots/2026-11-21-acceptance-09-drawer-sessions.png)）；协议测试通过。真实模型依据实现者在 Fold7 上的记录：在 `项目 ' demo` 里执行 pwd、读文件、写结果，独立核对过。
- 两个不同目录的对话，切换和重建后草稿、上下文、审批各自独立，关一个不影响另一个：**做到**。`CodexFolderIntegrationTest` 通过；导航测试里的同一场景整套跑时失败 1 次，单独重跑 3 次都通过（见上）。
- 恢复对话时还在原目录；目录失效、会话数到上限都有明确错误：**做到**，有测试覆盖。
- 共享目录只有确认导入副本后才有 Codex 入口；旧的终端、文件浏览、Codex 功能回归通过：**做到**。
- 构建、lint、设备测试通过；写清楚是否测过真实模型、是否更新过真机：**做到**。实现说明区分了协议验证和真实模型验证。
- 小问题（不影响结论）：Codex 对话的编号会一直往上加，模拟器上已经到了「Codex 40」，测试建过的对话也算在里面。
- CI：提交 `2a2aa8ba` 的 [Android 检查失败](https://github.com/wu-zhonghua/Waddle/actions/runs/44891364506)。build 和 device-tests 两个 job 都停在「准备内置运行时」这一步：锁定的 Termux 软件包（比如 `codex_0.170.0_aarch64.deb`）在软件源上已经 404。我在 Mac mini 上用干净的仓库副本跑同一步，也是 404；本机能构建，只是因为本地缓存里还留着这些包。后面的构建、单元测试、lint 和设备测试在 GitHub 上都没有跑到。[Desktop checks 通过](https://github.com/wu-zhonghua/Waddle/actions/runs/44891364520)。**CI 没过，按规则算没做到。**
- 结论：**验收不通过**。功能上「怎样算做好」的各条都做到了，但 GitHub 上的 Android 检查没过（干净的仓库构建不出内置运行时），另提 [2026-11-21-07-android-build-from-clean-checkout](2026-11-21-07-android-build-from-clean-checkout.md)。
