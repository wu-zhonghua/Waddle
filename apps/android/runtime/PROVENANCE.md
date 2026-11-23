# Waddle embedded runtime

## 2026-11-22: bundled agents stage 1 (0.6.0)

International builds restore the original, hash-pinned Codex 0.170.0 and helper
from the two original `.deb` archives now retained as source-controlled build
inputs alongside the 37 terminal archives. No package bytes or locks changed.
The helper-name rewrite, equal-length ELF edits and NDK-built supervisor below
apply again. Existing upstream notices and source recipe attribution are retained.
No vendored Termux terminal component source was modified.

`--distribution international` (default) selects all packages; `domestic` selects
terminal packages before verification/extraction/license copying. Each produces a
separate `runtime/generated/<distribution>/{jniLibs,assets}` tree with a tagged
manifest. Gradle uses `-PwaddleDistribution=domestic` to switch and rejects a
mismatched ABI/distribution or agent binaries in a domestic payload. Domestic
builds omit Codex, its helper and supervisor and all Codex-specific UI entry points.

The restored runtime uses the old private `home/.codex` for shared CLI/app-server
auth. International first launch creates that directory; domestic preparation
never creates or deletes it. Only obsolete Waddle-owned executable symlinks are
removed when changing distributions. User files and auth are retained.

The shell integration adds an exported `codex` function for bare interactive
launches, with private per-terminal request/reply files carrying cwd and interface
choice. Flags retain normal CLI behavior; explicit `--waddle-chat` and
`--waddle-terminal` override preferences. No external service or executable is
downloaded at runtime. App-server conversations have independent supervisors;
command suggestions use an ephemeral thread without execution environments/tools.

The 0.5.0 section below records the previous implementation, not the current
international distribution.

## 2026-11-22: AI terminal stage 1 (0.5.0)

The current APK selects Bash, Git, ripgrep, curl, HTTPS transport, certificates,
terminfo and shared-library dependencies from the same pinned upstream inputs.
`terminal_packages()` excludes the Codex package **before verification/extraction
and license copying**. Codex, code-mode helper and Waddle's process supervisor
are no longer packaged or invoked. The generated package lock lists only selected
packages; the original locks, archives and source records stay unchanged as
historical provenance. This preserves the previous offline-build work.

ELF SONAME/NEEDED and RUNPATH adaptations below still apply to terminal binaries.
The Codex helper-name rewrite and supervisor compilation have been removed from
the preparation script. `CODEX_HOME` is no longer injected into terminal sessions.
On upgrade only symlinks to Waddle's former Codex/helper libraries are removed;
user home, custom `.waddlerc`, imported files and old `.codex` data are retained.
The generated manifest version is `terminal-0.5.0`. Gradle rejects stale generated
Codex/helper/supervisor binaries. No vendored terminal-component source was changed.

The following record describes the original 0.3/0.4 runtime and its attribution;
Codex-specific transformations/licensing are historical, not current APK contents.

Waddle 0.3.0 embeds Android-native programs in its APK. It does not install, start,
or communicate with the Termux application. The programs run as Waddle's own UID,
with the same private home directory as the terminal.

## Binary sources and reproducibility

The public Termux package repository supplies Android/Bionic builds of Codex
0.170.0, its code-mode helper, Bash, Git, ripgrep, curl and their shared libraries.
This reuse retains the upstream licenses; it is not a claim that Waddle authored
these programs. Exact package versions, URLs and SHA-256 values are recorded in
`packages.lock.json` (ARM64) and `packages-x86_64.lock.json` (CI emulator).

`sources.json` links to source recipes and patches pinned to termux-packages commit
`15e04b447d07a1f82bd824797490cebc88837850`, plus the Codex source archive and hash.
The curl executable is a subpackage of libcurl. libc++ is the Android NDK r30
runtime supplied by that package; our small supervisor is built with NDK 28.2.
The recipes identify upstream source archives, checksums, configuration and patches.

