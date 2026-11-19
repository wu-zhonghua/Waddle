# Terminal component provenance

Source: https://github.com/termux/termux-app

Pinned commit: `8629e632fcb95da272221be327db653fb24befe9`.

Imported paths: `terminal-emulator/src` and `terminal-view/src`, including upstream tests. The Gradle wrapper launcher and JAR also originate from this checkout; the distribution is pinned separately.

Upstream repository licensing is described in `UPSTREAM-LICENSE.md`: GPL-3.0-only with Apache-2.0 portions. This Android distribution retains those notices and includes GPL-3.0-only and Apache-2.0 license texts. It does not change the desktop application's Apache-2.0 license. Waddle-authored Android code is provided under GPL-3.0-only.

Local integration changes:

- Independent Gradle Kotlin build definitions, SDK 36, min SDK 26, NDK 28.2.
- Minimal library manifests without deprecated package attributes.
- User-initiated selection copies directly to the clipboard, independently of the disabled OSC 52 callback; selection paste is routed through the host's preview dialog.
- No Termux application, bootstrap, package manager or termux-shared code is included.

Keep this document updated when modifying upstream source.
