"""Prepare local alpha.3 release assets from verified dual-loader artifacts.

This tool never runs Java, contacts a network, publishes, or opens player worlds.
Use --snapshot-visuals before Gradle clean; final packaging is gated on the full
release build/test report and binds the final JAR hashes to the retained images.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import html
import json
from pathlib import Path
import re
import shutil
import struct
import sys
import time
import tomllib
import zipfile

from verify_central_institute_visual import verify as verify_visual
from verify_central_institute import verify as verify_structure
from verify_gametest_log import verify as verify_gametest
from verify_lab_storage_visual import verify as verify_storage_native

ROOT = Path(__file__).resolve().parents[1]
VERSION = "0.12.0-alpha.3"
OUTPUT = ROOT / "build" / f"release-{VERSION}"
MANIFEST = ROOT / "ender_infection_colored/src/main/resources/data/ender_infection/central_institute.json"
DUAL = ROOT / "build/dual-version-validation"
EVIDENCE = ROOT / "build/central-institute"
LOADERS = {
    "forge": {"project": "ender_infection_colored", "minecraft": "1.20.1", "java": 61, "visual_log": "forge-visual.log", "data_dir": "structures", "metadata": "META-INF/mods.toml"},
    "neoforge": {"project": "ender_infection_neoforge", "minecraft": "1.21.1", "java": 65, "visual_log": "neo-visual.log", "data_dir": "structure", "metadata": "META-INF/neoforge.mods.toml"},
}
STORAGE_KEYS = ("lab_cabinet", "lab_cabinet_eroded", "lab_workbench", "lab_workbench_eroded",
                "lab_freezer", "lab_storage_crate", "lab_shelf", "lab_glass_cabinet", "lab_cryo_cabinet",
                "lab_server_rack", "lab_control_console", "lab_rolling_cart", "lab_sink")


def verify_storage_capture(loader, log, screenshots):
    checked = verify_storage_native(loader, log, screenshots)
    require(checked["ok"] is True and checked["log_sha256"] == digest(log), "Strict storage evidence did not match its actual native log")
    require({item["name"] for item in checked["captures"]} == {"menu_" + key for key in STORAGE_KEYS} | {"model_open_lab_cabinet"}, "Strict storage evidence omitted a planned device")
    # Public reports use package-relative image manifests, never the strict helper's
    # author-machine absolute capture paths.
    return {key: checked[key] for key in ("status", "menus", "screenshots", "storage_slots", "player_slots", "real_open_model")}


def storage_log(loader):
    short = "neo" if loader == "neoforge" else "forge"
    candidates = {EVIDENCE / name for name in (f"{loader}-storage-visual.log", f"storage-{loader}-visual.log", f"{short}-storage-visual.log", f"storage-{short}-visual.log")}
    candidates = [path for path in candidates if path.is_file()]
    require(candidates, f"No {loader} storage client log")
    return max(candidates, key=lambda path: path.stat().st_mtime_ns)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + "\n").encode("utf-8")


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_bytes(json_bytes(value))
    temporary.replace(path)


def source_version():
    text = (ROOT / "ender_infection_colored/build.gradle").read_text(encoding="utf-8")
    return re.search(r"(?m)^version\s*=\s*['\"]([^'\"]+)['\"]", text)[1]


def file_entry(path, base, kind=None):
    result = {"file": path.relative_to(base).as_posix(), "bytes": path.stat().st_size, "sha256": digest(path)}
    if kind:
        result["kind"] = kind
    return result


def shot_names(manifest):
    names = {f"room_{room['id']}.png" for room in manifest["rooms"]}
    names.update(f"floor_{floor}.png" for floor in ("G1", "G2", "G3", "B1", "B2", "B3"))
    names.update(f"{name}.png" for name in ("00_exterior_front", "01_exterior_rear", "02_atrium", "stairs_1", "stairs_10", "night_exterior", "night_reactor"))
    require(len(names) == 61, "Expected exactly 61 final camera names")
    return names


def visual_inputs():
    """Fingerprint behavior/art inputs, allowing only the release-label bump."""
    paths = set()
    for spec in LOADERS.values():
        project = ROOT / spec["project"]
        for path in (project / "src/main").rglob("*"):
            # These opt-in camera drivers are test fixtures. Their lighting or
            # screenshot timing can be repaired while production assets stay frozen.
            camera_fixture = path.name in {"CentralInstituteVisualCheck.java", "LabStorageVisualCheck.java"}
            if path.is_file() and "/java/dev/hui/enderinfection/test/" not in path.as_posix() and not camera_fixture:
                paths.add(path)
        paths.add(project / "build.gradle")
    paths.update(path for path in (ROOT / "common/src/main/java").rglob("*.java"))
    paths.add(ROOT / "tools/sync_neoforge_resources.py")
    rows = []
    for path in sorted(paths):
        data = path.read_bytes()
        if path.name == "build.gradle":
            text = data.decode("utf-8").replace("\r\n", "\n")
            data = re.sub(r"(?m)^version\s*=.*$", "version = <release label>", text).encode("utf-8")
        rows.append({"file": path.relative_to(ROOT).as_posix(), "sha256": hashlib.sha256(data).hexdigest()})
    return {"sha256": hashlib.sha256(json_bytes(rows)).hexdigest(), "files": rows}


def prepare_visual_capture():
    fingerprint = visual_inputs()
    marker = {"stage": "visual_capture_prepared", "prepared_at_utc": datetime.now(timezone.utc).isoformat(),
              "prepared_at_ns": time.time_ns(), "core_version": source_version(), "visual_inputs": fingerprint}
    write_json(OUTPUT / "visual-capture-inputs.json", marker)
    print(json.dumps({"stage": marker["stage"], "core_version": marker["core_version"], "visual_inputs_sha256": fingerprint["sha256"],
                      "next": "Run both institute and storage native clients plus preview generation; then --snapshot-visuals. All 150 PNGs and four logs must be newer than this marker."}, ensure_ascii=False))


def mark_visuals_stale(reason):
    path = OUTPUT / "visual-inputs/snapshot.json"
    report = read_json(path)
    report["stage"] = "superseded_visual_snapshot"
    report["requires_recapture"] = True
    report["superseded_reason"] = reason
    write_json(path, report)
    print(json.dumps({"stage": report["stage"], "screenshots_retained": report["screenshots"], "reason": reason}, ensure_ascii=False))


def snapshot_visuals():
    """Retain only approved PNGs/public metadata before per-project build/ is cleaned."""
    manifest = read_json(MANIFEST)
    manifest_hash = digest(MANIFEST)
    expected = shot_names(manifest)
    marker = read_json(OUTPUT / "visual-capture-inputs.json")
    fingerprint = visual_inputs()
    require(marker["stage"] == "visual_capture_prepared" and marker["visual_inputs"]["sha256"] == fingerprint["sha256"], "Production code/models/resources changed after capture preparation; prepare and recapture both native clients")
    base = OUTPUT / "visual-inputs"
    reports = []
    for loader, spec in LOADERS.items():
        source = ROOT / spec["project"] / "build/central-institute-visual-check/screenshots"
        log = EVIDENCE / spec["visual_log"]
        require(log.stat().st_mtime_ns >= marker["prepared_at_ns"], f"{loader}: native log predates capture preparation; recapture")
        checked = verify_visual(log, source)
        preview_base = ROOT / "previews/central-institute-runtime" / loader
        receipt = read_json(preview_base / "receipt.json")
        require(receipt["manifest"]["sha256"] == manifest_hash, f"{loader}: previews use a different structure manifest")
        inputs = {Path(item["path"]).name: item for item in receipt["source_images"]}
        require(set(inputs) == expected, f"{loader}: preview receipt omits final screenshots")
        shots = []
        for name in sorted(expected):
            original = source / name
            require(original.stat().st_mtime_ns >= marker["prepared_at_ns"], f"{loader}: stale screenshot predates capture preparation: {name}")
            require(digest(original) == inputs[name]["sha256"], f"{loader}: screenshot changed after preview generation: {name}")
            target = base / loader / "screenshots" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, target)
            shots.append(file_entry(target, base))
        previews = []
        for item in [*receipt["generated_images"], *receipt["copied_originals"]]:
            relative = Path(item["path"])
            require(not relative.is_absolute() and len(relative.parts) == 1 and relative.suffix.lower() == ".png", "Only named preview PNGs may be retained")
            original = preview_base / relative
            require(digest(original) == item["sha256"], f"{loader}: preview digest mismatch: {relative}")
            target = base / loader / "previews" / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, target)
            previews.append(file_entry(target, base))
        storage_source = ROOT / spec["project"] / "build/storage-visual-check/screenshots"
        storage_evidence = storage_log(loader)
        require(storage_evidence.stat().st_mtime_ns >= marker["prepared_at_ns"], f"{loader}: storage native log predates capture preparation")
        storage_check = verify_storage_capture(loader, storage_evidence, storage_source)
        storage_shots = []
        for name in sorted({"menu_" + key + ".png" for key in STORAGE_KEYS} | {"model_open_lab_cabinet.png"}):
            original = storage_source / name
            require(original.stat().st_mtime_ns >= marker["prepared_at_ns"], f"{loader}: storage UI image predates capture preparation: {name}")
            target = base / loader / "storage" / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(original, target)
            storage_shots.append(file_entry(target, base))
        fixture = ROOT / spec["project"] / "src/main/java/dev/hui/enderinfection/client/CentralInstituteVisualCheck.java"
        reports.append({"loader": loader, "minecraft": spec["minecraft"], "capture_core_version": marker["core_version"],
                        "native_visual": {key: checked[key] for key in ("status", "natural", "rooms", "pieces", "full_chunks", "screenshots")},
                        "visual_log_sha256": digest(log), "fixture_source_sha256": digest(fixture),
                        "screenshots": shots, "storage_images": storage_shots, "previews": previews,
                        "storage_visual": storage_check, "storage_log_sha256": digest(storage_evidence),
                        "image_operations": receipt["image_operations"]})
    base.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(MANIFEST, base / "central_institute.json")
    report = {"format_version": 1, "stage": "verified_visual_snapshot", "target_version": VERSION,
              "created_at_utc": datetime.now(timezone.utc).isoformat(), "manifest_sha256": manifest_hash,
              "visual_inputs": fingerprint, "capture_prepared_at_ns": marker["prepared_at_ns"],
              "layout_version": manifest["layout_version"], "main_screenshots": 122, "storage_screenshots": 28,
              "screenshots": sum(len(item["screenshots"]) + len(item["storage_images"]) for item in reports),
              "platforms": reports}
    require(report["screenshots"] == 150, "Visual snapshot must contain 122 institute and 28 real-menu/model screenshots")
    write_json(base / "snapshot.json", report)
    print(json.dumps({"stage": report["stage"], "screenshots": 150, "manifest_sha256": manifest_hash,
                      "directory": base.relative_to(ROOT).as_posix()}, ensure_ascii=False))
    return report


def snapshot_report():
    base = OUTPUT / "visual-inputs"
    report = read_json(base / "snapshot.json")
    require(report["stage"] == "verified_visual_snapshot" and report["screenshots"] == 150, "No complete retained visual snapshot; recapture both institute and storage native clients before --snapshot-visuals")
    require(report.get("visual_inputs", {}).get("sha256") == visual_inputs()["sha256"], "Retained screenshots are stale: production code/model/resource inputs changed; recapture both native clients")
    require(report["manifest_sha256"] == digest(MANIFEST), "Visual snapshot and release structure layout differ")
    require({p["loader"] for p in report["platforms"]} == set(LOADERS), "Visual snapshot must cover both loaders")
    for platform in report["platforms"]:
        require(len(platform["screenshots"]) == 61 and platform["native_visual"]["status"] == "PASS", "Retained visual acceptance is incomplete")
        require(len(platform["storage_images"]) == 14 and platform["storage_visual"]["status"] == "PASS", "Retained storage visual acceptance is incomplete")
        require({Path(item["file"]).name for item in platform["screenshots"]} == shot_names(read_json(MANIFEST)), "Retained camera set differs")
        for item in [*platform["screenshots"], *platform["storage_images"], *platform["previews"]]:
            path = base / item["file"]
            require(path.resolve().is_relative_to(base.resolve()) and path.is_file(), "Snapshot entry escapes the retained directory")
            require(path.stat().st_size == item["bytes"] and digest(path) == item["sha256"], f"Retained image changed: {item['file']}")
        fixture = ROOT / LOADERS[platform["loader"]]["project"] / "src/main/java/dev/hui/enderinfection/client/CentralInstituteVisualCheck.java"
        require(digest(fixture) == platform["fixture_source_sha256"], "Screenshot fixture source changed after the retained capture")
    return report


def restore_visuals():
    report = snapshot_report()
    base = OUTPUT / "visual-inputs"
    count = 0
    for platform in report["platforms"]:
        project_build = ROOT / LOADERS[platform["loader"]]["project"] / "build"
        destination = project_build / "central-institute-visual-check/screenshots"
        destination.mkdir(parents=True, exist_ok=True)
        for item in platform["screenshots"]:
            target = destination / Path(item["file"]).name
            require(target.resolve().is_relative_to(project_build.resolve()), "Visual restoration escaped the project build directory")
            shutil.copyfile(base / item["file"], target)
            require(digest(target) == item["sha256"], "Restored screenshot changed")
            count += 1
        storage_destination = project_build / "storage-visual-check/screenshots"
        storage_destination.mkdir(parents=True, exist_ok=True)
        for item in platform["storage_images"]:
            target = storage_destination / Path(item["file"]).name
            require(target.resolve().is_relative_to(project_build.resolve()), "Storage restoration escaped the project build directory")
            shutil.copyfile(base / item["file"], target)
            require(digest(target) == item["sha256"], "Restored storage screenshot changed")
            count += 1
    print(json.dumps({"stage": "verified_visuals_restored", "screenshots": count}, ensure_ascii=False))


def manifest_version(archive):
    text = archive.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n ", "").replace("\n ", "")
    found = re.search(r"(?m)^Implementation-Version:\s*([^\r\n]+)", text)
    require(found is not None, "JAR has no implementation version")
    return found[1].strip()


def class_versions(archive, expected):
    classes = [name for name in archive.namelist() if name.endswith(".class")]
    require(classes, "Artifact contains no classes")
    for name in classes:
        data = archive.read(name)
        require(data[:4] == b"\xca\xfe\xba\xbe" and struct.unpack(">H", data[6:8])[0] == expected, f"Wrong Java class target: {name}")


def minimum_core_dependency(metadata):
    parsed = tomllib.loads(metadata)
    dependencies = parsed.get("dependencies", {}).get("institute_addon", [])
    core = [row for row in dependencies if row.get("modId") == "ender_infection"]
    require(len(core) == 1 and core[0]["versionRange"] == "[0.12.0-alpha.2,)", "Addon minimum core dependency must remain 0.12.0-alpha.2")


def full_build_evidence(manifest):
    builds = read_json(DUAL / "verified-builds.json")
    require(source_version() == VERSION and builds["version"] == VERSION, "Build/source version is not the requested alpha.3")
    require(builds["status"] == "PASS" and builds["native_gametests_requested"] is True, "Final packaging requires build_dual_version.py --clean --gametests, not build-only evidence")
    require(len(builds["jars"]) == 2, "Expected both verified core JARs")
    expected = []
    for loader, spec in LOADERS.items():
        project = spec["project"]
        expected.extend(((loader, "rift_ai", f"{project}-rift-ai.log", 32),
                         (loader, "institute_api", f"{project}-institute.log", 12),
                         (loader, "central_institute", f"{project}-central-institute.log", 1),
                         (loader, "storage", f"{project}-storage.log", 6),
                         (loader, "ore", f"{project}-ore.log", 1),
                         (loader, "addon", f"{loader}-addon-tests.log", 2)))
    declared = {Path(row["log"].replace("\\", "/")).name: row for row in builds["tests"]}
    require(len(declared) == len(builds["tests"]), "Final build report contains duplicate native log names")
    baseline = {name: (loader, suite, minimum) for loader, suite, name, minimum in expected}
    require(set(baseline).issubset(declared), "The final build report omits a required native suite")
    tests = []
    for name, recorded in declared.items():
        if name in baseline:
            loader, suite, minimum = baseline[name]
        else:
            matches = [loader for loader, spec in LOADERS.items() if name.startswith(spec["project"] + "-") or name.startswith(loader + "-")]
            require(len(matches) == 1, f"Additional native suite lacks a unique loader: {name}")
            loader, suite, minimum = matches[0], Path(name).stem, 1
        path = DUAL / name
        actual = verify_gametest(path, minimum)
        text = path.read_text(encoding="utf-8", errors="replace")
        require("BUILD SUCCESSFUL" in text, f"Gradle did not finish successfully: {name}")
        require(recorded["status"] == "PASS" and recorded["required_passed"] == actual["required_passed"] and recorded["total"] == actual["total"], f"Native summary changed since the build report: {name}")
        test = {"loader": loader, "suite": suite, "status": "PASS", "total": actual["total"],
                "required_passed": actual["required_passed"], "evidence_log_name": name, "evidence_log_sha256": digest(path)}
        if suite == "central_institute":
            found = re.search(r"CENTRAL_INSTITUTE_NATIVE_PASS rooms=48 tiles=56 cursor_resume=(\d+) physical_lights=(\d+) exposed_crystals=(\d+) enclosed_crystals=(\d+)", text)
            require(found is not None, f"Central test lacks final physical exposure checks: {name}")
            require(int(found[2]) == len(manifest["light_sources"]) and int(found[3]) == len(manifest["crystal_lighting"]) and int(found[4]) == 0, f"Central physical lighting/exposure coverage differs: {name}")
            require("CENTRAL_INSTITUTE_NATIVE_LOOT_PASS categories=8" in text, f"Eight native loot categories were not exercised: {name}")
            test.update(physical_lights=int(found[2]), exposed_crystals=int(found[3]), enclosed_crystals=int(found[4]), loot_categories=8)
        tests.append(test)
    parity = read_json(DUAL / "parity.json")
    require(parity["status"] == "PASS" and parity["version"] == VERSION and not parity["issues"], "Final dual-version parity evidence is missing or stale")
    return builds, tests, parity


def stage_jars(builds, manifest):
    artifacts = []
    for loader, spec in LOADERS.items():
        project = ROOT / spec["project"]
        matches = [item for item in builds["jars"] if item["file"].replace("\\", "/").startswith(spec["project"] + "/")]
        require(len(matches) == 1, f"Missing unique verified core JAR for {loader}")
        recorded = matches[0]
        core = ROOT / recorded["file"]
        require(digest(core) == recorded["sha256"] and core.stat().st_size == recorded["bytes"], f"Final core JAR changed: {loader}")
        api = project / "build/api/ender-infection-api-dev.jar"
        for source, kind, name in ((core, "core", core.name), (api, "api_development", f"ender-infection-api-dev-{loader}-{spec['minecraft']}-{VERSION}.jar")):
            with zipfile.ZipFile(source) as archive:
                require(archive.testzip() is None and manifest_version(archive) == VERSION, f"Invalid/stale {kind} artifact: {source.name}")
                require(spec["metadata"] in archive.namelist(), f"Missing {loader} metadata")
                require(any(name.startswith("dev/hui/enderinfection/api/institute/") and name.endswith(".class") for name in archive.namelist()), "Institute API classes are absent")
                class_versions(archive, spec["java"])
                require(json.loads(archive.read("data/ender_infection/central_institute.json")) == manifest, f"JAR layout differs from the verified visual/static layout: {source.name}")
                for tile in manifest["tiles"]:
                    namespace, key = tile["template"].split(":", 1)
                    data = archive.read(f"data/{namespace}/{spec['data_dir']}/{key}.nbt")
                    require(hashlib.sha256(data).hexdigest() == tile["sha256"], f"Packaged tile differs: {source.name}/{key}")
            target = OUTPUT / name
            shutil.copyfile(source, target)
            artifacts.append({**file_entry(target, OUTPUT, kind), "loader": loader, "minecraft": spec["minecraft"], "java_class_major": spec["java"]})
        addon_base = ROOT / "examples/institute-addon" / loader
        addon = addon_base / f"build/libs/institute-addon-{loader}-{spec['minecraft']}-0.1.0.jar"
        with zipfile.ZipFile(addon) as archive:
            require(archive.testzip() is None and manifest_version(archive) == "0.1.0", f"Invalid addon JAR: {loader}")
            class_versions(archive, spec["java"])
            require(not any(name.startswith("dev/hui/enderinfection/") and name.endswith(".class") for name in archive.namelist()), "Addon illegally bundles core classes")
            require(f"data/institute_addon/{spec['data_dir']}/analysis_room.nbt" in archive.namelist(), "Addon template is missing")
            minimum_core_dependency(archive.read(spec["metadata"]).decode("utf-8"))
        minimum_core_dependency((addon_base / "src/main/resources" / spec["metadata"]).read_text(encoding="utf-8"))
        require(digest(addon_base / "build/coreDependency/ender-infection-api-dev.jar") == digest(api), f"{loader} addon did not compile/test against the final API-development JAR")
        target = OUTPUT / addon.name
        shutil.copyfile(addon, target)
        artifacts.append({**file_entry(target, OUTPUT, "addon_example"), "loader": loader, "minecraft": spec["minecraft"], "java_class_major": spec["java"], "minimum_core_version": "0.12.0-alpha.2"})
    return artifacts


def write_archive(path, entries):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
        for name, value in sorted(entries.items()):
            require(not name.startswith("/") and ".." not in Path(name).parts, "Unsafe archive entry")
            if isinstance(value, Path):
                archive.write(value, name)
            else:
                archive.writestr(name, value)
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, f"Archive CRC check failed: {path.name}")


def addon_sources(artifacts, tests):
    source = ROOT / "examples/institute-addon"
    root_files = {"README.md", "LICENSE", ".gitignore", "generate_room.py", "verify_examples.py"}
    loader_files = {"build.gradle", "settings.gradle", "gradle.properties", "gradlew", "gradlew.bat"}
    entries = {}
    files = []
    for path in sorted(source.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(source)
        parts = relative.parts
        allowed = (len(parts) == 1 and path.name in root_files)
        allowed |= parts[:3] == ("shared", "src", "main")
        if parts[0] in LOADERS:
            allowed |= len(parts) == 2 and path.name in loader_files
            allowed |= len(parts) > 3 and parts[1:3] == ("src", "main")
            allowed |= len(parts) == 4 and parts[1:3] == ("gradle", "wrapper") and path.name in {"gradle-wrapper.jar", "gradle-wrapper.properties"}
        if not allowed:
            continue
        require(not any(part in {"build", "run", ".gradle", "saves", "__pycache__"} for part in parts), "Generated/private file entered source allowlist")
        entries["institute-addon/" + relative.as_posix()] = path
        files.append(file_entry(path, source))
    require(any(item["file"].endswith("gradle-wrapper.jar") for item in files) and any(item["file"].endswith("AddonGameTestChecks.java") for item in files), "Source package omits the wrapper or native tests")
    api_doc = (ROOT / "docs/institute-api.md").read_text(encoding="utf-8").replace("../examples/institute-addon/README.md", "README.md")
    entries["institute-addon/INSTITUTE_API.md"] = api_doc.encode("utf-8")
    files.append({"file": "INSTITUTE_API.md", "bytes": len(entries["institute-addon/INSTITUTE_API.md"]), "sha256": hashlib.sha256(entries["institute-addon/INSTITUTE_API.md"]).hexdigest()})
    validation = {"status": "PASS", "minimum_core_version": "0.12.0-alpha.2", "required_api_version": 1,
                  "validated_with_core_version": VERSION, "client_visual_validation": "not_run",
                  "platforms": [{"loader": loader, "minecraft": LOADERS[loader]["minecraft"],
                                 "artifact": next(item for item in artifacts if item["loader"] == loader and item["kind"] == "addon_example"),
                                 "native_gametests": next(item for item in tests if item["loader"] == loader and item["suite"] == "addon")}
                                for loader in LOADERS]}
    entries["institute-addon/validation.json"] = json_bytes(validation)
    files.append({"file": "validation.json", "bytes": len(entries["institute-addon/validation.json"]), "sha256": hashlib.sha256(entries["institute-addon/validation.json"]).hexdigest()})
    entries["institute-addon/source-manifest.json"] = json_bytes({"format_version": 1, "files": files, "validated_with_core_version": VERSION})
    target = OUTPUT / f"institute-addon-source-v1-{VERSION}.zip"
    write_archive(target, entries)
    return file_entry(target, OUTPUT, "addon_sources")


def gallery_html(manifest):
    panels = []
    for loader, spec in LOADERS.items():
        prefix = f"{loader}-{spec['minecraft']}"
        previews = "".join(f'<a href="{prefix}/previews/{name}"><img loading="lazy" src="{prefix}/previews/{name}" alt="{html.escape(name)}"></a>' for name in ("overview.png", "rooms_A_G1.png", "rooms_B_G2.png", "rooms_C_G3.png", "rooms_D_B1.png", "rooms_E_B2.png", "rooms_F_B3.png"))
        rooms = "".join(f'<a href="{prefix}/screenshots/room_{room["id"]}.png">{html.escape(room["id"]+" "+room["name"])}</a>' for room in manifest["rooms"])
        menus = "".join(f'<a href="{prefix}/storage/menu_{key}.png">{html.escape(key)}</a>' for key in STORAGE_KEYS)
        panels.append(f'<section><h2>{loader} · Minecraft {spec["minecraft"]}</h2><div class="grid">{previews}</div><p class="rooms">{rooms}</p><p><a href="{prefix}/screenshots/01_exterior_rear.png">背面原图</a> · <a href="{prefix}/screenshots/02_atrium.png">中庭原图</a> · <a href="{prefix}/screenshots/night_reactor.png">夜间晶体大厅</a></p><h3>真实27格库存与开柜模型</h3><p class="rooms">{menus}<a href="{prefix}/storage/model_open_lab_cabinet.png">实际开柜模型</a></p></section>')
    return ('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>中央研究所双版本实景</title><style>body{max-width:1480px;margin:24px auto;padding:0 20px;font:16px/1.7 system-ui,sans-serif;background:#eef1f4;color:#26323e}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px}img{width:100%;height:auto}.rooms{display:flex;gap:8px 18px;flex-wrap:wrap}a{color:#145e85}section{margin:32px 0}</style><h1>中央研究所：双版本原生实景</h1><p>150张原生客户端截图：122张研究所房间与建筑实景，加28张真实库存界面及开柜模型。总览图仅等比缩放排版；原始截图像素未修改。</p>''' + "".join(panels) + '</html>\n').encode("utf-8")


def runtime_archive(snapshot, manifest, static, tests, artifacts):
    base = OUTPUT / "visual-inputs"
    prefix = f"central-institute-runtime-{VERSION}/"
    entries = {}
    source_files = []
    public_platforms = []
    for platform in snapshot["platforms"]:
        loader = platform["loader"]
        target_prefix = f"{loader}-{LOADERS[loader]['minecraft']}"
        copied = []
        for item in [*platform["screenshots"], *platform["storage_images"], *platform["previews"]]:
            relative = Path(item["file"])
            target = target_prefix + "/" + "/".join(relative.parts[1:])
            entries[prefix + target] = base / relative
            row = {**item, "file": target}
            copied.append(row)
            source_files.append(row)
        public_platforms.append({key: platform[key] for key in ("loader", "minecraft", "capture_core_version", "native_visual", "storage_visual", "visual_log_sha256", "storage_log_sha256", "image_operations")})
        entries[prefix + f"verification/{loader}-images.json"] = json_bytes({"loader": loader, "files": copied})
    inside = {"status": "PASS", "release_version": VERSION, "layout_version": manifest["layout_version"],
              "manifest_sha256": digest(MANIFEST), "screenshots": 150, "institute_screenshots": 122, "storage_screenshots": 28, "static": static,
              "native_release_tests": tests, "visual": public_platforms, "release_artifacts_before_image_archive": artifacts,
              "visual_binding": "Native capture code/model/resource fingerprint, final JAR manifests and all 56 tile hashes match. Capture version labels are recorded separately.",
              "privacy": "Only named screenshots, previews and structured verification are included; player worlds and raw logs are excluded."}
    entries[prefix + "verification/verification.json"] = json_bytes(inside)
    entries[prefix + "verification/central_institute.json"] = MANIFEST
    entries[prefix + "image-manifest.json"] = json_bytes({"format_version": 1, "files": source_files})
    entries[prefix + "index.html"] = gallery_html(manifest)
    entries[prefix + "README.txt"] = (f"中央研究所双版本实景 {VERSION}\n\n打开 index.html 查看总览、六层房间表、真实库存及原图链接。两版各61张研究所实景和14张库存/开柜PNG，共150张。\n原图未经像素修饰；预览只做等比缩放和排版。verification/ 含实际布局与验证数据。\n截图来自本次修改后的原生客户端；源码、模型与资源指纹记录在验证数据中。最终发布JAR按同一布局清单与56片NBT哈希核对。\n本图包不包含玩家世界、客户端原始日志或旧版265MB设计图册/PDF。\n").encode("utf-8")
    target = OUTPUT / f"central-institute-runtime-{VERSION}.zip"
    write_archive(target, entries)
    return file_entry(target, OUTPUT, "runtime_screenshots")


def package_release():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    manifest = read_json(MANIFEST)
    snapshot = snapshot_report()
    builds, tests, parity = full_build_evidence(manifest)
    static = verify_structure(manifest, ROOT / "ender_infection_colored/src/main/resources")
    artifacts = stage_jars(builds, manifest)
    artifacts.append(addon_sources(artifacts, tests))
    artifacts.append(runtime_archive(snapshot, manifest, static, tests, artifacts))
    verification = {"format_version": 1, "status": "PASS", "stage": "ready_for_release", "version": VERSION,
                    "created_at_utc": datetime.now(timezone.utc).isoformat(), "publication_performed_by_packaging_tool": False,
                    "manifest_sha256": digest(MANIFEST), "static": static, "parity": parity,
                    "visual_inputs_sha256": snapshot["visual_inputs"]["sha256"],
                    "native_gametests_requested": True, "native_required_tests_passed": sum(item["required_passed"] for item in tests),
                    "native_tests": tests, "screenshots": 150, "institute_screenshots": 122, "storage_screenshots": 28,
                    "visual_capture_versions": {p["loader"]: p["capture_core_version"] for p in snapshot["platforms"]},
                    "visual_layout_matches_final_jars": True, "artifacts": artifacts}
    write_json(OUTPUT / "verification.json", verification)
    verification_entry = file_entry(OUTPUT / "verification.json", OUTPUT, "verification")
    assets = [*artifacts, verification_entry]
    for loader, spec in LOADERS.items():
        selected = [item for item in assets if "loader" not in item or item["loader"] == loader]
        name = f"SHA256SUMS-{loader}-{spec['minecraft']}.txt"
        (OUTPUT / name).write_text("".join(f"{item['sha256']}  {item['file']}\n" for item in sorted(selected, key=lambda row: row["file"])), encoding="utf-8")
        assets.append({**file_entry(OUTPUT / name, OUTPUT, "checksums"), "loader": loader})
    # Do not recursively checksum a checksum file or put the receipt into itself.
    global_sums = OUTPUT / "SHA256SUMS.txt"
    global_sums.write_text("".join(f"{item['sha256']}  {item['file']}\n" for item in sorted(assets, key=lambda row: row["file"])), encoding="utf-8")
    assets.append(file_entry(global_sums, OUTPUT, "checksums"))
    receipt = {"stage": "ready_for_release", "version": VERSION, "publication_performed_by_packaging_tool": False,
               "upload_files": assets, "upload_file_count": len(assets), "native_required_tests_passed": verification["native_required_tests_passed"],
               "screenshots": 150, "release_notes_modified": False}
    require(len(assets) == 12, "Unexpected release asset count")
    write_json(OUTPUT / "package-receipt.json", receipt)
    print("CENTRAL_RELEASE_PACKAGE_PASS " + json.dumps({"version": VERSION, "assets": len(assets), "screenshots": 150,
                                                        "native_required_tests_passed": receipt["native_required_tests_passed"],
                                                        "receipt": (OUTPUT / "package-receipt.json").relative_to(ROOT).as_posix()}, ensure_ascii=False))


def asset_plan():
    files = []
    for loader, spec in LOADERS.items():
        core_name = f"ender-infection-neoforge-1.21.1-{VERSION}.jar" if loader == "neoforge" else f"ender-infection-{VERSION}.jar"
        files.extend(({"file": core_name, "kind": "core", "loader": loader},
                      {"file": f"ender-infection-api-dev-{loader}-{spec['minecraft']}-{VERSION}.jar", "kind": "api_development", "loader": loader},
                      {"file": f"institute-addon-{loader}-{spec['minecraft']}-0.1.0.jar", "kind": "addon_example", "loader": loader}))
    files.extend(({"file": f"institute-addon-source-v1-{VERSION}.zip", "kind": "addon_sources"},
                  {"file": f"central-institute-runtime-{VERSION}.zip", "kind": "runtime_screenshots", "native_screenshots": 150, "institute_screenshots": 122, "storage_screenshots": 28},
                  {"file": "verification.json", "kind": "verification"},
                  {"file": "SHA256SUMS-forge-1.20.1.txt", "kind": "checksums"},
                  {"file": "SHA256SUMS-neoforge-1.21.1.txt", "kind": "checksums"},
                  {"file": "SHA256SUMS.txt", "kind": "checksums"}))
    report = {"stage": "asset_plan_only", "version": VERSION, "minimum_addon_core_version": "0.12.0-alpha.2",
              "upload_files": files, "requires_final_evidence": ["Full dual-loader native build/test report, including both storage suites", "Final core/API/addon JAR version and SHA verification", "Retained 150 native screenshots: 122 institute plus 28 real-menu/model images, bound to unchanged production inputs", "Fresh static NBT validation"],
              "excluded_historical_assets": "The prior 265MB design atlas/PDF remains attached to its historical release."}
    write_json(OUTPUT / "asset-plan.json", report)
    print(json.dumps(report, ensure_ascii=False, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default=VERSION)
    parser.add_argument("--snapshot-visuals", action="store_true", help="Preserve verified screenshots/previews before Gradle clean; does not package a release")
    parser.add_argument("--restore-visuals", action="store_true", help="Restore retained screenshots to the two isolated build directories after clean")
    parser.add_argument("--plan", action="store_true", help="Write the expected upload-file list without claiming final tests or staging binaries")
    parser.add_argument("--prepare-visual-capture", action="store_true", help="Fingerprint production inputs before fresh native captures; prevents reusing old images after code/model changes")
    parser.add_argument("--mark-visuals-stale", metavar="REASON", help="Retain old images as reference while blocking them from final release packaging")
    args = parser.parse_args()
    require(args.version == VERSION, f"This release preparation targets {VERSION}")
    require(sum((args.snapshot_visuals, args.restore_visuals, args.plan, args.prepare_visual_capture, bool(args.mark_visuals_stale))) <= 1, "Choose one preparation operation")
    if args.mark_visuals_stale:
        mark_visuals_stale(args.mark_visuals_stale)
        return
    if args.prepare_visual_capture:
        prepare_visual_capture()
        return
    if args.plan:
        asset_plan()
        return
    if args.snapshot_visuals:
        snapshot_visuals()
        return
    if args.restore_visuals:
        restore_visuals()
        return
    package_release()


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, KeyError, AssertionError, zipfile.BadZipFile) as error:
        print(f"CENTRAL_RELEASE_PACKAGE_FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
