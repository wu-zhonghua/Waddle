# Waddle for Android

Waddle is a standalone Android terminal and Codex workspace. Version 0.4.0 bundles Bash, Codex 0.170.0, its code-mode helper, Git, ripgrep, curl, native libraries and CA certificates. **No Termux app, external-command permission or computer service is required.** The terminal and Codex page share Waddle's private home, tools and Codex login.

## Build

Install JDK 17 and Android SDK platform 36, build-tools 36.0.0 and NDK 28.2.13676358. Set `JAVA_HOME` and `ANDROID_HOME`, or use an untracked `local.properties` for the SDK path. From this directory:

```sh
python3 tools/prepare-runtime.py --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew :app:connectedDebugAndroidTest
```

Runtime preparation downloads only packages pinned by SHA-256 in `runtime/packages.lock.json`. It does not read a device or execute package scripts. Downloads and generated payloads are ignored by Git. Use `--offline` to rebuild from a populated cache. Gradle fails with an explanation if the runtime is missing or has the wrong ABI. See [runtime provenance](runtime/PROVENANCE.md) for binary transformations, sources and licensing.

The default debug APK targets **ARM64**, Android API 26+, target SDK 35:

```sh
adb -s DEVICE_SERIAL install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n dev.waddle.android/.MainActivity
```

For the x86_64 CI emulator, prepare with `--abi x86_64`, then pass `-PwaddleAbi=x86_64` to Gradle. Each preparation emits one ABI. These APKs are development builds, not a signed production release. On phones with app-clone/work profiles, explicitly target the intended Android user (the examples use primary user 0); an unrestricted ADB install can register the app in multiple profiles. The instrumentation APK is a temporary test harness without a launcher icon, not another Waddle client.

## Use

Open the terminal and run `bash`, `git`, `rg`, `curl`, or `codex` directly. Basic filesystem commands come from Android's system tools. Text scripts can run through `bash script.sh` or `sh script.sh`.

Alternatively open **☰ → Codex → 启动 Codex → ChatGPT device-code login**, then send a task. Existing login is reused without signing out. Both entry points use the same `HOME` and `CODEX_HOME`; a file created by Codex is immediately accessible in the terminal. A previous Termux login is not imported. Credentials created by the embedded Codex stay in Waddle's private directory, with Android backup disabled. Uninstalling/clearing Waddle deletes its files and login.

The Codex page streams replies and tool activity, offers stop, approval dialogs and last-thread resume. Each panel restores its own saved thread when reconnected, reading and validating the original cwd first; a missing/inaccessible directory is an error, never a fallback. Pending approvals can be deferred with **稍后处理** so another panel can be used; **查看审批** returns to the request without approving it. A user request can invoke a native read-only tool for brightness, brightness mode, screen timeout, airplane mode and accessibility enabled state. The read toggle in **☰ → 设置与运行环境** applies to the selected Codex panel and disables this reader only; display-write opt-in is likewise per panel. It cannot accept arbitrary setting keys/URIs or inspect other app screens. The standalone Codex CLI shares files/login but does not register the panel's client-side dynamic tools.

Stopping also works while a task is starting. For an active turn, Waddle stops the panel's local process tree and reopens the saved conversation before allowing another task. This also disables display writes. Commands already completed are not rolled back. The terminal's separate sessions remain independent. Brief background/foreground transitions and actual screen rotation preserve the selected Codex page and conversation while the service remains alive.

### Navigation

The title bar has a menu and a left-side file shortcut. The drawer places **文件** first, followed by Shell sessions and independent Codex conversations with directory/status; settings/help stay at the bottom. Select a session to close the drawer, use **＋ 新建终端** to add a shell, **＋ 新建 Codex** to choose a default/current-terminal/typed local cwd, or × to close one after confirmation. Up to four Codex panels can be open; each owns its process, thread, approvals and cwd. Closing one stops only that panel and retains saved rollout files and shared credentials. Back/scrim dismiss the drawer without navigating the file browser behind it. Files do not change shell cwd until an explicit handoff.

Page changes and Activity recreation retain the Codex draft/pending file and live shell state; process death still does not restore shell processes. The first launch opens the terminal. Panel IDs, cwd and last-thread references survive process death; running commands do not. Reconnect a saved panel to recover its saved thread. Themes and permanent wide-screen panels remain separate requirements.