Run `python3 tools/prepare-runtime.py --ndk <NDK 28.2 directory>` from the Android
root before Gradle. Since 2026-11-21, the script reads original packages from
`third-party/runtime-packages`, verifies all inputs against the unchanged locks,
extracts regular files into ignored cache without executing package scripts, and
generates ignored `runtime/generated/{jniLibs,assets}`. No network or prior cache
is needed for this step. `--offline` is retained as a compatibility flag. The 39
original upstream dependency archives (216,548,432 bytes, both ABIs) were recovered
from the existing download cache, each matching its already-recorded SHA-256.
They are now versioned build inputs, not generated Waddle outputs. No versions,
original URLs, checksums, binary transformations or package bytes changed. This
avoids relying on upstream retaining old packages in its rolling repository.
See the [snapshot record](../third-party/runtime-packages/README.md).
For the x86_64 CI emulator use `--abi x86_64`, then Gradle `-PwaddleAbi=x86_64`.
Each preparation produces one ABI; ARM64 is the default distributed debug build.

## Local adaptations

- ELF executables are packaged as `libwaddle_*.so` and extracted by Android's
  package installer into `nativeLibraryDir`. App-writable directories contain
  command symlinks and data, never copied executable payloads. targetSdk stays 35.
- Versioned ELF SONAME/NEEDED strings such as `libssl.so.3` become equal-length
  APK-compatible names such as `libssl_3.so`. All selected native files receive
  the same replacements, including references in ELF version tables.
- The exact Termux library RUNPATH becomes `$ORIGIN` with NUL padding. Runtime
  `LD_LIBRARY_PATH` also points to Waddle's installed native library directory.
- Codex's fixed 20-byte helper name `codex-code-mode-host` becomes the equal-length
  `libwaddle_helper_.so`, so it finds the packaged helper beside its own binary.
  No Rust string offsets or code lengths change. Other embedded upstream strings
  may still mention Termux; this is not a source rebuild for a different prefix.
- Environment variables relocate HOME, CODEX_HOME, PATH, certificates, Git
  exec/template directories and terminfo to Waddle. Git's native HTTPS transport
  is included. Unselected scripted Git porcelain and an SSH client are not bundled.
- `supervisor.c` is Waddle-authored GPL-3.0-only code. It creates a process group
  for app-server, forwards termination and acts as a Linux child subreaper. On
  shutdown it kills/reaps adopted descendants, including shells that created a
  separate session; original-group termination alone did not stop those commands
  in the stage-1 real-model interruption test. This scans only parent IDs in
  `/proc/*/stat` and signals this supervisor's own direct/adopted children.
  It does not elevate privileges. The panel uses private stdio pipes, not TCP.

The package preparation logic is the complete binary transformation recipe. Both
ARM64 and x86_64 generated ELF dependency closures were checked locally; all
non-system NEEDED entries resolve to packaged libraries. Only ARM64 was run on
a device in this session.
Generated Waddle binaries, extracted caches, credentials and device outputs are
not committed. Only the original, hash-verified third-party dependency archives
are vendored alongside their existing source and license records.
Keep the source recipes and exact corresponding upstream sources available when
distributing binaries under their respective copyleft licenses. A public release
must also supply the corresponding-source distribution, not just this debug APK.

## Licensing

- Waddle integration/supervisor and Bash/readline: GPL-3.0 (Waddle: GPL-3.0-only).
- Git: GPL-2.0; libiconv libraries: LGPL-2.1 (upstream package also includes GPL parts).
- Codex: upstream Apache-2.0/MIT notices; ripgrep: MIT/Unlicense.
- OpenSSL: Apache-2.0; LLVM libc++: upstream LLVM/NCSA notices and exceptions.
- CA certificate bundle: MPL-2.0. Remaining libraries retain their package notices.

Additional complete texts are in `licenses/`. The preparation script also copies
the licenses/copyright notices supplied by each selected package into APK assets.
Terminal rendering code has its own [provenance](../third-party/termux/PROVENANCE.md).

## Limits

This is a bounded bundled toolchain, not the whole Termux package ecosystem.
Native programs cannot simply be downloaded into app-writable home and executed
on targetSdk 35. Run text scripts via `bash script.sh` / `sh script.sh`. Additional
native tools require packaging and validation. Model inference still needs a
network connection. Android's per-app UID boundary remains; reliable isolation
between an agent and other files inside that same UID is not established.
