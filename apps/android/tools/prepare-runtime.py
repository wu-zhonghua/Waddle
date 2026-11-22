#!/usr/bin/env python3
"""Build Waddle's APK runtime from vendored, hash-pinned Android packages.

No device/Termux installation is read. Only APK-installed ELF files are executable;
data assets and command symlinks are created under Waddle's own private directory.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'runtime/generated'
CACHE = ROOT / 'runtime/cache'
PACKAGES = ROOT / 'third-party/runtime-packages'
PREFIX = 'data/data/com.termux/files/usr/'


def unpack(deb, destination):
    data = deb.read_bytes()
    assert data[:8] == b'!<arch>\n'
    pos = 8
    while pos + 60 <= len(data):
        header = data[pos:pos + 60]
        size = int(header[48:58]); name = header[:16].decode().strip().rstrip('/')
        pos += 60
        if name.startswith('data.tar'):
            with tarfile.open(fileobj=io.BytesIO(data[pos:pos + size])) as archive:
                # Extract regular files only; never materialize package absolute symlinks.
                for member in archive:
                    path = member.name.removeprefix('./')
                    if not member.isfile() or not path.startswith(PREFIX):
                        continue
                    relative = Path(path[len(PREFIX):])
                    if '..' in relative.parts:
                        raise ValueError('Unsafe package path')
                    dest = destination / relative
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    dest.write_bytes(archive.extractfile(member).read())
            return
        pos += size + size % 2
    raise ValueError('Missing Debian package payload')


def verified_packages(lock, directory=PACKAGES):
    packages = []
    for package in lock['packages']:
        # Escape Debian epoch separators so a source checkout also works on Windows.
        deb = directory / Path(package['Filename']).name.replace(':', '%3A')
        if not deb.is_file():
            raise FileNotFoundError(f'Missing vendored runtime dependency: {deb}. Restore it from the checkout.')
        if hashlib.sha256(deb.read_bytes()).hexdigest() != package['SHA256']:
            raise ValueError(f'Package hash mismatch: {deb}. Restore the locked dependency from the checkout.')
        packages.append((package, deb))
    return packages


def terminal_packages(lock):
    """Keep the historical lock intact while selecting only terminal packages."""
    return {**lock, "packages": [p for p in lock["packages"] if p["Package"] != "codex"]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ndk', default=os.environ.get('ANDROID_NDK_HOME'))
    parser.add_argument('--offline', action='store_true',
                        help='Compatibility flag: runtime preparation is always offline')
    parser.add_argument('--abi', choices=['arm64-v8a', 'x86_64'], default='arm64-v8a')
    parser.add_argument('--output', type=Path, default=OUT)
    args = parser.parse_args()
    lockfile = ROOT / 'runtime' / ('packages.lock.json' if args.abi == 'arm64-v8a' else 'packages-x86_64.lock.json')
    lock = json.loads(lockfile.read_text())
    # Legacy lock/input archives are retained as provenance, not part of the app.
    lock = terminal_packages(lock)
    if not args.ndk:
        parser.error('Set ANDROID_NDK_HOME or --ndk to Android NDK 28.2.13676358')
    readelf = next(Path(args.ndk).glob('toolchains/llvm/prebuilt/*/bin/llvm-readelf'))
    # Verify every input before changing generated output. Never fall back to the
    # rolling repository or an old local download, even if a vendored file is bad.
    packages = verified_packages(lock)
    CACHE.mkdir(parents=True, exist_ok=True)
    # Remove only this tool's known generated children, never an arbitrary output root.
    for child in ['jniLibs', 'assets']:
        if (args.output / child).exists(): shutil.rmtree(args.output / child)
    native = args.output / 'jniLibs' / lock['abi']
    assets = args.output / 'assets/runtime'
    native.mkdir(parents=True); assets.mkdir(parents=True)
    roots = {}
    for package, deb in packages:
        root = CACHE / 'unpack' / package['Package']
        if root.exists(): shutil.rmtree(root)
        unpack(deb, root); roots[package['Package']] = root
        docs = root / 'share/doc'
        if docs.exists():
            for path in docs.rglob('*'):
                if path.is_file() and any(s in path.name.lower() for s in ['copyright', 'license', 'notice', 'copying']):
                    dest = assets / 'licenses' / package['Package'] / path.relative_to(docs)
                    dest.parent.mkdir(parents=True, exist_ok=True); shutil.copyfile(path, dest)
    binaries = {
        'bash': ('bash', 'bin/bash'), 'git': ('git', 'bin/git'),
        'rg': ('ripgrep', 'bin/rg'), 'curl': ('curl', 'bin/curl'),
    }
    files = []
    command_map = {}
    for name, (package, path) in binaries.items():
        filename = 'libwaddle_' + name + '.so'
        command_map[name] = filename
        files.append((roots[package] / path, filename))
    # Native Git HTTPS transport helper; scripted Git porcelain is not bundled.
    for name in ['git-remote-http', 'git-remote-https']:
        command_map[name] = 'libwaddle_git_remote_http.so'
    files.append((roots['git'] / 'libexec/git-core/git-remote-http', 'libwaddle_git_remote_http.so'))
    rename = {}
    for root in roots.values():
        for lib in sorted((root / 'lib').glob('*.so*')):
            dynamic = subprocess.check_output([str(readelf), '-d', str(lib)], text=True)
            soname = re.search(r'\(SONAME\).*?\[(.*?)\]', dynamic)
            name = soname.group(1) if soname else lib.name
            mapped = re.sub(r'\.so\.(.+)$', r'_\1.so', name)
            assert len(mapped) == len(name)
            if mapped != name: rename[name.encode()] = mapped.encode()
            files.append((lib, mapped))
    for source, name in files:
        data = source.read_bytes()
        if data[:4] != b'\x7fELF': raise ValueError('Expected ELF: ' + str(source))
        # Equal-length replacements preserve ELF/Rust string offsets. Record all edits.
        for old, new in rename.items(): data = data.replace(old + b'\0', new + b'\0')
        old_runpath = b'/data/data/com.termux/files/usr/lib\0'
        data = data.replace(old_runpath, b'$ORIGIN' + b'\0' * (len(old_runpath) - 7))
        dest = native / name
        dest.write_bytes(data); dest.chmod(0o755)
    shutil.copyfile(roots['ca-certificates'] / 'etc/tls/cert.pem', assets / 'cert.pem')
    for pkg, path, target in [('git', 'share/git-core/templates', 'git-template'), ('ncurses', 'share/terminfo', 'terminfo')]:
        source = roots[pkg] / path
        if source.exists(): shutil.copytree(source, assets / target)
    extra_licenses = ROOT / 'runtime/licenses'
    if extra_licenses.exists(): shutil.copytree(extra_licenses, assets / 'licenses/upstream', dirs_exist_ok=True)
    (assets / 'packages.lock.json').write_text(json.dumps(lock, indent=2) + '\n')
    manifest = {'version': 'terminal-0.5.0', 'abi': lock['abi'], 'commands': command_map,
                'files': sorted(str(p.relative_to(assets)) for p in assets.rglob('*') if p.is_file())}
    (assets / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    print(f'Prepared {len(files)} native executables/libraries for {lock["abi"]}')


if __name__ == '__main__': main()