Folder-scoped Codex validation (2026-11-21): 27 device cases passed on the isolated API 35 ARM64 emulator; two real-model cases were skipped. After the final compact-card layout adjustment, all seven navigation cases passed again. Assembly/unit checks/lint passed; the actual bundled server performed relative file reads/writes and commands in a path containing Chinese, spaces and a quote. A subsequent user-requested Fold7 update preserved all 2,946 existing home files and login-cache bytes without creating a clone. A real model task launched via the folder card then verified pwd, input reading, 17+25=42, and relative result-file writing/readback in the selected folder; the result was independently checked on-device. Formal user acceptance remains pending. See [implementation and artifacts](../../docs/dev/2026-11-21-03-open-codex-here.md).

### Visual file browser

Open **文件** to choose a location: **手机存储**, **Waddle 文件**, or a saved/system-picked **其他来源**. Browsing shows a single breadcrumb and source label, a file grid, and one bottom **打开终端 / 打开 Codex** pair for the current directory. Each folder has **在此打开…** for opening that folder without navigating first. Refresh, full path, hidden files, manual path entry and existing-session handoff are in **更多**. **位置** returns to the source list; Back goes up, then returns to locations before leaving Files.

On Android 11+, **手机存储** explains and opens the system all-files-access screen. The user grants it manually; Waddle never grants it itself. With access, mounted primary storage and available SD/USB volumes are real filesystem locations, so terminals and Codex work directly on original files. Permission denial/revocation and unavailable directories produce errors rather than an empty-looking browser or an automatic copy. Other apps' private data and Android/data remain restricted. Android 8–10 continue to use private Waddle files and SAF sources; this change does not request legacy broad storage permissions. [Android all-files-access documentation](https://developer.android.com/training/data-storage/manage-all-files).

**其他来源 → 添加来源 / 选择文件** retains SAF grants and previews. Only the platform external-storage provider can resolve to a direct folder, using a matching mounted storage volume, validated relative path, actual filesystem access and the user's broad-storage grant. Cloud and unknown providers remain documents; arbitrary content URIs are never guessed into paths. Saved grants and existing Imports copies are retained.

When a document-only folder needs a copy, **复制到 Waddle…** explains that edits do not sync back. After confirmation and successful import, the browser enters the local copy automatically and labels it **Waddle 文件 · 副本**. A compact completion message replaces the old large result card; changing locations clears stale notices. There is no automatic sync, export or write-back. Imports remain atomic and cancellable, limited to 512 MiB / 5,000 entries / 32 levels, with safe filenames/cycle detection and cleanup of incomplete staging copies. Text previews remain limited to 64 KiB. An interrupted process may leave private staging data, which is not presented as a completed import.

All-files access is declared for the terminal/file-workspace use case; any future Google Play publication requires the applicable permission review. This task does not publish a release or expand access to other apps' private directories.

### Viewing and taking files out

Images and local file links in Codex messages open the same full-screen viewer as Files. Image tool events (`imageView`, `imageGeneration`, MCP/dynamic/function outputs) attach thumbnails to their original messages. Quoted paths and Markdown links support Chinese/spaces; relative paths use the conversation/command cwd. Returning preserves the conversation position and service-owned tasks.

The viewer supports pinch/pan images (including camera orientation), PDF page navigation and zoom, video/audio play/pause/seeking, and source/formatted Markdown or HTML. **文件操作** provides **用其他应用打开**, **分享**, and **保存到手机**. Exported content URIs grant read access only to a snapshot of the selected file. Android 10+ saves images in `Pictures/Waddle` and other files in `Download/Waddle`; duplicate names receive a new name. Android 8–9 uses the system save picker. Nothing is automatically exported to public storage.

In a new terminal, run `waddle-open 'path/to/file.png'`, or ask terminal Codex to execute that command. The exported Bash function writes to that shell's private request directory, so captured stdout and detached child process groups do not hide the request. Closing the viewer leaves the shell running. These per-session functions disappear when their terminal closes.

Image previews are decoded off the UI thread (480 px thumbnails / 2560 px full views); PDF pages are rendered individually up to 2400 px. Text/source previews are limited to 64 KiB. Markdown supports common headings, emphasis, code, lists, quotes and tables; HTML/Markdown viewing is offline, disables JavaScript and blocks external/local resource loads. This is a document reader, not a browser for running sites. Media codec support follows the device. Android 8.x GIF preview is a still frame. Inline tool image data is bounded to 32 MiB encoded; local files use the regular decoder. Remote-only or attachment-ID-only image results without local bytes/path show a useful message instead of an empty image. Temporary share snapshots older than 24 hours are cleaned up on later exports.

