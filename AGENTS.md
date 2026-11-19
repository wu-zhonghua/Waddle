# Waddle workspace

- `apps/android` is the Android terminal. Build and test it with its Gradle wrapper.
- `apps/desktop` is the original desktop app. Follow its own `AGENTS.md` and `.kilocode` guides when changing desktop code.
- `.github` contains repository workflows; action paths are relative to the repository root, even when run steps use a different working directory.
- Keep the two applications independently buildable. Shared protocols belong in `protocols` only when both clients consume them.
- Preserve upstream attribution in vendored Android terminal code. Record changes in its provenance document.
- Never commit local SDK paths, credentials, signing keys, generated builds, or device output containing secrets.
- Do not publish a release or push changes unless requested.
