#!/usr/bin/env python3
"""Generate reviewed biosample decoder fixtures using the pinned Perl CLI."""
import argparse
import json
from pathlib import Path
import subprocess

from snapshot_reference import BASELINE, ROOT


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    args = parser.parse_args()
    reference = args.reference.resolve()
    head = subprocess.check_output(["git", "-C", str(reference), "rev-parse", "HEAD"], text=True).strip()
    if head != BASELINE:
        raise SystemExit("Reference HEAD does not match the pinned baseline.")
    if subprocess.check_output(["git", "-C", str(reference), "diff", "HEAD", "--", "lib", "bin", "share"]):
        raise SystemExit("Reference implementation or resources have uncommitted changes.")

    def cli(*arguments):
        return subprocess.check_output(
            ["perl", "-Ilib", "bin/clarid-tools", "code", "--entity", "biosample",
             "--codebook", "share/versions/0.04/clarid-codebook.yaml", *arguments],
            cwd=reference, text=True,
        ).strip()

    base = dict(project="TCGA-AML", species="Human", subject_id=1, tissue="Liver",
                sample_type="Tumor", assay="RNA_seq", condition="I25.110",
                timepoint="Challenge", duration="P1M")
    scenarios = [
        ("basic", {}),
        ("both_suffixes", {"batch": 1, "replicate": 5}),
        ("batch_only", {"batch": 0}),
        ("replicate_only", {"replicate": 99}),
        ("zero_subject", {"subject_id": 0}),
        ("base62_lowercase", {"subject_id": 61}),
        ("base62_carry", {"subject_id": 62}),
        ("human_boundary", {"subject_id": 99999}),
        ("multiple_conditions", {"condition": "I25.110,C22.0"}),
        ("duration_unavailable", {"duration": "P0N"}),
        ("zero_duration", {"duration": "P0D"}),
        ("long_vocabulary_stubs", {"project": "TARGET-AML", "species": "Mouse",
                                  "tissue": "PeripheralBlood", "assay": "HiC", "timepoint": "Collection"}),
        ("unknown_values", {"species": "Unknown", "tissue": "Unknown",
                            "sample_type": "Unknown", "assay": "Unknown", "timepoint": "Unknown"}),
        ("unavailable_values", {"tissue": "Not Available", "sample_type": "Not Available",
                                "assay": "Not Available", "timepoint": "Not Available"}),
    ]
    fixtures = []
    for fmt in ("human", "stub"):
        cases = [(name, changes, 5, 3) for name, changes in scenarios]
        cases.append(("custom_width", {"subject_id": 500000}, 7, 4))
        if fmt == "stub":
            cases.append(("stub_boundary", {"subject_id": 238327}, 5, 3))
        for name, changes, human_width, stub_width in cases:
            values = base | changes
            flags = [item for key, value in values.items() for item in (f"--{key}", str(value))]
            widths = ["--subject_id_pad_length", str(human_width), "--subject_id_base62_width", str(stub_width)]
            identifier = cli("--format", fmt, "--action", "encode", *widths, *flags)
            output = cli("--format", fmt, "--action", "decode", "--clar_id", identifier,
                         "--with_condition_name", *widths)
            expected = dict(line.split(": ", 1) if ": " in line else (line.rstrip(":"), "")
                            for line in output.splitlines())
            fixtures.append(dict(name=f"{fmt}_{name}", format=fmt, identifier=identifier,
                                 human_width=human_width, stub_width=stub_width, expected=expected))
    destination = ROOT / "core/src/test/resources/biosample-fixtures.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps({"commit": BASELINE, "fixtures": fixtures}, indent=2) + "\n")
    print(f"Generated {len(fixtures)} fixtures from the pinned CLI.")


if __name__ == "__main__":
    main()