### AI commands in the terminal

The default prompt is now `\w \$ ` (for example `~/my-project $`), with the full current path above the terminal. Only the exact old generated `.waddlerc` is migrated; custom shell configuration is preserved.

Tap **✦ 问 Codex，写命令…** at the bottom of the terminal, describe the task, and choose **生成命令**. The drawer shows the current session/path and lets you inspect the recent transcript being sent (last 12,000 characters). It shares the existing Codex login. The draft and explanation appear before anything runs; edit the draft, then press hardware Enter, the keyboard's Go action, or **运行命令 ↵**. Shift+Enter inserts a newline. Long/multiline Bash is supported up to 64 KiB; control characters other than newline/tab are rejected.

Execution replaces any unsubmitted terminal input, sources an immutable private script in the original Bash, and keeps `cd` and exported variables in that session. It does not start a separate task shell. Session ID, prompt epoch, actual working directory and PTY foreground ownership are checked. A changed directory, closed/switched session, continuation prompt or foreground program requires returning to a prompt and regenerating. Both Emacs and Vi Readline maps are supported; disabled Readline disables command submission. Closing the drawer, switching sessions or stopping generation cancels only this suggestion process. Activity recreation closes this transient drawer; terminal files and Codex login remain intact.

Suggestion generation uses its own ephemeral Codex 0.170.0 thread with `environments: []` at both thread/turn start. Shell/code-mode, agents, apps, plugins, hooks and web search are disabled. No native settings tools are registered, and client tool/approval requests are rejected. Enabled custom MCP servers cause a clear refusal before starting the suggestion thread. These are protocol/tool restrictions, not a claim that Android's filesystem sandbox works. Codex's own login/cache state may update normally; the model is not given filesystem access to inspect or modify the workspace during generation. The normal Codex page keeps its existing full task capabilities.

### Brightness changes (0.4.0)

The Codex panel also offers `waddle_set_display_setting`. It only accepts `brightness` (integer raw value 1–255) or `brightness_mode` (0 manual, 1 automatic), one setting per call.

1. Open **☰ → 设置与运行环境**, choose **系统授权** beside **允许修改亮度 · 每次确认** and grant Android's **修改系统设置** access to Waddle.
2. Return and enable **允许修改亮度 · 每次确认**. This switch is off initially and after disconnect.
3. Start a new conversation and request a brightness/mode change. Old threads retain their original tool list.
4. Check the proposed old → new value and choose **允许本次** or **拒绝**. Dismissing the dialog rejects it. The app reads back the setting before reporting success.

A fixed brightness requires manual mode; the tool never silently disables automatic brightness. Each mode/brightness change needs its own confirmation. Requests are rejected when permission is absent/revoked, the switch is off, the task is stale, or relevant values changed while confirmation was pending. Disabling the switch cancels pending display changes. Automatic brightness can vary during a mode confirmation; that dialog only approves the mode, not a fixed brightness.

The OS permission covers more system settings than this tool exposes. These checks constrain this native tool, not arbitrary code running with Waddle's UID. Enabling the automatic-mode flag does not prove a particular phone's light sensor or vendor brightness algorithm works correctly.

Model inference requires the network. Local execution means the CLI, helper, shell and files run on the phone; it does not mean the model runs offline.

## Architecture

- `WaddleRuntime` owns the shared private home, config, command symlinks and environment. Executable payloads live in APK-installed `nativeLibraryDir`; target SDK 35 is retained. Writable app storage contains data and symlinks to installed binaries.
- `TerminalWorkspace` creates real PTY Bash sessions using the vendored Termux terminal emulator/view components. `ShellIntegration` tracks prompts and uses Readline bindings for original-shell execution; its separate `CommandSuggester` owns the transient generation process. This source reuse does not depend on the Termux application.
- `TerminalService` owns the PTYs and `CodexWorkspace` (one `CodexController`/process per panel) independently of the Activity. Its visible notification can stop all sessions. It is not automatically restarted after process death.
- `CodexRpc` uses newline-delimited JSON-RPC over the embedded child's stdin/stdout. No localhost port, bearer token, OkHttp client or RUN_COMMAND bridge remains.
- `supervisor.c` puts app-server in a process group and acts as a child subreaper, cleaning up adopted descendants even when a shell creates its own session. Android can still terminate the app; it does not promise survival across force-stop, restart or background eviction.
- Codex protocol handling is pinned to 0.170.0. Dynamic tools use the experimental capability. Unknown client requests are rejected; not every advanced Codex interaction is supported yet.

