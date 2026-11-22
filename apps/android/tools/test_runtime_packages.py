"""Regression checks for the offline, pinned build inputs (no SDK required)."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('prepare_runtime', Path(__file__).with_name('prepare-runtime.py'))
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class RuntimePackagesTest(unittest.TestCase):
    def test_both_architectures_are_complete_and_match_original_locks(self):
        expected = set()
        for name in ('packages.lock.json', 'packages-x86_64.lock.json'):
            lock = json.loads((runtime.ROOT / 'runtime' / name).read_text())
            packages = runtime.verified_packages(lock)
            self.assertEqual(len(packages), 20)
            expected.update(deb.name for _, deb in packages)
        self.assertEqual(expected, {p.name for p in runtime.PACKAGES.glob('*.deb')})
        self.assertEqual(len(expected), 39)  # CA certificates are shared.

    def test_terminal_selection_excludes_codex_without_modifying_historical_lock(self):
        for name in ('packages.lock.json', 'packages-x86_64.lock.json'):
            lock = json.loads((runtime.ROOT / 'runtime' / name).read_text())
            selected = runtime.terminal_packages(lock)
            self.assertIn('codex', {p['Package'] for p in lock['packages']})
            self.assertNotIn('codex', {p['Package'] for p in selected['packages']})
            self.assertEqual(len(runtime.verified_packages(selected)), 19)
            self.assertTrue({'bash', 'git', 'curl', 'ripgrep'} <= {p['Package'] for p in selected['packages']})

    def test_missing_or_corrupt_vendored_input_fails_without_cache_or_network_fallback(self):
        payload = b'pinned dependency'
        lock = {'packages': [{'Package': 'fixture', 'Filename': 'pool/fixture_1:1_all.deb',
                              'SHA256': hashlib.sha256(payload).hexdigest()}]}
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            with self.assertRaisesRegex(FileNotFoundError, 'Missing vendored runtime dependency'):
                runtime.verified_packages(lock, directory)
            deb = directory / 'fixture_1%3A1_all.deb'
            deb.write_bytes(b'truncated or replaced input')
            with self.assertRaisesRegex(ValueError, 'Package hash mismatch'):
                runtime.verified_packages(lock, directory)
            deb.write_bytes(payload)
            self.assertEqual(runtime.verified_packages(lock, directory), [(lock['packages'][0], deb)])


if __name__ == '__main__':
    unittest.main()
