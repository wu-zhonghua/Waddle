# Waddle for Android

Native Android terminal MVP. Kotlin/Compose owns the application UI, while the vendored Termux terminal-view/emulator components provide terminal input, rendering, escape parsing and JNI PTY support. It launches `/system/bin/sh` as Waddle's app UID, without root or a Termux installation.

## Build

Install JDK 17 and Android SDK platform 36, build-tools 36.0.0 and NDK 28.2.13676358. Set `JAVA_HOME` to your JDK. Set `ANDROID_HOME` to your SDK, or create a local, untracked `local.properties`:

```properties
sdk.dir=/absolute/path/to/android-sdk
```

Run from this directory:

```sh
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The last command installs test APKs and runs tests on connected devices. Connect only the intended test device, or set `ANDROID_SERIAL` to its serial number.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

```sh
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -n dev.waddle.android/.MainActivity
```

The app targets API 35, supports API 26+, and packages `arm64-v8a` and `x86_64`. Debug APKs are for development, not a signed production release.

## Implementation

- `TerminalWorkspace` owns sessions and app-private home/cache directories. Each session has a separate PTY/process and scrollback buffer.
- `TerminalService` holds sessions independently of the Activity. It uses an explicit user-started `specialUse` foreground service with a visible notification and a stop action.
- `TerminalScreen` hosts `TerminalView` in Compose; terminal input is sent directly to its session, including IME composition and hardware keys.
- Closing a session terminates its shell. Activity recreation reattaches to the existing session instead of launching a duplicate process. The service is not automatically restarted after process death.
- Long-press copies the selected text. Paste previews its text before sending it. Unsolicited OSC 52 clipboard writes are disabled.

Home lives in the app's private `files/home` directory. Files survive normal relaunches; uninstalling or clearing app data removes them. This is a system-shell environment, not a complete Linux distribution. No package manager, SSH, network permission or AI runtime is included yet.

The interactive shell reads `~/.waddlerc` through `ENV`. Its initial prompt is `waddle $ `; users can edit this file to customize their shell settings.

Android can terminate background work despite a foreground service. This version does not promise survival across force-stop, device restart, or system process eviction. Special-use service declarations and distribution requirements need review before store submission.

## Verification

Upstream emulator unit tests cover escape handling, Unicode, screen buffers and reflow. `TerminalIntegrationTest` exercises real Android PTYs, UTF-8, resize, Ctrl-C, session isolation, exit codes, IME composition, and Activity recreation.

Local validation: 145 unit tests and 5 device integration tests passed on an ARM64 API 35 emulator. Debug assembly and lint passed (warnings remain). Physical devices and API 26 have not yet been validated. See the [workspace verification record](../../docs/workspace-migration.md#本地验证).

## Source and license

Waddle's Android code is licensed under GPL-3.0-only. See [LICENSE](LICENSE) and the [terminal component provenance](third-party/termux/PROVENANCE.md). The desktop application retains its original license. Build definitions are maintained locally; upstream source modifications must be recorded in the provenance file.
