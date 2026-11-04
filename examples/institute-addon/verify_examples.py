"""Check native test summaries and addon packaging; fail on a zero-test/startup failure.

Run each loader's `gradlew build runGameTestServer` with its output redirected to
gametest-validation.log, then run this script from any directory. No third-party
Python packages are required. It writes validation.json only when both pass.
"""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import struct
import zipfile

ROOT = Path(__file__).resolve().parent


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(loader: str, minecraft: str, major: int, structure_dir: str) -> dict:
    base = ROOT / loader
    # PowerShell may use UTF-16 LE for redirection on Windows PowerShell 5.
    raw = (base / "gametest-validation.log").read_bytes()
    log = raw.decode("utf-16" if raw.startswith(b"\xff\xfe") else "utf-8", errors="replace")
    assert "BUILD SUCCESSFUL" in log, f"{loader}: Gradle has not completed successfully"
    assert "Failed to start the minecraft server" not in log, f"{loader}: server startup failed"
    assert re.search(r"\b2 GAME TESTS COMPLETE\b", log), f"{loader}: expected exactly two completed native tests"
    assert "All 2 required tests passed" in log, f"{loader}: both required native tests must pass"
    artifact = base / f"build/libs/institute-addon-{loader}-{minecraft}-0.1.0.jar"
    with zipfile.ZipFile(artifact) as archive:
        names = archive.namelist()
        assert not any(name.startswith("dev/hui/enderinfection/") and name.endswith(".class") for name in names), \
            f"{loader}: core classes must never be shaded into the addon"
        for name in names:
            if name.endswith(".class"):
                assert struct.unpack(">H", archive.read(name)[6:8])[0] == major, f"{loader}: wrong Java bytecode target"
        template = f"data/institute_addon/{structure_dir}/analysis_room.nbt"
        assert template in names, f"{loader}: room template missing from artifact"
        assert "assets/institute_addon/blockstates/research_console.json" in names
    core = base / "build/coreDependency/ender-infection-api-dev.jar"
    return {
        "loader": loader,
        "minecraft": minecraft,
        "java_class_major": major,
        "artifact": artifact.relative_to(ROOT).as_posix(),
        "artifact_bytes": artifact.stat().st_size,
        "artifact_sha256": sha(artifact),
        "core_dev_dependency_sha256": sha(core),
        "core_classes_bundled": False,
        "native_gametests_passed": 2,
        "native_checks": ["registered_template_preview_and_placement", "console_toggle_offhand_pass_and_light"],
        "log": (base / "gametest-validation.log").relative_to(ROOT).as_posix(),
        "room_template": template,
    }


def main() -> None:
    report = {
        "verified_at_utc": datetime.now(timezone.utc).isoformat(),
        "published_core_alpha_1_has_api": False,
        "requires_workspace_api_v1_build": True,
        "client_visual_validation": "not_run",
        "platforms": [check("forge", "1.20.1", 61, "structures"),
                      check("neoforge", "1.21.1", 65, "structure")],
    }
    (ROOT / "validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("Both addon artifacts verified; 2/2 Forge and 2/2 NeoForge native GameTests passed; core code is not bundled.")


if __name__ == "__main__":
    main()
