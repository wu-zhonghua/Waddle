# Waddle for Android

Waddle 0.6.0 is an Android **AI terminal**. The international APK includes Bash, Git, ripgrep, curl, Codex 0.170.0 and their native dependencies. The domestic APK excludes overseas agents. Both keep built-in model settings and work as terminals without AI credentials. No Termux app or computer service is needed. Only stage 1 (Codex) is implemented; Claude Code and Antigravity are not bundled yet.

## Build

Use JDK 17, Android SDK platform 36, build-tools 36.0.0 and NDK 28.2.13676358. Set `JAVA_HOME` and `ANDROID_HOME`, or use an untracked `local.properties` for the SDK path.

```sh
cd apps/android
python3 tools/prepare-runtime.py --distribution international --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew assembleDebugAndroidTest
```

Runtime preparation is offline. All 39 original upstream packages, including both Codex archives, are pinned in [`third-party/runtime-packages`](third-party/runtime-packages/README.md). The script verifies hashes before extracting selected inputs. International and domestic payloads are isolated under `runtime/generated/{international,domestic}`; Gradle checks the distribution and ABI and rejects agent binaries in the domestic payload. Initial Gradle/SDK dependencies still need internet. See [runtime provenance](runtime/PROVENANCE.md).

The default distribution is `international`. To build the domestic APK:

```sh
python3 tools/prepare-runtime.py --distribution domestic --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug -PwaddleDistribution=domestic
```

Both distributions use the same application ID and output filename (`app/build/outputs/apk/debug/app-debug.apk`), so copy the APK aside before switching builds. They are alternatives, not separate installed apps. Add `-PwaddleDistribution=domestic` to device-test commands when testing that distribution.

The default APK is ARM64, Android 8+ (API 26), target SDK 35. For x86_64 CI prepare with `--abi x86_64` and build with `-PwaddleAbi=x86_64`. Each preparation emits one ABI. Generated runtime and build outputs stay ignored.

```sh
adb -s DEVICE_SERIAL install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start --user 0 -n dev.waddle.android/.MainActivity
```

Covering an existing 0.4.0/0.5.0 installation preserves `files/home`, imports, shell settings and `.codex` credentials. Domestic upgrades remove only Waddle-managed Codex executable links; they do not delete the saved login or user files. Uninstalling or clearing data deletes local files and keys. On multi-user phones always install using `--user 0` (or the explicitly intended user).

## Codex and accounts (international)

1. In any terminal directory, type `codex`. Choose **对话界面（推荐）** or **终端界面**, optionally **记住我的选择**. Change the default under **设置与运行环境 → 账号 → 管理 Codex 账号与界面**. `codex --waddle-chat` and `codex --waddle-terminal` override it for one launch. Ordinary arguments (`codex --version`, `codex exec ...`, etc.) pass directly to the CLI.
2. Chat uses that terminal's cwd and its own app-server process/thread/approval queue. Open separate terminals for simultaneous chats (up to eight). **回到终端** and the terminal's **对话进行中 · 返回对话** button keep messages intact. The drawer lists active chats only; **结束对话** stops that process without deleting files or auth. Activity recreation preserves service-owned chats; process death ends live panels. **历史对话** lists saved local sessions by last update, with title and original cwd; **加载更早对话** paginates. Selecting a session opens a separate panel in its original directory (or switches to its already-open panel), preserving the source panel and draft. Missing/inaccessible directories produce an error instead of falling back; **重试恢复** retries after fixing the directory. History is also available through the CLI.
3. Chat shows command/file events, approval details, local path links and image thumbnails. Approvals support **允许本次** and **拒绝**; stopping declines outstanding requests. Threads use Codex's `untrusted` approval policy and read-only sandbox request. Android's runtime does not provide a strong workspace security boundary; normal app storage permissions still apply.
4. **账号** lists Codex login and each built-in provider's saved-Key state. Manage Codex with ChatGPT device-code login (complete authorization yourself in the browser), API Key or logout. Waddle never shows a saved Key. Codex manages credentials in the existing private `HOME/.codex`; CLI and chat share it. Logging out stops Waddle chat panels; stop any independent CLI already running before changing accounts. No account credentials are copied into built-in model settings.
5. Domestic builds have no Codex program, login, preferences or AI-backend option. `codex`, `claude`, `agy` are unavailable. Built-in AI and terminal/files remain available.

