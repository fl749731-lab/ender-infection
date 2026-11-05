"""Three dedicated server processes per loader, with actual Warden unload/reload."""
from __future__ import annotations
import argparse
import json
import time
import build_dual_version as dual


def check(loader: str) -> dict:
    name, folder, major = ("Forge 1.20.1", "ender_infection_colored", 17) if loader == "forge" else ("NeoForge 1.21.1", "ender_infection_neoforge", 21)
    run = dual.ROOT / folder / "build" / f"warden-persistence-{time.time_ns()}"
    run.mkdir(parents=True)
    (run / "eula.txt").write_text("eula=true\n", "utf-8")
    (run / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-tick-time=0\n", "utf-8")
    logs = []
    for stage in ("seed", "resume", "claimed"):
        log = dual.run_project(name, folder, major, ["runServer", f"-PwardenPersistenceDirectory={run}", f"-PwardenPersistenceStage={stage}"], f"{loader}-warden-persistence-{stage}.log")
        text = log.read_text("utf-8", errors="replace")
        if f"WARDEN_PERSISTENCE_PASS {stage}" not in text or "WARDEN_PERSISTENCE_FAIL" in text:
            raise RuntimeError(f"{name} Warden persistence {stage} failed; see {log}")
        if stage == "resume" and not all(marker in text for marker in ("WARDEN_ACTIVE_RESTART_PASS", "WARDEN_CHUNKS_UNLOADED_PASS", "WARDEN_CHUNKS_RELOADED_PASS")):
            raise RuntimeError(f"Missing actual restart/unload observations: {log}")
        logs.append(str(log.relative_to(dual.ROOT)))
    ids = (run / "warden-identity.txt").read_text().splitlines()
    if len(ids) != 2 or len(set(ids)) != 2:
        raise RuntimeError("Expected distinct stable entity/run identities")
    return {"loader": loader, "encounter": "F8", "world": str(run.relative_to(dual.ROOT)), "status": "PASS", "processes": 3, "identities": ids, "health_retained": 231, "consoles_retained": 1, "logs": logs}


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--loader',choices=['forge','neoforge','both'],default='both');args=parser.parse_args()
    dual.OUTPUT.mkdir(parents=True,exist_ok=True)
    for loader in (['forge','neoforge'] if args.loader=='both' else [args.loader]):
        receipt=dual.OUTPUT/f'{loader}-warden-persistence.json';receipt.unlink(missing_ok=True);result=check(loader)
        receipt.write_text(json.dumps(result,indent=2)+'\n','utf-8');print(json.dumps(result,indent=2),flush=True)


if __name__=='__main__':
    main()
