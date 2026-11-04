"""Collect final alpha.3 evidence into the tracked preview verification report.

Run after the full dual-loader build, all four native clients, preview generation,
visual snapshot and final release packaging. Missing/stale final evidence fails;
historical preview verification is never used as a fallback. Only the destination
verification.json is written. No Java, Gradle, network or publication is invoked.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath, PureWindowsPath
import re
import struct
import sys
import zipfile

# Imports must not create auxiliary cache files during evidence collection.
sys.dont_write_bytecode = True
import package_central_institute_release as release

ROOT = release.ROOT
GALLERY = ROOT / "previews/central-institute-runtime"
DESTINATION = GALLERY / "verification.json"


def checked_path(base: Path, relative: str) -> Path:
    """Resolve portable evidence paths without accepting drive names or traversal."""
    release.require(isinstance(relative, str) and relative and "\\" not in relative,
                    "Evidence paths must use nonempty relative POSIX paths")
    release.require(not PurePosixPath(relative).is_absolute() and not PureWindowsPath(relative).drive
                    and all(part not in {"", ".", ".."} for part in relative.split("/")),
                    "Evidence path is absolute or escapes its base")
    path = base / relative
    release.require(path.resolve().is_relative_to(base.resolve()) and path.is_file(),
                    f"Missing or escaped evidence file: {relative}")
    return path


def checked_file(base: Path, row: dict, key: str = "file") -> Path:
    path = checked_path(base, row[key])
    release.require(re.fullmatch(r"[0-9a-f]{64}", row["sha256"]) is not None
                    and release.digest(path) == row["sha256"], f"Evidence SHA256 changed: {row[key]}")
    if "bytes" in row:
        release.require(path.stat().st_size == row["bytes"], f"Evidence size changed: {row[key]}")
    return path


def entry(path: Path) -> dict:
    return release.file_entry(path, ROOT)


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as image:
        header = image.read(24)
    release.require(len(header) == 24 and header[:8] == b"\x89PNG\r\n\x1a\n", "Invalid receipt PNG")
    return struct.unpack(">II", header[16:24])


def gallery_evidence(loader: str, platform: dict, manifest: dict, snapshot_base: Path) -> dict:
    base = GALLERY / loader
    receipt_path = base / "receipt.json"
    receipt = release.read_json(receipt_path)
    spec = release.LOADERS[loader]
    release.require(receipt["loader"] == loader and receipt["layout_version"] == manifest["layout_version"],
                    f"{loader}: gallery uses an obsolete layout")
    release.require(receipt["manifest"]["path"] == release.MANIFEST.relative_to(ROOT).as_posix()
                    and receipt["manifest"]["sha256"] == release.digest(release.MANIFEST),
                    f"{loader}: gallery manifest differs from final resources")
    expected_directory = spec["project"] + "/build/central-institute-visual-check/screenshots"
    release.require(receipt["source_directory"] == expected_directory, f"{loader}: unexpected gallery source")
    originals = {Path(row["file"]).name: row for row in platform["screenshots"]}
    release.require(len(receipt["source_images"]) == 61 and set(originals) == release.shot_names(manifest),
                    f"{loader}: incomplete gallery originals")
    seen = set()
    for row in receipt["source_images"]:
        relative = PurePosixPath(row["path"])
        name = relative.name
        release.require(name not in seen and name in originals and row["path"] == expected_directory + "/" + name,
                        f"{loader}: duplicate or unexpected gallery original")
        seen.add(name)
        original = checked_file(snapshot_base, originals[name])
        release.require(row["sha256"] == originals[name]["sha256"] and row["bytes"] == original.stat().st_size
                        and (row["width"], row["height"]) == png_dimensions(original),
                        f"{loader}: gallery receipt does not match retained original {name}")
    release.require(seen == set(originals), f"{loader}: gallery omitted a room camera")
    retained = {Path(row["file"]).name: row for row in platform["previews"]}
    rows = [*receipt["generated_images"], *receipt["copied_originals"]]
    release.require(len(rows) == len(retained) == 10 and len({row["path"] for row in rows}) == 10,
                    f"{loader}: expected seven review sheets and three copied originals")
    for row in rows:
        release.require(row["path"] in retained, f"{loader}: unexpected gallery PNG")
        current = checked_file(base, row, "path")
        saved = checked_file(snapshot_base, retained[row["path"]])
        release.require(release.digest(current) == release.digest(saved)
                        and (row["width"], row["height"]) == png_dimensions(current),
                        f"{loader}: gallery changed after visual snapshot")
    return {"receipt": entry(receipt_path), "layout_version": receipt["layout_version"],
            "source_images": 61, "preview_images": 10, "image_operations": receipt["image_operations"]}


def collect() -> dict:
    # The packager is the authority for archive/JAR validation. Its receipt binds
    # verification.json and every staged artifact to the bytes checked below.
    report_path = release.OUTPUT / "verification.json"
    report = release.read_json(report_path)
    package_path = release.OUTPUT / "package-receipt.json"
    package = release.read_json(package_path)
    for record in (report, package):
        release.require(record["stage"] == "ready_for_release" and record["version"] == release.VERSION
                        and record["publication_performed_by_packaging_tool"] is False,
                        "Final alpha.3 release packaging has not completed")
    release.require(report["status"] == "PASS" and report["native_gametests_requested"] is True
                    and report["visual_layout_matches_final_jars"] is True, "Incomplete release verification")
    uploads = package["upload_files"]
    release.require(len(uploads) == package["upload_file_count"] == 12
                    and len({row["file"] for row in uploads}) == 12, "Incomplete final asset receipt")
    for row in uploads:
        checked_file(release.OUTPUT, row)
    verification_rows = [row for row in uploads if row.get("kind") == "verification"]
    release.require(len(verification_rows) == 1 and verification_rows[0]["file"] == report_path.name,
                    "Package receipt does not bind the final verification report")
    manifest = release.read_json(release.MANIFEST)
    release.require(manifest["layout_version"] == "central-institute-v2"
                    and len(manifest["tiles"]) == 56 and len(manifest["storage_containers"]) > 1000,
                    "Final v2 layout/storage inventory differs")
    release.require(report["manifest_sha256"] == release.digest(release.MANIFEST), "Packaged layout is stale")
    # These are shared, read-only validators; deliberately do not call stage_jars
    # or package_release, which would copy/rebuild other release files.
    builds, tests, parity = release.full_build_evidence(manifest)
    static = release.verify_structure(manifest, ROOT / "ender_infection_colored/src/main/resources")
    release.require(static["storage_containers"] == len(manifest["storage_containers"]),
                    "Static storage count differs from the current manifest")
    snapshot = release.snapshot_report()
    release.require(len(snapshot["platforms"]) == 2
                    and sum(len(row["screenshots"]) + len(row["storage_images"]) for row in snapshot["platforms"]) == 150,
                    "Retained evidence must contain exactly two loaders and 150 native PNGs")
    total = sum(row["required_passed"] for row in tests)
    release.require(report["native_tests"] == tests and report["native_required_tests_passed"] == total
                    and package["native_required_tests_passed"] == total, "Final native test evidence changed")
    release.require(report["static"] == static and report["parity"] == parity, "Static/parity evidence changed")
    release.require(report["visual_inputs_sha256"] == snapshot["visual_inputs"]["sha256"], "Capture inputs changed")
    for record in (report, snapshot):
        release.require(record["screenshots"] == 150, "Final evidence requires all 150 approved PNGs")
    release.require(report["institute_screenshots"] == 122 and report["storage_screenshots"] == 28
                    and snapshot["main_screenshots"] == 122 and snapshot["storage_screenshots"] == 28,
                    "Final camera counts differ")
    release.require(package["screenshots"] == 150, "Package camera count differs")
    artifact_map = {row["file"]: row for row in report["artifacts"]}
    release.require(len(artifact_map) == len(report["artifacts"]) == 8, "Incomplete final artifact set")
    upload_map = {row["file"]: row for row in uploads}
    for name, row in artifact_map.items():
        release.require(name in upload_map and row == upload_map[name], "Release/package artifact metadata differs")
    for core in builds["jars"]:
        original = checked_file(ROOT, core)
        rows = [row for row in report["artifacts"] if row["kind"] == "core" and row["file"] == original.name]
        release.require(len(rows) == 1 and rows[0]["sha256"] == core["sha256"]
                        and rows[0]["bytes"] == core["bytes"], "Staged core differs from the full native build")
    platforms = []
    snapshot_base = release.OUTPUT / "visual-inputs"
    for platform in snapshot["platforms"]:
        loader = platform["loader"]
        visual_log = release.EVIDENCE / release.LOADERS[loader]["visual_log"]
        visual = release.verify_visual(visual_log, snapshot_base / loader / "screenshots")
        visual = {key: visual[key] for key in ("status", "natural", "rooms", "pieces", "full_chunks", "screenshots")}
        storage_log = release.storage_log(loader)
        storage = release.verify_storage_capture(loader, storage_log, snapshot_base / loader / "storage")
        release.require(visual == platform["native_visual"] and storage == platform["storage_visual"]
                        and release.digest(visual_log) == platform["visual_log_sha256"]
                        and release.digest(storage_log) == platform["storage_log_sha256"], "Native client evidence changed")
        platforms.append({"loader": loader, "minecraft": platform["minecraft"],
                          "capture_core_version": platform["capture_core_version"], "native_visual": visual,
                          "storage_visual": storage, "visual_log": entry(visual_log), "storage_log": entry(storage_log),
                          "gallery": gallery_evidence(loader, platform, manifest, snapshot_base)})
    capture_versions = {row["loader"]: row["capture_core_version"] for row in platforms}
    release.require(report["visual_capture_versions"] == capture_versions, "Client version provenance changed")
    public_tests = [{**row, "log": (release.DUAL / row["evidence_log_name"]).relative_to(ROOT).as_posix()} for row in tests]
    artifacts = [{**row, "file": (release.OUTPUT / row["file"]).relative_to(ROOT).as_posix()} for row in report["artifacts"]]
    result = {"format_version": 2, "status": "PASS", "stage": "ready_for_release", "release_status": "ready_for_release",
              "version": release.VERSION, "layout_version": manifest["layout_version"],
              "collected_at_utc": datetime.now(timezone.utc).isoformat(), "publication_performed": False,
              "manifest": entry(release.MANIFEST), "manifest_sha256": release.digest(release.MANIFEST),
              "static": static, "storage_furniture_variants": len(release.STORAGE_KEYS),
              "native_gametests_requested": True, "native_required_tests_passed": total, "native_tests": public_tests,
              "screenshots": 150, "institute_screenshots": 122, "storage_screenshots": 28,
              "visual_inputs_sha256": snapshot["visual_inputs"]["sha256"], "visual": platforms,
              "visual_layout_matches_final_jars": True, "artifacts": artifacts,
              "jars": [row for row in artifacts if row["kind"] == "core"],
              "provenance": {"release_verification": entry(report_path), "package_receipt": entry(package_path),
                             "full_build_report": entry(release.DUAL / "verified-builds.json"),
                             "visual_snapshot": entry(snapshot_base / "snapshot.json")},
              "path_base": "workspace root", "privacy": "Relative evidence paths and hashes only; no player worlds, raw logs or credentials."}
    # Guard the selected payload too; shared helper reports must never introduce
    # a machine-local path into the tracked public receipt.
    def portable(value):
        if isinstance(value, dict):
            for item in value.values():
                portable(item)
        elif isinstance(value, list):
            for item in value:
                portable(item)
        elif isinstance(value, str):
            release.require(not re.search(r"(?:[A-Za-z]:[\\/]|\\\\|/(?:home|Users|mnt|tmp)/)", value),
                            "Public evidence contains a machine-local absolute path")
    portable(result)
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Validate all final evidence without replacing the gallery report")
    args = parser.parse_args()
    report = collect()
    if not args.check:
        release.write_json(DESTINATION, report)
    print("CENTRAL_EVIDENCE_PASS " + release.json_bytes({"version": report["version"], "stage": report["stage"],
          "native_required_tests_passed": report["native_required_tests_passed"], "screenshots": report["screenshots"],
          "written": not args.check, "report": DESTINATION.relative_to(ROOT).as_posix()}).decode("utf-8").strip())


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, AssertionError, zipfile.BadZipFile) as error:
        message = str(error).replace(str(ROOT).replace("\\", "\\\\"), "<workspace>")
        message = message.replace(str(ROOT), "<workspace>").replace(ROOT.as_posix(), "<workspace>")
        print("CENTRAL_EVIDENCE_FAIL " + message, file=sys.stderr)
        raise SystemExit(1)