## Terminal and files

- The drawer lists files and up to eight independent terminal sessions. Sessions live in a foreground service and survive Activity recreation and brief backgrounding; process death/reboot ends running shells.
- The prompt and title show the current directory. Long-press copies selected text; two fingers resize text. Ctrl affects the next key, `^C` interrupts, Paste previews clipboard contents, Keys opens the keyboard. Unsolicited OSC 52 clipboard writes are disabled.
- Files offers **手机存储**, **Waddle 文件**, and **其他来源**. Browsing does not change a shell's cwd. **打开终端** and a folder's **目录操作** create a new shell in that folder; **文件选项（⋮）→ 切换当前终端到此目录** explicitly changes the existing idle shell.
- Android 11+ phone storage uses user-granted all-files access to edit original files. Other apps' private data and Android/data remain restricted. Document-only/cloud sources require an explicit **复制到 Waddle…** operation, then open the local copy; changes do not sync back. Android 8–10 retain private files and document-provider access.
- Image pinch/pan, PDF pages, audio/video playback, Markdown/HTML/text preview, opening in another app, sharing, and saving to phone remain available. The document reader disables JavaScript and external resources. `waddle-open 'path/to/file.png'` opens a file from the current shell, including child Bash processes.
- Imports are bounded to 512 MiB, 5,000 entries and 32 levels with atomic publication and cancellation. Text previews are bounded to 64 KiB; large images/PDF pages are downsampled. Shares grant read access to a snapshot of the selected file only.

Bash, Git, `rg`, `curl`, Android system commands and `bash script.sh` work locally. Python, Node, SSH, a package manager and Git scripted porcelain are not bundled.

## AI model settings

Open **导航 → 设置与运行环境 → 账号 → AI 模型 · 配置 Key 和模型**. Select **DeepSeek**, **通义千问**, **Kimi**, **智谱**, or **自定义（OpenAI 兼容）**. Select a suggested model or type an exact model ID, enter a Key belonging to that service and endpoint, then **测试连接** and **保存**. Tests use the current form; a successful test does not automatically save changes. Model availability depends on the service and account.

Endpoints accept an HTTPS Base URL or the complete `/chat/completions` URL. Custom gateways must support non-streaming Chat Completions, system/user messages and text responses. Redirects, URLs containing user info/query credentials, and cleartext HTTP are rejected. Preset endpoints can also be edited for a service's regional/workspace address. Changing an endpoint requires entering the Key again so a saved Key is never silently sent to a different server.

