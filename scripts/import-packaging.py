"""Import Ford's explicit public packaging CSV; retain removed bases as history.

Usage: python scripts/import-packaging.py /path/to/packagingdata.csv --date YYYY-MM-DD
The raw download is never copied into the repository. Refreshing does not assert
fitment, interchangeability, availability, or that absent parts are discontinued.
"""
import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from datetime import date, datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COLUMNS = ['Prefix', 'Base', 'Suffix', 'Service Part Number', 'Part Description']


def extract(path, retrieved_at):
    if date.fromisoformat(retrieved_at).isoformat() != retrieved_at:
        raise ValueError('Use an ISO date in YYYY-MM-DD format.')
    groups, excluded, total = {}, Counter(), 0
    with path.open(encoding='utf-8-sig', newline='') as f:
        rows = csv.DictReader(f)
        if not rows.fieldnames or not set(COLUMNS).issubset(rows.fieldnames):
            raise ValueError('CSV is missing required Ford packaging columns; no files changed.')
        for row in rows:
            total += 1
            if any(row.get(k) is None for k in COLUMNS):
                raise ValueError(f'Incomplete CSV row {total + 1}; no files changed.')
            prefix, base, suffix, part, name = (row[k].strip() for k in COLUMNS)
            if not re.fullmatch(r'[A-Z0-9]{4}', prefix):
                excluded['nonconventional_prefix'] += 1
                continue
            if not re.fullmatch(r'[0-9][A-Z0-9]{3,7}', base):
                excluded['nonconventional_base'] += 1
                continue
            if not name:
                excluded['missing_description'] += 1
                continue
            if re.sub(r'[- ]', '', part) != prefix + base + suffix:
                excluded['reconstruction_mismatch'] += 1
                continue
            group = groups.setdefault(base, {'base': base, 'descriptions': Counter(), 'examples': [], 'serviceCount': 0})
            group['descriptions'][name] += 1
            group['serviceCount'] += 1
            if len(group['examples']) < 3 and part not in group['examples']:
                group['examples'].append(part)
    if not groups:
        raise ValueError('CSV contains no accepted base records; no files changed.')
    entries = []
    for base, group in sorted(groups.items()):
        group['descriptions'] = [{'name': k, 'count': v} for k, v in group['descriptions'].most_common()]
        entries.append(group)
    return {
        'source': 'https://upccrossreference.cdis3.com/downloadfiles/packagingdata.csv',
        'landingPage': 'https://upccrossreference.cdis3.com/downloadfiles/packagingdata.csv',
        'sourceName': 'Ford / Motorcraft packaging cross-reference',
        'retrievedAt': retrieved_at, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
        'inputRows': total, 'excludedRows': dict(excluded), 'distinctBases': len(entries), 'entries': entries,
    }


def refresh_report(previous, current):
    old = {x['base']: x for x in previous['entries']}
    new = {x['base']: x for x in current['entries']}
    changed = sorted(b for b in old.keys() & new.keys() if old[b] != new[b])
    return {
        'previousRetrievedAt': previous['retrievedAt'], 'retrievedAt': current['retrievedAt'],
        'previousSha256': previous['sha256'], 'sha256': current['sha256'],
        'previousInputRows': previous['inputRows'], 'inputRows': current['inputRows'],
        'previousDistinctBases': len(old), 'distinctBases': len(new),
        'addedBases': sorted(new.keys() - old.keys()), 'absentBases': sorted(old.keys() - new.keys()),
        'changedBases': changed,
        'descriptionCountsChangedBases': sorted(b for b in changed if old[b]['descriptions'] != new[b]['descriptions']),
        'descriptionNamesChangedBases': sorted(b for b in changed if
            {x['name'] for x in old[b]['descriptions']} != {x['name'] for x in new[b]['descriptions']}),
        'coverage': 'Snapshot comparison, not a supersession, availability, discontinuation, or VIN-fitment decision.',
    }


def preserve_absent(previous, current, history):
    current_bases = {x['base'] for x in current['entries']}
    entries = {x['base']: x for x in history['entries'] if x['base'] not in current_bases}
    for entry in previous['entries']:
        if entry['base'] not in current_bases:
            entries[entry['base']] = {**entry, 'retrievedAt': previous['retrievedAt'],
                                     'sha256': previous['sha256']}
    return {'source': current['source'], 'landingPage': current['landingPage'],
            'coverage': 'Previously observed bases absent from the latest download. Historical evidence only; absence does not imply discontinuation.',
            'entries': [entries[b] for b in sorted(entries)]}


def write_json(path, value):
    # Replacement happens only after the entire download is validated.
    temp = path.with_suffix(path.suffix + '.tmp')
    temp.write_text(json.dumps(value, separators=(',', ':')) + '\n', encoding='utf-8')
    temp.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('csv')
    parser.add_argument('--date', default=datetime.now(timezone.utc).date().isoformat())
    args = parser.parse_args()
    current = extract(Path(args.csv), args.date)
    target = ROOT / 'data/packaging-reference.json'
    previous = json.loads(target.read_text(encoding='utf-8')) if target.exists() else None
    if previous and current['inputRows'] < previous['inputRows'] * 0.9:
        raise ValueError('Download has over 10% fewer rows; review source completeness before replacing the snapshot.')
    if previous and previous['sha256'] != current['sha256']:
        history_path = ROOT / 'data/packaging-history.json'
        history = json.loads(history_path.read_text(encoding='utf-8')) if history_path.exists() else {'entries': []}
        write_json(history_path, preserve_absent(previous, current, history))
        write_json(ROOT / 'data/packaging-refresh.json', refresh_report(previous, current))
    write_json(target, current)
    print(json.dumps({k: v for k, v in current.items() if k != 'entries'}, indent=2))


if __name__ == '__main__':
    main()
