"""Reject zero-test success and incomplete native GameTest runs."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import re


def verify(path: Path, minimum: int) -> dict:
    content = path.read_text("utf-8", errors="replace")
    complete = re.findall(r"=+\s+(\d+) GAME TESTS COMPLETE", content)
    passed = re.findall(r"All (\d+) required tests passed", content)
    failures = re.findall(r"(\d+) required tests failed", content)
    if not complete or not passed or any(int(x) for x in failures):
        raise ValueError(f"No complete successful native GameTest run in {path}")
    total, required = int(complete[-1]), int(passed[-1])
    if required < minimum or total < required:
        raise ValueError(f"Expected at least {minimum} required tests, saw {required}/{total} in {path}")
    return {"status": "PASS", "log": str(path), "total": total, "required_passed": required}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("log", type=Path)
    parser.add_argument("--minimum", type=int, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.log, args.minimum), indent=2))