Official preset references: [DeepSeek](https://api-docs.deepseek.com/quick_start/agent_integrations/), [Qwen](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope), [Kimi](https://platform.kimi.com/docs/get-api-key), [Zhipu](https://docs.bigmodel.cn/cn/guide/develop/openai/introduction). The Qwen preset uses the still-supported Beijing DashScope endpoint; users can enter their workspace-specific endpoint.

Keys are encrypted using a non-exportable Android Keystore AES-GCM key and private app preferences, separately per provider. Backup is disabled. Saved keys are never filled back into the UI, shell environment or model context. Leave the Key field blank to preserve it for the same endpoint, type a replacement, or choose **删除已保存的 Key**. There is no key-reveal button. HTTP errors are translated into messages for invalid/expired Keys, insufficient balance/quota, network/TLS problems, rate limiting and unavailable models without displaying raw server text. Waddle does not supply accounts or credits.

## Generate commands and explain failures (stage 1)

1. At a Bash prompt, tap **问 AI，写命令**. In the international build choose **内置模型** or **Codex**; both command generation and error diagnosis use this choice. **账号与模型设置** opens account setup. In the domestic build, unconfigured AI offers **去设置** directly.
2. Describe the task. **查看本次上下文** shows the recent transcript sent with the current cwd and last exit code (up to 12,000 characters). This data goes directly to the selected service. Check it before sending if the terminal contains sensitive output.
3. Tap **生成命令**, inspect the explanation and edit the command. **运行命令 ↵**, hardware Enter or the keyboard's Go action confirms execution. Shift+Enter inserts a newline. Long/multiline Bash is supported up to 64 KiB.
4. After a command returns nonzero, **退出码 … · 让 AI 看看** appears. Open it and choose **分析报错** to obtain a cause and fix from the same context. A repair command is an editable draft and requires the same confirmation; an explanation may have no command.

Commands run once in the original interactive Bash, preserving `cd` and exported variables. Execution replaces unsubmitted terminal input and requires the same session, prompt epoch, cwd and foreground shell. Switched/closed sessions, new commands, `read`, continuation prompts and running foreground programs invalidate the draft. Both Emacs and Vi Readline maps are supported; disabling Readline disables AI command submission. Close the sheet, stop generation, switch sessions or recreate the Activity to cancel a request; late results are discarded. HTTP requests are bounded by connection/read timeouts and a two-minute overall deadline. Requests have no tools or automatic execution path; there is no stage-2 agent loop.

Exit-code reporting describes the last Bash command/pipeline status, following normal Bash semantics (without `pipefail`, a pipeline reports its last element). Custom prompt hooks loaded from `.waddlerc` remain supported. A user who later replaces Waddle's shell integration can disable tracking.

## Verification

Use an isolated emulator for the full suite:

```sh
./gradlew :app:connectedDebugAndroidTest
```

For an existing data-bearing test installation, build and install the instrumentation APK directly rather than using Gradle's installation lifecycle:

```sh
adb -s DEVICE_SERIAL install --user 0 -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument --user 0 -w dev.waddle.android.test/dev.waddle.android.WaddleTestRunner
adb -s DEVICE_SERIAL shell pm uninstall --user 0 dev.waddle.android.test
adb -s DEVICE_SERIAL shell am start --user 0 -n dev.waddle.android/.MainActivity
```

Instrumentation force-stops the app and ends live shells. Do not run it while someone is manually using that device. Default tests use deterministic AI HTTP peers and temporary encrypted test profiles; they never need or log a real Key. They cover request construction, errors, credentials, cancellation, editable command/diagnosis UI, exactly-once execution and shell/file regressions. `-e waddleLiveSuggestions true` opts into the real model context/generation test using a model already configured through settings. `-e waddleNetwork true` enables curl/Git public HTTPS checks. The shared-storage roundtrip requires a manually granted permission and `-e waddleStorage granted`.

The test runner handles the actual notification prompt on emulators only. Pass `-e waddleNotifications deny` (or Gradle `-Pandroid.testInstrumentationRunnerArguments.waddleNotifications=deny`) after a fresh emulator install to exercise denial. It never changes a physical phone's authorization. CI covers both permission choices for both distributions.

`-e waddleLiveCodex true` opts into real saved-account Codex read/write, command draft/confirmation, CLI reuse, diagnosis and history-picker continuation tests. Temporary task folders are removed afterward; the real account is not logged out. A separate offline account test uses an isolated temporary CODEX_HOME and an intentionally fake Key to verify local save/read/logout without touching user credentials.

Actual results and remaining acceptance steps are recorded in [stage 1 implementation](../../docs/dev/2026-11-22-04-bundled-agents-and-accounts.md) and [session history follow-up](../../docs/dev/2026-11-22-05-codex-session-history.md). Mocked protocol tests do not establish real-provider or domestic-network connectivity.

## Source and license

Waddle Android is GPL-3.0-only; the desktop keeps its original license. Preserve [terminal provenance](third-party/termux/PROVENANCE.md), [runtime provenance](runtime/PROVENANCE.md), upstream source records, locks and notices. A public binary release requires the corresponding sources mandated by bundled licenses. This work does not publish a release.

## Android design and responsive layout

All app surfaces share `WaddleTheme` and the [Android design system](../../docs/dev/android-design-system.md). Files starts as a list with a grid toggle; accounts, provider configuration and help are full-page surfaces. Icon actions retain Chinese accessibility labels. Home paths display as `~`, with full paths available from the file menu. At 840dp width the same workspace gets permanent navigation; terminal processes, chat messages and saved drafts stay alive across layout changes.

Visual acceptance captures are opt-in on a disposable emulator: build/install both debug APKs, then run `adb -s EMULATOR shell am instrument -w -e class dev.waddle.android.UiScreenshotTest -e waddleScreenshots after -e waddleSize phone dev.waddle.android.test/dev.waddle.android.WaddleTestRunner`. PNGs are written to the app's external `files/screenshots` directory. The harness seeds a demo folder and illustrative chat messages, resets demo sessions and needs no login. Do not run this visual harness on a data-bearing installation. `ProductUiIntegrationTest` covers list/grid state, provider access, and live fold-size changes; it restores the emulator's original size and density.