The interactive Bash reads `~/.waddlerc`; users can customize the `waddle $ ` prompt. Long-press copies selected terminal text. Paste previews its content. Unsolicited OSC 52 clipboard writes are disabled.

## Verification

Terminal AI drawer (2026-11-19): API 35 ARM64 emulator build/lint and 145 unit tests passed. The final device run reported 18 cases: 17 executed successfully, one pre-existing opt-in full-agent case skipped. All seven new cases passed, including three live model/UI cases. Fold7 was disconnected on that date. On 2026-11-20, all 13 focused command/terminal device cases passed on Fold7 across the initial run and a three-case UI rerun after unlocking. Instrumentation-driven target stops were investigated after the user reported apparent crashes; the temporary test package was removed and the main app reopened normally. See the [implementation record](../../docs/dev/2026-11-19-02-terminal-ai-command.md).

Physical stage 2 (2026-11-19): Samsung SM-F966B, reported Android 17 / API 37, ARM64 with 4 KB pages. Fifteen device cases executed and passed; the ungranted-permission and opt-in lifecycle cases were skipped. Real Codex file edits, commands, terminal/shared login and native display writes passed. The user observed the actual screen brighten from raw 64 to 160 and auto-adjust after covering/uncovering the light sensor. Original raw brightness 103 was restored before re-enabling automatic mode; subsequent automatic readings varied with ambient light. The write toggle was switched off, the temporary test APK was removed, and the Waddle account remained signed in. Details are in the [implementation record](../../docs/dev/2026-11-19-01-codex-in-waddle.md).

Stage-1 follow-up (2026-11-19): 17 distinct device tests are covered across permission states and focused reruns on API 35 ARM64, after removing Termux for the emulator's current user while retaining its historical data. Added coverage proves stop during startup, no late write after stopping an active command, detached-child cleanup, background return, actual rotation and continued conversation. The real file test modifies existing files from both the panel and the terminal CLI using the same login. Noninteractive CLI writes explicitly use `codex --ask-for-approval on-request exec --sandbox workspace-write`; exit code alone is not considered proof of a file change. See the [stage-1 implementation and CI scope](../../docs/dev/2026-11-19-01-codex-in-waddle.md#实现说明实现者填写).

Settings reads report stored values, not a screenshot of Android Settings. On this emulator raw brightness 102 is displayed as 83% by the system's nonlinear slider; do not divide by 255 to derive that percentage. Its custom timeout 2147483647 ms appears as “Not set” in the Settings choices. The tool description makes these distinctions explicit.

Initial 0.4.0 brightness validation: 145 unit tests passed; assembly and lint passed with warnings. On Android 15 ARM64 with Termux disabled, all 15 device tests are covered across runs before and after the Android write-settings grant. The live run executes 14 tests and skips the missing-permission case; the earlier ungranted run covers that case. Real Codex requests changed brightness 102 → 96 and mode manual → automatic → manual, verified each result, rejected a denied request, and cancelled a pending request when the write switch was disabled. Cleanup restored 102/manual. A separate UI pass confirmed brightness 102 → 128 → 102 through the visible approval dialogs, with independent system reads.

The existing live model test also passed: native settings reads, file reading/writing, shell execution, terminal access to model-created output, Activity recreation and reconnect/resume. In 0.3.0, ten device tests additionally passed on a clean emulator without Termux installed, and a separate `codex exec` task launched from the actual Waddle terminal reused the login, copied a test file and successfully executed `cmp` and `id -u`.

The normal device suite covers native settings, strict tool parameters, embedded command versions and app UID, Git/file operations, app-server stdio/command execution, process exit, PTY behavior, UTF-8, resizing, Ctrl-C, IME input, independent sessions and Activity recreation. The live model test is opt-in and skipped by default.

Connect only the intended disposable device, or set `ANDROID_SERIAL`. Gradle's connected tests may uninstall the target afterward. For a signed-in Waddle environment, build/install the instrumentation APK and run it directly to retain login. **Instrumentation still force-stops the target process at startup/completion**, and ActivityScenario cases close/recreate screens; this can look like repeated crashes and ends live shell processes. Explain this before running tests on a user-held phone, keep it unlocked and foregrounded, and do not run instrumentation while the user is manually trying the app. Afterward remove only the temporary test package and reopen Waddle normally:

