#!/usr/bin/env python3
"""Verify public release inputs and privacy without modifying the game."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "PUBLIC-TREE-MANIFEST.json"
INPUTS = ("VERSION", "src", "fs", "patch", "release-base", "tools/release/classpath.txt")
TEXT_SUFFIXES = {".java", ".json", ".json5", ".md", ".txt", ".toml", ".properties", ".csv", ".py", ".ps1", ".sh", ".mcmeta"}
PRIVATE_NAME = re.compile(rb"austin[\s._/\\-]*shedd|austinshedd", re.I)


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def files() -> list[Path]:
    found: list[Path] = []
    for name in INPUTS:
        path = ROOT / name
        if path.is_file():
            found.append(path)
        elif path.is_dir():
            found.extend(item for item in path.rglob("*") if item.is_file())
        else:
            raise SystemExit(f"Missing release input: {name}")
    return sorted(found, key=lambda item: item.relative_to(ROOT).as_posix())


def snapshot() -> dict:
    tracked = files()
    for path in tracked:
        relative = path.relative_to(ROOT).as_posix()
        if PRIVATE_NAME.search(relative.encode("utf-8")):
            raise SystemExit(f"Private author identifier in path: {relative}")
        if path.suffix.lower() in TEXT_SUFFIXES and PRIVATE_NAME.search(path.read_bytes()):
            raise SystemExit(f"Private author identifier in text: {relative}")

    if (ROOT / "VERSION").read_text(encoding="utf-8").strip() != "1.5.0":
        raise SystemExit("VERSION must remain 1.5.0")
    frontier = (ROOT / "recovered-source/com/formaworks/frontierhunts/FrontierHunts.java").read_text(encoding="utf-8")
    structures = (ROOT / "fs/java/com/formaworks/frontierstructures/FrontierStructures.java").read_text(encoding="utf-8")
    if '"frontierhunts"' not in frontier or '"frontierstructures"' not in structures:
        raise SystemExit("Stable mod identifiers are missing from their entry points")

    audio = [path for path in tracked if path.suffix.lower() in {".ogg", ".wav", ".mp3", ".flac"}]
    return {
        "format": 1,
        "version": "1.5.0",
        "release_inputs": {path.relative_to(ROOT).as_posix(): digest(path) for path in tracked},
        "audio_files": {path.relative_to(ROOT).as_posix(): digest(path) for path in audio},
        "counts": {
            "release_input_files": len(tracked),
            "authored_frontier_java": len(list((ROOT / "src").rglob("*.java"))),
            "frontier_structures_java": len(list((ROOT / "fs/java").rglob("*.java"))),
            "recovered_review_java": len(list((ROOT / "recovered-source").rglob("*.java"))),
            "audio_files": len(audio),
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="write the reviewed baseline")
    args = parser.parse_args()
    current = snapshot()
    if args.write:
        MANIFEST.write_text(json.dumps(current, indent=2) + "\n", encoding="utf-8")
        print(f"Wrote {MANIFEST.name}: {current['counts']}")
        return 0
    if not MANIFEST.is_file():
        raise SystemExit("PUBLIC-TREE-MANIFEST.json is missing")
    expected = json.loads(MANIFEST.read_text(encoding="utf-8"))
    if current != expected:
        print("Public release inputs differ from the reviewed manifest.", file=sys.stderr)
        return 1
    print(f"Public tree verified: {current['counts']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
