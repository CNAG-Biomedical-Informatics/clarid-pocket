#!/usr/bin/env python3
"""Export the authoritative bundled YAML to JSON for Foundation's JSONDecoder."""
import argparse
import hashlib
import json
from pathlib import Path

import yaml

IOS = Path(__file__).resolve().parents[1]
SOURCE = IOS.parent / "core/src/main/resources/clarid/0.04/clarid-codebook.yaml"
OUTPUT = IOS / "Resources/clarid-codebook.json"


class UniqueLoader(yaml.SafeLoader):
    pass


def unique_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in result:
            raise ValueError(f"Duplicate YAML key: {key}")
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Fail if the committed export is stale")
    args = parser.parse_args()
    raw = SOURCE.read_bytes()
    book = yaml.load(raw, Loader=UniqueLoader)
    if book["metadata"]["version"] != "0.04":
        raise ValueError("Only ClarID 0.04 is supported")
    book["source_sha256"] = hashlib.sha256(raw).hexdigest()
    text = json.dumps(book, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.check:
        if not OUTPUT.exists() or OUTPUT.read_text() != text:
            parser.exit(1, "Codebook export is stale; run ios/tools/prepare_reference.py\n")
        print("iOS codebook matches the authoritative YAML")
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(text)
        print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