```sh
./gradlew assembleDebugAndroidTest
adb -s DEVICE_SERIAL install --user 0 -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s DEVICE_SERIAL shell am instrument --user 0 -w \
  -e class dev.waddle.android.CodexIntegrationTest \
  -e waddleLiveCodex true -e waddleNetwork true \
  dev.waddle.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL shell pm uninstall --user 0 dev.waddle.android.test
adb -s DEVICE_SERIAL shell am start --user 0 --display 0 -n dev.waddle.android/.MainActivity
```

For the command drawer, run `CommandSuggestionIntegrationTest` with `-e waddleLiveSuggestions true` on a signed-in test device. The opt-in cases use real model requests and test context-only generation plus the drawer's editable draft, hardware Enter and soft-keyboard Go. Ordinary cases cover prompt migration, Chinese/spaced paths, a 16 KB multiline command, preserved shell state, duplicate submission, Vi Readline, stale/closed/switched sessions, running processes, the `read` builtin, continuation prompts and cancellation. These tests create and remove only their own fixture directories. Keep physical-device installation scoped to `--user 0`.

`DisplaySettingsIntegrationTest` additionally checks argument rejection and missing permission. Its modifying tests are opt-in: `-e waddleDisplayWrites true` runs native roundtrips/stale-confirmation checks; `-e waddleLiveDisplay true` runs real model requests with denied, cancelled and accepted confirmations. Grant the OS permission through its Settings page first. These tests record original brightness/mode and restore them in `finally`; run only on the selected disposable test device.

`-e class dev.waddle.android.CodexFolderIntegrationTest -e waddleLiveFolder true` opts into a real-model cwd/file-write check using existing login. Without this flag, folder tests use deterministic protocol peers plus the actual bundled app-server for offline thread/cwd and relative command/file operations; they do not claim model verification.

`-e waddleLiveLifecycle true` enables the real-model lifecycle test: stop before thread/turn startup, stop a running foreground command and check that it cannot write later, continue chatting, return through the launcher after backgrounding, and rotate the actual display while keeping the conversation. This opt-in test temporarily changes emulator rotation and restores it. `CodexIntegrationTest` also verifies cleanup of shells with a separate session without requiring a model login.

The live test uses the selected account for real model requests, verifies native settings and a file/command task, reads model-created output from a terminal PTY, then recreates the Activity and reconnects/resumes. Optional network checks exercise curl TLS and Git HTTPS on public endpoints. No credentials or raw device output are committed. Current evidence and remaining gaps are recorded in [research](../../docs/research.md).

Shared-storage physical-device validation (2026-11-21): after the user manually enabled all-files access on Fold7, a real model task opened from Files used the selected original Download directory (including Chinese, spaces and a quote). It ran shell commands, read a randomized fixture, computed 42, and wrote/read back pwd.txt and result.json. Independent device checks confirmed exact cwd, unchanged input and no new Imports batch (2 before and after). Existing login was reused; no reinstall, data clearing, instrumentation or clone was involved in this check. The fixture and Codex 4 result remain available for user acceptance. See [original-folder validation](../../docs/dev/2026-11-21-04-file-locations.md).

## Boundaries and unfinished work

This bundles the tools needed for the current Codex workflow; it is not a general Linux distribution or package manager. Python, Node, compilers, SSH and all Git scripted porcelain are not included. Android restricts directly executing writable binaries; adding native tools requires packaging and validation.

Codex's Android `readOnly`/`workspaceWrite` isolation must not be treated as a reliable boundary inside Waddle's UID. Approval dialogs do not guarantee every command prompts. Android's boundary between app UIDs is separate. Other physical devices, API 26, 16 KB-page devices, long background operation and complete approval/cancellation behavior remain to be validated.

The historical `tools/probe-codex-termux.sh` and `tools/probe-android-access.sh` document the earlier feasibility work. They are not used by Waddle's build or runtime.

## Source and license

Waddle Android is GPL-3.0-only; the desktop keeps its original license. Preserve [terminal provenance](third-party/termux/PROVENANCE.md), [embedded runtime provenance](runtime/PROVENANCE.md), exact package locks and upstream notices. A public binary release must include corresponding sources as required by the bundled licenses.

References: [Codex app-server](https://learn.chatgpt.com/docs/app-server), [Android native library packaging](https://developer.android.com/ndk/guides/abis#native-code-in-app-packages), [Android app execution restrictions](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission).
