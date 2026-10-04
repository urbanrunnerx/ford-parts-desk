"""Real apksigner integration tests; disposable key stays in a temporary directory."""
import base64
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class ReleaseSigningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.suite = tempfile.TemporaryDirectory()
        cls.root = Path(cls.suite.name)
        cls.password = secrets.token_hex(24)
        cls.alias = 'disposable-release-test'
        cls.base_env = dict(os.environ, TEST_KEY_PASSWORD=cls.password)
        key = cls.root / 'test.jks'
        subprocess.run(['keytool', '-genkeypair', '-keystore', str(key), '-storetype', 'JKS',
                        '-alias', cls.alias, '-storepass:env', 'TEST_KEY_PASSWORD',
                        '-keypass:env', 'TEST_KEY_PASSWORD', '-keyalg', 'RSA', '-keysize', '2048',
                        '-validity', '2', '-dname', 'CN=Disposable CI Test'], env=cls.base_env,
                       check=True, capture_output=True)
        cert = subprocess.run(['keytool', '-exportcert', '-keystore', str(key), '-alias', cls.alias,
                               '-storepass:env', 'TEST_KEY_PASSWORD'], env=cls.base_env,
                              check=True, capture_output=True).stdout
        cls.fingerprint = hashlib.sha256(cert).hexdigest()
        cls.encoded_key = base64.b64encode(key.read_bytes()).decode()

    @classmethod
    def tearDownClass(cls):
        cls.suite.cleanup()

    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(dir=self.root)
        self.addCleanup(self.directory.cleanup)
        self.work = Path(self.directory.name)
        shutil.copytree(ROOT / '.github/scripts', self.work / '.github/scripts')
        (self.work / '.github/release-certificate.sha256').write_text(self.fingerprint + '\n')
        (self.work / 'release-input').mkdir()
        fixture = Path(os.environ.get('RELEASE_TEST_APK', ROOT / 'android/app/build/outputs/apk/release/app-release-unsigned.apk'))
        shutil.copyfile(fixture, self.work / 'release-input/app-release-unsigned.apk')
        (self.work / 'temp').mkdir()
        self.env = dict(self.base_env, RUNNER_TEMP=str(self.work / 'temp'),
                        RELEASE_TAG='v' + json.loads((ROOT / 'package.json').read_text())['version'],
                        ANDROID_KEYSTORE_BASE64=self.encoded_key, ANDROID_KEYSTORE_PASSWORD=self.password,
                        ANDROID_KEY_ALIAS=self.alias, ANDROID_KEY_PASSWORD=self.password)

    def run_script(self, script, *args, success=True):
        result = subprocess.run(['bash', '.github/scripts/' + script, *args], cwd=self.work,
                                env=self.env, text=True, capture_output=True)
        log = result.stdout + result.stderr
        self.assertNotIn(self.password, log)
        self.assertNotIn(self.encoded_key, log)
        self.assertNotIn(self.alias, log)
        if success:
            self.assertEqual(result.returncode, 0, log)
        else:
            self.assertNotEqual(result.returncode, 0, log)
        self.assertEqual(list((self.work / 'temp').iterdir()), [], 'Temporary signing material survived')
        return log

    def test_sign_verify_checksum_and_tampering(self):
        self.run_script('sign-release.sh')
        output = self.work / 'release-output'
        self.assertEqual(sorted(p.name for p in output.iterdir()), ['Ford-Parts-Desk.apk', 'SHA256SUMS.txt'])
        apk = output / 'Ford-Parts-Desk.apk'
        self.assertEqual((output / 'SHA256SUMS.txt').read_text(), hashlib.sha256(apk.read_bytes()).hexdigest() + '  Ford-Parts-Desk.apk\n')
        self.run_script('verify-release.sh', str(apk))
        with apk.open('ab') as stream:
            stream.write(b'tampered')
        self.run_script('verify-release.sh', str(apk), success=False)

    def test_missing_secret(self):
        del self.env['ANDROID_KEY_PASSWORD']
        self.assertIn('ANDROID_KEY_PASSWORD', self.run_script('sign-release.sh', success=False))
        self.assertFalse((self.work / 'release-output').exists())

    def test_bad_password(self):
        self.env['ANDROID_KEY_PASSWORD'] = 'incorrect-test-password'
        self.run_script('sign-release.sh', success=False)
        self.assertFalse((self.work / 'release-output').exists())

    def test_wrong_certificate(self):
        (self.work / '.github/release-certificate.sha256').write_text('0' * 64 + '\n')
        self.run_script('sign-release.sh', success=False)
        self.assertFalse((self.work / 'release-output').exists())

    def test_already_signed_input(self):
        self.run_script('sign-release.sh')
        shutil.copyfile(self.work / 'release-output/Ford-Parts-Desk.apk', self.work / 'release-input/app-release-unsigned.apk')
        shutil.rmtree(self.work / 'release-output')
        self.run_script('sign-release.sh', success=False)
        self.assertFalse((self.work / 'release-output').exists())

    def test_unsigned_debug_input(self):
        debug = Path(os.environ.get('RELEASE_TEST_DEBUG_APK', ROOT / 'android/app/build/outputs/apk/debug/app-debug.apk'))
        if not debug.is_file():
            self.skipTest('Debug fixture not available; this case runs after the CI build')
        # Repacking strips the signing block/JAR signatures, but keeps debuggable=true.
        with zipfile.ZipFile(debug) as source, zipfile.ZipFile(self.work / 'release-input/app-release-unsigned.apk', 'w') as target:
            for entry in source.infolist():
                if not entry.filename.startswith('META-INF/'):
                    target.writestr(entry, source.read(entry.filename))
        log = self.run_script('sign-release.sh', success=False)
        self.assertIn('Debuggable APKs cannot be released', log)
        self.assertFalse((self.work / 'release-output').exists())


if __name__ == '__main__':
    unittest.main()
