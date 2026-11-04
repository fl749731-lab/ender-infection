"""Check shared inputs and feature inventory before building both loaders."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

from sync_neoforge_resources import SOURCE, DEST, target, transform

ROOT = Path(__file__).resolve().parents[1]
FORGE = ROOT / "ender_infection_colored"
NEO = ROOT / "ender_infection_neoforge"


def check() -> dict:
    issues = []
    version = re.search(r"(?m)^version\s*=\s*['\"]([^'\"]+)", (FORGE / "build.gradle").read_text("utf-8"))
    if not version:
        raise ValueError("Forge build.gradle must declare the canonical release version")
    neo_build = (NEO / "build.gradle").read_text("utf-8")
    if "ender_infection_colored/build.gradle" not in neo_build:
        issues.append("NeoForge must derive its version from Forge's canonical build.gradle")
    common = ROOT / "common/src/main/java"
    shared = sorted(p.relative_to(common).as_posix() for p in common.rglob("*.java"))
    if not shared:
        issues.append("No shared API source files found")
    inventories = {}
    registers = {}
    for name, project in (("forge", FORGE), ("neoforge", NEO)):
        base = project / "src/main/java"
        # Platform bridges are allowed to have different names; all existing gameplay
        # and test classes must remain present in both ports.
        files = {p.relative_to(base).as_posix(): p for p in base.rglob("*.java")}
        inventories[name] = set(files)
        registers[name] = set()
        for rel, path in files.items():
            if "/institute/" in rel:
                continue
            registers[name].update(re.findall(r'\.register(?:Block|Item)?\(\s*"([a-z0-9_/.-]+)"', path.read_text("utf-8")))
        if "../common/src/main/java" not in (project / "build.gradle").read_text("utf-8"):
            issues.append(f"{name} does not compile the common API source")
    missing_classes = sorted(x for x in inventories["forge"] - inventories["neoforge"] if "/institute/" not in x)
    if missing_classes:
        issues.append("NeoForge is missing Forge gameplay/test classes: " + ", ".join(missing_classes))
    extra_classes = sorted(x for x in inventories["neoforge"] - inventories["forge"] if "/institute/" not in x)
    if extra_classes:
        issues.append("Forge is missing NeoForge gameplay/test classes: " + ", ".join(extra_classes))
    missing_ids = sorted(registers["forge"] - registers["neoforge"])
    if missing_ids:
        issues.append("NeoForge is missing literal registrations: " + ", ".join(missing_ids))
    extra_ids = sorted(registers["neoforge"] - registers["forge"])
    if extra_ids:
        issues.append("Forge is missing literal registrations: " + ", ".join(extra_ids))
    resource_count = 0
    resource_errors = []
    for path in sorted(SOURCE.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(SOURCE)
        if rel.as_posix() in ("META-INF/mods.toml", "pack.mcmeta"):
            continue
        out = DEST / target(rel)
        if path.suffix == ".json":
            expected = transform(json.loads(path.read_text("utf-8")), len(rel.parts) > 2 and rel.parts[2] == "recipes")
            try:
                matches = expected == json.loads(out.read_text("utf-8"))
            except (FileNotFoundError, json.JSONDecodeError):
                matches = False
        else:
            matches = out.is_file() and hashlib.sha256(path.read_bytes()).digest() == hashlib.sha256(out.read_bytes()).digest()
        if not matches:
            resource_errors.append(rel.as_posix())
        resource_count += 1
    if resource_errors:
        issues.append(f"{len(resource_errors)} generated NeoForge resources are missing/stale; run syncSharedResources: " + ", ".join(resource_errors[:8]))
    return {
        "status": "PASS" if not issues else "FAIL",
        "version": version.group(1), "shared_api_sources": len(shared),
        "forge_java_sources": len(inventories["forge"]), "neoforge_java_sources": len(inventories["neoforge"]),
        "forge_literal_registration_ids": len(registers["forge"]), "shared_resource_files": resource_count,
        "issues": issues,
        "scope": "Source/registration inventory and shared-resource consistency; runtime behavior is covered by both native GameTest runs.",
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = check()
    encoded = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(encoded, "utf-8")
    print(encoded, end="")
    raise SystemExit(0 if report["status"] == "PASS" else 1)
