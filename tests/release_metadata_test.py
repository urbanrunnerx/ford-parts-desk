import importlib.util
import json
from pathlib import Path
import tempfile
import os
import unittest

spec = importlib.util.spec_from_file_location('release_metadata', Path(__file__).resolve().parents[1] / '.github/scripts/release-metadata.py')
metadata = importlib.util.module_from_spec(spec)
spec.loader.exec_module(metadata)


class ReleaseMetadataTest(unittest.TestCase):
    def test_valid_apk(self):
        metadata.apk('v3.0.1', self.badging())

    @staticmethod
    def badging():
        return "package: name='dev.urbanrunnerx.partsdesk' versionCode='30001' versionName='3.0.1'\nsdkVersion:'26'\n"

    def test_invalid_tag(self):
        for tag in ['v3.0.1-rc1', 'v03.0.1', 'v3.100.0', 'v3.0.01', 'v3.0.1;echo bad', 'v999999.0.0']:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                metadata.version(tag)

    def test_bad_apk_metadata(self):
        good = self.badging()
        for bad in [good + 'application-debuggable\n', good.replace('partsdesk', 'other'),
                    good.replace('30001', '30000'), good.replace('3.0.1', '3.0.0'),
                    good.replace("'26'", "'25'"), '']:
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                metadata.apk('v3.0.1', bad)

    def test_source_and_release_history(self):
        previous = os.getcwd()
        with tempfile.TemporaryDirectory() as directory:
            try:
                os.chdir(directory)
                Path('android/app').mkdir(parents=True)
                Path('android/app/build.gradle').write_text("versionName '3.0.1'\nversionCode 30001\n")
                Path('package.json').write_text(json.dumps({'version': '3.0.1'}))
                shipped = {'tag_name': 'v3.0.0', 'draft': False, 'prerelease': False}
                metadata.source('v3.0.1', [shipped])
                for release in [dict(shipped, tag_name='v3.0.1'), dict(shipped, tag_name='v3.0.1', draft=True),
                                dict(shipped, tag_name='v3.1.0'), dict(shipped, tag_name='unknown')]:
                    with self.subTest(release=release), self.assertRaises(ValueError):
                        metadata.source('v3.0.1', [release])
                with self.assertRaises(ValueError):
                    metadata.source('v3.0.0', [])
                Path('package.json').write_text(json.dumps({'version': '3.0.2'}))
                with self.assertRaises(ValueError):
                    metadata.source('v3.0.1', [])
                with self.assertRaises(ValueError):
                    metadata.source('v3.0.2', [])
            finally:
                os.chdir(previous)


if __name__ == '__main__':
    unittest.main()
