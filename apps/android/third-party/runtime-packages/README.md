> 0.6.0 / bundled agents stage 1: international builds select all 39 pinned inputs (20 per ABI); domestic builds select only terminal inputs (19 per ABI). Both Codex archives must accompany the source changes when the reviewer commits. They are ordinary Git inputs below 100 MiB each, not Git LFS or release downloads.

# Pinned upstream runtime dependencies

These 39 original Termux `.deb` files are **build inputs**, vendored like the
Gradle wrapper and the terminal component sources. They are not Waddle APKs,
generated/patched runtime files, or a developer's cache. Both ARM64 and x86_64
are included; the CA certificate package is shared. Total: 216,548,432 bytes
(about 207 MiB). Each file is below GitHub's 100 MiB file limit; no Git LFS,
credentials, separate download service or package installation is needed.

The `:` epoch separator in upstream filenames is stored as `%3A` so these
inputs can be checked out on Windows too; archive bytes are unchanged.

Versions, original upstream URLs and SHA-256 checksums remain in
[`packages.lock.json`](../../runtime/packages.lock.json) and
[`packages-x86_64.lock.json`](../../runtime/packages-x86_64.lock.json).
`prepare-runtime.py` checks every required input against those original locks
before changing generated output. It never downloads from the rolling Termux
repository and never substitutes packages from a local cache. A missing or
changed input fails the build. `--offline` remains accepted for compatibility;
runtime preparation is now always offline.

The snapshot was recovered on 2026-11-21 from the previously downloaded original
packages, with all 39 hashes verified against the existing locks. No package
version or byte was changed. This preserves the inputs even when upstream
removes old versions. Do not replace this directory with ignored cache files,
CI artifacts with expiry dates, or links to a rolling mirror.

Upstream attribution, corresponding source recipes and licenses are documented
in [runtime provenance](../../runtime/PROVENANCE.md) and
[`sources.json`](../../runtime/sources.json). Package-provided copyright/license
notices are retained inside the original archives and copied into the APK by
the preparation script. Their respective licenses continue to apply.

To verify the snapshot from `apps/android`:

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
```

If intentionally updating a dependency in future, update the original package,
lock, source/patch references and license records together, then verify both
ABIs from a fresh source copy. Never add generated APKs, transformed `.so` files,
extracted package trees, SDKs, signing keys, or device data to this directory.
