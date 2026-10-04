"""Offline regression fixtures for the official packaging importer."""
import csv
import importlib.util
import tempfile
import unittest
from pathlib import Path

SPEC = importlib.util.spec_from_file_location('packaging_import', Path(__file__).resolve().parents[1] / 'scripts/import-packaging.py')
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class PackagingImportTests(unittest.TestCase):
    def extract_rows(self, rows, headers=None):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'fixture.csv'
            with path.open('w', newline='', encoding='utf-8-sig') as out:
                writer = csv.writer(out)
                writer.writerow(headers or MODULE.COLUMNS)
                writer.writerows(rows)
            return MODULE.extract(path, '2026-10-04')

    def test_exact_base_field_and_leading_zeros_are_preserved(self):
        result = self.extract_rows([
            ['AB1Z', '043B13', 'A', 'AB1Z-043B13-A', 'FIXTURE'],
            ['AB1Z', '043B13', 'B', 'AB1Z043B13B', 'FIXTURE'],
            ['AB1Z', '78240A52', 'AA', 'AB1Z78240A52AA', 'PANEL'],
        ])
        self.assertEqual(result['distinctBases'], 2)
        self.assertEqual(result['entries'][0]['base'], '043B13')
        self.assertEqual(result['entries'][0]['serviceCount'], 2)

    def test_invalid_and_unreconstructable_rows_do_not_invent_bases(self):
        result = self.extract_rows([
            ['AB1Z', '1234', 'A', 'AB1Z1234A', 'FIXTURE'],
            ['', '12345', '', '12345', 'FINIS'],
            ['AB1Z', '12', 'A', 'AB1Z12A', 'TOO SHORT'],
            ['AB1Z', '3456', 'A', 'AB1Z9999A', 'WRONG BASE'],
            ['AB1Z', '4567', 'A', 'AB1Z4567A', ''],
        ])
        self.assertEqual(result['distinctBases'], 1)
        self.assertEqual(result['excludedRows'], {'nonconventional_prefix':1,'nonconventional_base':1,'reconstruction_mismatch':1,'missing_description':1})

    def test_missing_headers_empty_download_and_truncated_row_are_rejected(self):
        for rows, headers in [([], ['wrong']), ([], None), ([['AB1Z','1234']], None)]:
            with self.subTest(rows=rows, headers=headers), self.assertRaises(ValueError):
                self.extract_rows(rows, headers)

    def test_refresh_delta_and_historical_preservation_are_idempotent(self):
        old = self.extract_rows([['AB1Z','1234','A','AB1Z1234A','FIXTURE'],['AB1Z','2345','A','AB1Z2345A','HISTORICAL']])
        old['retrievedAt']='2026-09-17'
        new = self.extract_rows([['AB1Z','1234','A','AB1Z1234A','RENAMED'],['AB1Z','3456','A','AB1Z3456A','ADDED']])
        report = MODULE.refresh_report(old,new)
        self.assertEqual(report['addedBases'],['3456'])
        self.assertEqual(report['absentBases'],['2345'])
        self.assertEqual(report['changedBases'],['1234'])
        history = MODULE.preserve_absent(old,new,{'entries':[]})
        self.assertEqual(history['entries'][0]['retrievedAt'],'2026-09-17')
        self.assertEqual(MODULE.preserve_absent(new,new,history),history)
        self.assertEqual(MODULE.preserve_absent(new,old,history)['entries'][0]['base'],'3456')


if __name__ == '__main__':
    unittest.main()
