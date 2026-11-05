"""Three real server processes, one isolated world, per loader. No user instance access."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
import subprocess
import time
import build_dual_version as dual

def check(loader: str) -> dict:
    name, folder, major = ("Forge 1.20.1", "ender_infection_colored", 17) if loader == "forge" else ("NeoForge 1.21.1", "ender_infection_neoforge", 21)
    run = dual.ROOT / folder / "build" / f"incident-persistence-{time.time_ns()}"
    run.mkdir(parents=True)
    (run / "eula.txt").write_text("eula=true\n", "utf-8")
    (run / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-tick-time=0\n", "utf-8")
    logs = []
    for stage in ("seed", "resume", "claimed"):
        log = dual.run_project(name, folder, major, ["runServer", f"-PincidentPersistenceDirectory={run}", f"-PincidentPersistenceStage={stage}"], f"{loader}-incident-persistence-{stage}.log")
        text = log.read_text("utf-8", errors="replace")
        if f"INCIDENT_PERSISTENCE_PASS {stage}" not in text or "INCIDENT_PERSISTENCE_FAIL" in text:
            raise RuntimeError(f"{name} persistence stage {stage} failed; see {log}")
        if stage == "resume" and not all(marker in text for marker in ("INCIDENT_ACTIVE_RESTART_PASS", "INCIDENT_CHUNKS_UNLOADED_PASS", "INCIDENT_CHUNKS_RELOADED_PASS")):
            raise RuntimeError(f"Missing actual restart/unload observations: {log}")
        logs.append(str(log.relative_to(dual.ROOT)))
    ids = (run / "incident-identities.txt").read_text().splitlines()
    if len(ids) != 6 or len(set(ids)) != 6:
        raise RuntimeError("Expected six stable, distinct entity identities")
    return {"loader": loader, "world": str(run.relative_to(dual.ROOT)), "status": "PASS", "processes": 3, "identities": ids, "logs": logs}

def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--loader", choices=("forge", "neoforge", "both"), default="both")
    args = parser.parse_args()
    dual.OUTPUT.mkdir(parents=True, exist_ok=True)
    loaders = ("forge", "neoforge") if args.loader == "both" else (args.loader,)
    for loader in loaders:
        receipt = dual.OUTPUT / f"{loader}-incident-persistence.json"
        receipt.unlink(missing_ok=True)
        result = check(loader)
        receipt.write_text(json.dumps(result, indent=2) + "\n", "utf-8")
        print(json.dumps(result, indent=2), flush=True)

if __name__ == "__main__":
    main()
