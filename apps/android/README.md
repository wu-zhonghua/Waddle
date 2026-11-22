# Waddle for Android

Waddle 0.5.0 is an Android **AI terminal**. The APK includes Bash, Git, ripgrep, curl, their native libraries and CA certificates. It does not include Codex, a Codex login/panel, or any downloadable extensions. No Termux app or computer service is required. The terminal works without AI configuration or a network connection.

## Build

Use JDK 17, Android SDK platform 36, build-tools 36.0.0 and NDK 28.2.13676358. Set `JAVA_HOME` and `ANDROID_HOME`, or use an untracked `local.properties` for the SDK path.

```sh
cd apps/android
python3 tools/prepare-runtime.py --ndk "$ANDROID_HOME/ndk/28.2.13676358"
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew assembleDebugAndroidTest
```

Runtime preparation is offline. Original upstream packages remain pinned in [`third-party/runtime-packages`](third-party/runtime-packages/README.md); the preparation script selects terminal packages only and verifies their hashes. The historical Codex archives/locks remain provenance inputs in the repository, but Codex, its helper, supervisor and package notices are excluded from generated APK contents. Gradle rejects old generated Codex payloads: rerun preparation before building. Initial Gradle/SDK dependency installation still requires internet access. See [runtime provenance](runtime/PROVENANCE.md).

The default APK is ARM64, Android 8+ (API 26), target SDK 35. For x86_64 CI prepare with `--abi x86_64` and build with `-PwaddleAbi=x86_64`. Each preparation emits one ABI. Generated runtime and build outputs stay ignored.

```sh
adb -s DEVICE_SERIAL install --user 0 -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start --user 0 -n dev.waddle.android/.MainActivity
```

Covering an existing installation preserves `files/home`, imported files and custom shell settings. Only obsolete Waddle-managed Codex executable symlinks are removed; old user-created files and `.codex` data are not erased. There is no entry point to that old login or history. Uninstalling or clearing app data deletes local files and AI keys. Do not use an unrestricted install on a phone with multiple Android profiles; target its intended user explicitly.

## Terminal and files

- The drawer lists files and up to eight independent terminal sessions. Sessions live in a foreground service and survive Activity recreation and brief backgrounding; process death/reboot ends running shells.
- The prompt and title show the current directory. Long-press copies selected text; two fingers resize text. Ctrl affects the next key, `^C` interrupts, Paste previews clipboard contents, Keys opens the keyboard. Unsolicited OSC 52 clipboard writes are disabled.
- Files offers **手机存储**, **Waddle 文件**, and **其他来源**. Browsing does not change a shell's cwd. **打开终端** and a folder's **在此打开…** create a new shell in that folder; **更多 → 切换当前终端到此目录** explicitly changes the existing idle shell.
- Android 11+ phone storage uses user-granted all-files access to edit original files. Other apps' private data and Android/data remain restricted. Document-only/cloud sources require an explicit **复制到 Waddle…** operation, then open the local copy; changes do not sync back. Android 8–10 retain private files and document-provider access.
- Image pinch/pan, PDF pages, audio/video playback, Markdown/HTML/text preview, opening in another app, sharing, and saving to phone remain available. The document reader disables JavaScript and external resources. `waddle-open 'path/to/file.png'` opens a file from the current shell, including child Bash processes.
- Imports are bounded to 512 MiB, 5,000 entries and 32 levels with atomic publication and cancellation. Text previews are bounded to 64 KiB; large images/PDF pages are downsampled. Shares grant read access to a snapshot of the selected file only.

Bash, Git, `rg`, `curl`, Android system commands and `bash script.sh` work locally. Python, Node, SSH, a package manager and Git scripted porcelain are not bundled.

## AI model settings

Open **☰ → 设置与运行环境 → AI 模型** (the dialog title). Select **DeepSeek**, **通义千问**, **Kimi**, **智谱**, or **自定义（OpenAI 兼容）**. Select a suggested model or type an exact model ID, enter a Key belonging to that service and endpoint, then **测试连接** and **保存**. Tests use the current form; a successful test does not automatically save changes. Model availability depends on the service and account.

Endpoints accept an HTTPS Base URL or the complete `/chat/completions` URL. Custom gateways must support non-streaming Chat Completions, system/user messages and text responses. Redirects, URLs containing user info/query credentials, and cleartext HTTP are rejected. Preset endpoints can also be edited for a service's regional/workspace address. Changing an endpoint requires entering the Key again so a saved Key is never silently sent to a different server.

Official preset references: [DeepSeek](https://api-docs.deepseek.com/quick_start/agent_integrations/), [Qwen](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope), [Kimi](https://platform.kimi.com/docs/get-api-key), [Zhipu](https://docs.bigmodel.cn/cn/guide/develop/openai/introduction). The Qwen preset uses the still-supported Beijing DashScope endpoint; users can enter their workspace-specific endpoint.

Keys are encrypted using a non-exportable Android Keystore AES-GCM key and private app preferences, separately per provider. Backup is disabled. Saved keys are never filled back into the UI, shell environment or model context. Leave the Key field blank to preserve it for the same endpoint, type a replacement, or choose **删除已保存的 Key**. There is no key-reveal button. HTTP errors are translated into messages for invalid/expired Keys, insufficient balance/quota, network/TLS problems, rate limiting and unavailable models without displaying raw server text. Waddle does not supply accounts or credits.

## Generate commands and explain failures (stage 1)

1. At a Bash prompt, tap **✦ 问 AI，写命令…**. Without configured AI, **去设置** opens model settings directly.
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
adb -s DEVICE_SERIAL shell am instrument --user 0 -w dev.waddle.android.test/androidx.test.runner.AndroidJUnitRunner
adb -s DEVICE_SERIAL shell pm uninstall --user 0 dev.waddle.android.test
adb -s DEVICE_SERIAL shell am start --user 0 -n dev.waddle.android/.MainActivity
```

Instrumentation force-stops the app and ends live shells. Do not run it while someone is manually using that device. Default tests use deterministic AI HTTP peers and temporary encrypted test profiles; they never need or log a real Key. They cover request construction, errors, credentials, cancellation, editable command/diagnosis UI, exactly-once execution and shell/file regressions. `-e waddleLiveSuggestions true` opts into the real model context/generation test using a model already configured through settings. `-e waddleNetwork true` enables curl/Git public HTTPS checks. The shared-storage roundtrip requires a manually granted permission and `-e waddleStorage granted`.

Actual results and remaining Fold7/live-provider/CI acceptance steps are recorded in [stage 1 implementation](../../docs/dev/2026-11-22-01-ai-terminal.md). Mocked protocol tests do not establish real-provider or domestic-network connectivity.

## Source and license

Waddle Android is GPL-3.0-only; the desktop keeps its original license. Preserve [terminal provenance](third-party/termux/PROVENANCE.md), [runtime provenance](runtime/PROVENANCE.md), upstream source records, locks and notices. A public binary release requires the corresponding sources mandated by bundled licenses. This work does not publish a release.
