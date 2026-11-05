"""Build/verify both supported loaders using their own Java installation."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from verify_gametest_log import verify

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "build/dual-version-validation"


def java_for(major: int) -> str:
    def matches(candidate: str) -> bool:
        result = subprocess.run([candidate, "-version"], capture_output=True, text=True)
        found = re.search(r'version "(?:1\.)?(\d+)', result.stderr + result.stdout)
        return result.returncode == 0 and found is not None and int(found.group(1)) == major

    for key in (f"JAVA{major}_HOME", f"JAVA_HOME_{major}_X64"):
        home = os.environ.get(key)
        if home:
            path = Path(home) / "bin" / ("java.exe" if os.name == "nt" else "java")
            if not path.is_file() or not matches(str(path)):
                raise RuntimeError(f"{key} must point to a JDK {major}, got {home}")
            return str(path)
    candidates = [shutil.which("java")]
    if os.name == "nt":
        candidates.extend([
            str(ROOT / "Minecraft1201/java17/bin/java.exe"),
            *[str(p) for p in Path("C:/Program Files/Eclipse Adoptium").glob(f"jdk-{major}*/bin/java.exe")],
        ])
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            if matches(candidate):
                return candidate
    raise RuntimeError(f"Set JAVA{major}_HOME to an installed JDK {major}")


def run_project(name: str, folder: str, major: int, tasks: list[str], log_name: str) -> Path:
    java = java_for(major)
    environment = dict(os.environ, JAVA_HOME=str(Path(java).parent.parent))
    environment["PATH"] = str(Path(java).parent) + os.pathsep + environment.get("PATH", "")
    if major >= 21:
        environment.setdefault("NFRT_ASSET_ROOT", str(ROOT / "build/minecraft-assets-1.21.1"))
    gradle_jvm_args = environment.get("ENDER_GRADLE_JVM_ARGS", "-Xmx1G")
    command = [java, "-cp", "gradle/wrapper/gradle-wrapper.jar", "org.gradle.wrapper.GradleWrapperMain", "--no-daemon", "--console=plain", f"-Dorg.gradle.jvmargs={gradle_jvm_args}"]
    init_script = environment.get("ENDER_GRADLE_INIT_SCRIPT")
    if init_script:
        script = Path(init_script).resolve()
        if not script.is_file():
            raise RuntimeError(f"ENDER_GRADLE_INIT_SCRIPT does not exist: {script}")
        command.extend(["--init-script", str(script)])
    command.extend(tasks)
    print(f"{name}: {' '.join(tasks)} (Java {major})", flush=True)
    log = OUTPUT / log_name
    with log.open("w", encoding="utf-8") as output:
        result = subprocess.run(command, cwd=ROOT / folder, env=environment, stdout=output, stderr=subprocess.STDOUT)
    if result.returncode:
        lines = log.read_text("utf-8", errors="replace").splitlines()
        print("\n".join(lines[-80:]), file=sys.stderr)
        raise RuntimeError(f"{name} failed; see {log}")
    return log


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--clean", action="store_true")
    parser.add_argument("--gametests", action="store_true", help="Run focused AI/rift and institute suites on each loader")
    args = parser.parse_args()
    OUTPUT.mkdir(parents=True, exist_ok=True)
    # A failed run must never leave an older successful receipt as its result.
    report_path = OUTPUT / "verified-builds.json"
    report_path.unlink(missing_ok=True)
    subprocess.run([sys.executable, str(ROOT / "tools/verify_central_institute.py")], cwd=ROOT, check=True)
    subprocess.run([sys.executable, str(ROOT / "tools/sync_neoforge_resources.py")], cwd=ROOT, check=True)
    subprocess.run([sys.executable, str(ROOT / "tools/check_dual_version.py"), "--output", str(OUTPUT / "parity.json")], cwd=ROOT, check=True)
    version = json.loads((OUTPUT / "parity.json").read_text("utf-8"))["version"]
    projects = (("Forge 1.20.1", "ender_infection_colored", 17), ("NeoForge 1.21.1", "ender_infection_neoforge", 21))
    tests = []
    persistence_checks = []
    for name, folder, major in projects:
        tasks = (["clean"] if args.clean else []) + ["build", "apiDevJar"]
        run_project(name, folder, major, tasks, f"{folder}-build.log")
        if args.gametests:
            log = run_project(name, folder, major, ["runGameTestServer", "-PriftChecks"], f"{folder}-rift-ai.log")
            tests.append(verify(log, 32))
            log = run_project(name, folder, major, ["runGameTestServer", "-PinstituteChecks"], f"{folder}-institute.log")
            tests.append(verify(log, 12))
            log = run_project(name, folder, major, ["runGameTestServer", "-PcentralInstituteChecks"], f"{folder}-central-institute.log")
            tests.append(verify(log, 1))
            log = run_project(name, folder, major, ["runGameTestServer", "-PstorageChecks"], f"{folder}-storage.log")
            tests.append(verify(log, 6))
            log = run_project(name, folder, major, ["runGameTestServer", "-PprocessingChecks"], f"{folder}-processing.log")
            tests.append(verify(log, 6))
            log = run_project(name, folder, major, ["runGameTestServer", "-PoperationsChecks"], f"{folder}-operations.log")
            tests.append(verify(log, 6))
            log = run_project(name, folder, major, ["runGameTestServer", "-PincidentChecks"], f"{folder}-incidents.log")
            tests.append(verify(log, 8))
            from check_incident_persistence import check
            persistence_checks.append(check("neoforge" if "neoforge" in folder else "forge"))
            log = run_project(name, folder, major, ["runGameTestServer", "-PoreChecks"], f"{folder}-ore.log")
            tests.append(verify(log, 1))
        addon = "neoforge" if "neoforge" in folder else "forge"
        addon_folder = f"examples/institute-addon/{addon}"
        run_project(name + " addon", addon_folder, major, ["build"], f"{addon}-addon-build.log")
        if args.gametests:
            log = run_project(name + " addon", addon_folder, major, ["runGameTestServer"], f"{addon}-addon-tests.log")
            tests.append(verify(log, 2))
    jars = []
    for _, folder, _ in projects:
        candidates = [p for p in (ROOT / folder / "build/libs").glob("*.jar") if p.name.endswith(f"-{version}.jar")]
        if len(candidates) != 1:
            raise RuntimeError(f"Expected one main jar for {version} under {folder}/build/libs; use --clean")
        jar = candidates[0]
        with zipfile.ZipFile(jar) as archive:
            names = archive.namelist()
            if not any(x.startswith("dev/hui/enderinfection/api/institute/") and x.endswith(".class") for x in names):
                raise RuntimeError(f"Shared institute API missing from {jar}")
            metadata = "META-INF/neoforge.mods.toml" if "neoforge" in folder else "META-INF/mods.toml"
            if metadata not in names:
                raise RuntimeError(f"Loader metadata missing from {jar}")
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8").replace("\r\n ", "")
            if f"Implementation-Version: {version}\r\n" not in manifest and f"Implementation-Version: {version}\n" not in manifest:
                raise RuntimeError(f"JAR implementation version is not {version}: {jar}")
        jars.append({"file": jar.relative_to(ROOT).as_posix(), "bytes": jar.stat().st_size, "sha256": hashlib.sha256(jar.read_bytes()).hexdigest()})
    report = {"status": "PASS", "version": version, "native_gametests_requested": args.gametests, "tests": tests, "persistence_checks": persistence_checks, "jars": jars}
    report_path.write_text(json.dumps(report, indent=2) + "\n", "utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
