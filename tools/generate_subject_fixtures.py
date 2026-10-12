#!/usr/bin/env python3
"""Generate subject fixtures read-only from the pinned ClarID-Tools CLI."""
import argparse
import json
from pathlib import Path
import subprocess
from snapshot_reference import BASELINE, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('reference', type=Path)
    ref = parser.parse_args().reference.resolve()
    if subprocess.check_output(['git', '-C', str(ref), 'rev-parse', 'HEAD'], text=True).strip() != BASELINE:
        raise SystemExit('Reference HEAD does not match baseline.')
    if subprocess.check_output(['git', '-C', str(ref), 'diff', 'HEAD', '--', 'lib', 'bin', 'share']):
        raise SystemExit('Reference implementation or resources have uncommitted changes.')

    def cli(*args):
        return subprocess.check_output(['perl', '-Ilib', 'bin/clarid-tools', 'code', '--entity', 'subject',
            '--codebook', 'share/versions/0.04/clarid-codebook.yaml', *args], cwd=ref, text=True).strip()

    base = dict(study='TCGA-AML', subject_id=1, type='Case', condition='I25.110', sex='Male', age_group='Age40to49')
    cases = [('basic', {}), ('control', dict(type='Control', sex='Female', age_group='Age0to9')),
        ('multiple_conditions', dict(condition='I25.110,C22.0')),
        ('free_study', dict(study='CUSTOM_2026')), ('known_study', dict(study='TARGET-AML')),
        ('zero', dict(subject_id=0)), ('base62_lower', dict(subject_id=61)), ('base62_carry', dict(subject_id=62)),
        ('not_reported', dict(sex='Not reported')), ('unspecified', dict(sex='Unspecified')),
        ('unknown', dict(type='Unknown', sex='Unknown', age_group='Unknown')),
        ('unavailable', dict(type='Not Available', sex='Not Available', age_group='Not Available'))]
    cases += [('age_' + str(n), dict(age_group='Age' + str(n) + 'to' + str(n + 9))) for n in range(10, 100, 10)]
    fixtures = []
    for fmt in ['human', 'stub']:
        selected = [(name, delta, 5, 3) for name, delta in cases]
        selected += [('custom_width', dict(subject_id=500000), 7, 4)]
        if fmt == 'stub': selected += [('boundary', dict(subject_id=238327), 5, 3)]
        for name, delta, hw, sw in selected:
            flags = [arg for k, v in (base | delta).items() for arg in ('--' + k, str(v))]
            config = ['--format', fmt, '--subject_id_pad_length', str(hw), '--subject_id_base62_width', str(sw)]
            text = cli(*config, '--action', 'encode', *flags)
            decoded = cli(*config, '--action', 'decode', '--clar_id', text, '--with_condition_name')
            expected = dict(line.split(': ', 1) if ': ' in line else (line.rstrip(':'), '') for line in decoded.splitlines())
            fixtures.append(dict(name=fmt + '_' + name, format=fmt, identifier=text, human_width=hw,
                stub_width=sw, expected=expected))
    (ROOT / 'core/src/test/resources/subject-fixtures.json').write_text(
        json.dumps(dict(commit=BASELINE, fixtures=fixtures), indent=2) + '\n')
    print('Generated', len(fixtures), 'subject fixtures.')


if __name__ == '__main__': main()
