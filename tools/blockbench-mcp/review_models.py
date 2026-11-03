"""Capture saved creature models through the installed Blockbench MCP plugin."""

import argparse
import base64
import json
import math
from pathlib import Path
import subprocess
import time
import shutil

from client import BlockbenchMCP

ROOT = Path(__file__).resolve().parents[2]
MODELS = ROOT / "colored_units/design_pack_2026-11-02_v2/monsters"
OUTPUT = ROOT / "previews/blockbench-mcp-2026-11-03"
NAMES = [
    "lurking_forager", "sporewing_scout", "mycelial_burrower",
    "crystalback_carrier", "parasitic_spider_mother", "pulsing_hatch_sac",
    "crystal_shield_sentinel", "rift_throat_spitter", "leapblade_ambusher",
    "rootspike_turret", "resonance_priest", "siegebreaker_colossus",
    "walking_brood_crab", "abyssal_burrow_king",
]


def data(result):
    if "structuredContent" in result:
        return result["structuredContent"]
    for item in result.get("content", []):
        if item["type"] == "text":
            return json.loads(item["text"])
    raise RuntimeError("Expected structured MCP response")


def save_image(result, path):
    for item in result.get("content", []):
        if item["type"] == "image":
            path.write_bytes(base64.b64decode(item["data"]))
            return
    raise RuntimeError(f"No rendered image returned for {path}")


def capture(names, revision=None):
    output = OUTPUT / revision if revision else OUTPUT
    output.mkdir(parents=True, exist_ok=True)
    client = BlockbenchMCP()
    view = "creature_review"
    client.call("create_offscreen_view", {"id": view, "width": 1200, "height": 900})
    try:
        for name in names:
            path = MODELS / f"{name}.bbmodel"
            if revision:
                # A unique path prevents Blockbench reusing an older open tab.
                snapshot = ROOT / "ender_infection_colored/build/blockbench-review" / revision / path.name
                snapshot.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(path, snapshot)
                path = snapshot
            source = json.loads(path.read_text(encoding="utf-8"))
            # Load through the MCP connection, including native BB5 group layout.
            client.call('risky_eval', {'code':
                "(()=>{const path="+json.dumps(path.as_posix())+";"
                "const p=ModelProject.all.find(p=>p.save_path.replaceAll('\\\\','/')===path);"
                "if(p){p.select();}else{let model;Blockbench.read([path],{readtype:'text',errorbox:false},"
                "files=>model=JSON.parse(files[0].content));Codecs.project.load(model,{path});}"
                "return {name:Project.name,path:Project.save_path};})()"})
            for _ in range(40):
                info = data(client.call("get_project_info"))
                if info["project"]["name"] == name and Path(info["project"]["save_path"]).resolve() == path.resolve():
                    break
                time.sleep(0.25)
            else:
                raise RuntimeError(f"Blockbench did not open {name}")
            client.call("set_mode", {"mode_id": "edit"})
            bounds = data(client.call("risky_eval", {"code":
                "(()=>{const b=new THREE.Box3().setFromObject(Project.model_3d);"
                "return {min:b.min.toArray(),max:b.max.toArray()};})()"
            }))
            lo, hi = bounds["min"], bounds["max"]
            center = [(a+b)/2 for a,b in zip(lo,hi)]
            size = [b-a for a,b in zip(lo,hi)]
            distance = max(size) * 2.0
            metadata = {"server": client.info["serverInfo"], "project": info,
                        "bounds": bounds, "views": []}
            for angle, direction in [
                ("front", [0.9, 0.40, -1.4]),
                ("side", [1.6, 0.25, 0]),
                ("back", [-0.9, 0.40, 1.4]),
            ]:
                norm = math.sqrt(sum(v*v for v in direction))
                position = [c+distance*v/norm for c,v in zip(center,direction)]
                result = client.call("set_camera_angle", {
                    "view": view, "position": position, "target": center,
                    "projection": "perspective", "fov": 38, "format": "png",
                })
                save_image(result, output / f"{name}_{angle}.png")
                metadata["views"].append({"name": angle, "position": position, "target": center})
            client.call("set_mode", {"mode_id": "animate"})
            for clip, frame_name in [("walk", "walk"), ("attack", "attack_prepare"), ("attack", "attack")]:
                source_clip = "fly" if name == "sporewing_scout" and clip == "walk" else clip
                animation = next(a for a in source["animations"] if a["name"].split(".")[-1] == source_clip)
                sample_time = animation["length"] * (0.25 if source_clip == "fly" else 0.125) if clip == "walk" else (0.5 if frame_name == "attack_prepare" else 0.8)
                client.call("animation_timeline", {"animation_id": animation["name"], "action": "set_time", "time": sample_time})
                position = [center[i]+distance*[0.5,0.22,-0.8][i] for i in range(3)]
                result = client.call("set_camera_angle", {"view":view,"position":position,"target":center,"projection":"perspective","fov":38,"format":"png"})
                save_image(result, output / f"{name}_{frame_name}.png")
                metadata["views"].append({"name":frame_name,"animation":animation["name"],"time":sample_time})
            client.call("animation_timeline", {"action":"stop"})
            client.call("set_mode", {"mode_id":"edit"})
            (output / f"{name}.json").write_text(json.dumps(metadata,ensure_ascii=False,indent=2),encoding="utf-8")
            print(f"MCP_CAPTURE {name} 6 views", flush=True)
    finally:
        try:
            client.call("delete_offscreen_view", {"view": view})
        except RuntimeError as error:
            # Blockbench 5.2.2 can throw from Preview.delete after rendering.
            # Preserve successful captures and record the host cleanup error.
            if "onContextMenu is not defined" not in str(error):
                raise
            print("MCP_HOST_CLEANUP_WARNING onContextMenu is not defined", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("names", nargs="*", choices=NAMES)
    parser.add_argument("--revision")
    args = parser.parse_args()
    capture(args.names or NAMES, args.revision)
