#!/usr/bin/env python3
"""Refresh bundled assets from an explicitly pinned, read-only ClarID-Tools checkout."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

BASELINE = "3789effd7481cef5928a251095f2b7f792b9fa3f"
ROOT = Path(__file__).resolve().parents[1]
ASSETS = {
    "share/versions/0.04/clarid-codebook.yaml": "clarid-codebook.yaml",
    "share/versions/0.04/clarid-codebook-schema.json": "clarid-codebook-schema.json",
    "share/icd10.json": "icd10.json",
    "share/icd10_order.json": "icd10_order.json",
    "share/README_ICD10.md": "README_ICD10.md",
    "LICENSE": "LICENSE-ClarID-Tools.txt",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", type=Path)
    args = parser.parse_args()
    destination = ROOT / "core/src/main/resources/clarid/0.04"
    destination.mkdir(parents=True, exist_ok=True)
    files = []
    for source, name in ASSETS.items():
        data = subprocess.check_output(
            ["git", "-C", str(args.reference), "show", f"{BASELINE}:{source}"]
        )
        (destination / name).write_bytes(data)
        files.append({"source": source, "bundled": name,
                      "sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)})
    provenance = {
        "repository": "https://github.com/CNAG-Biomedical-Informatics/clarid-tools",
        "commit": BASELINE,
        "release": "0.04",
        "files": files,
    }
    (destination / "provenance.json").write_text(
        json.dumps(provenance, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Copied {len(files)} pinned reference assets.")


if __name__ == "__main__":
    main()
