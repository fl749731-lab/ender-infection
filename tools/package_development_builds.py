"""Create local dual-loader development and independent addon-source deliveries."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    builds = json.loads((ROOT / "build/dual-version-validation/verified-builds.json").read_text("utf-8"))
    assert builds["status"] == "PASS"
    addon = json.loads((ROOT / "examples/institute-addon/validation.json").read_text("utf-8"))
    assert all(p["native_gametests_passed"] == 2 and not p["core_classes_bundled"] for p in addon["platforms"])
    entries = {}
    metadata = []
    for item in builds["jars"]:
        path = ROOT / item["file"]
        content = path.read_bytes()
        assert sha(content) == item["sha256"]
        loader = "neoforge-1.21.1" if "neoforge" in item["file"] else "forge-1.20.1"
        output = f"{loader}/{path.stem}-dev.jar"
        entries[output] = content
        metadata.append({"file": output, "bytes": len(content), "sha256": sha(content), "kind": "core"})
    for item in addon["platforms"]:
        path = ROOT / "examples/institute-addon" / item["artifact"]
        content = path.read_bytes()
        assert sha(content) == item["artifact_sha256"]
        output = f'{item["loader"]}-{item["minecraft"]}/{path.name}'
        entries[output] = content
        metadata.append({"file": output, "bytes": len(content), "sha256": sha(content), "kind": "addon_example"})
    entries["README.txt"] = """末影感染 双加载器开发构建

这是包含研究所API v1的本地开发构建，暂沿用0.12.0-alpha.1版本号。GitHub原有同版本发布包早于本API，请按开发包来源区分。

forge-1.20.1：Minecraft 1.20.1，Forge47.3.12，Java17，GeckoLib Forge1.20.1 4.4.9。
neoforge-1.21.1：Minecraft1.21.1，NeoForge21.1.265，Java21，GeckoLib NeoForge1.21.1 4.9.3。
每个目录内有一个本体JAR和一个可选的附属示例JAR。按实例版本使用对应目录；GeckoLib需另外安装。
本包不含开发依赖api-dev.jar。示例源码在另附institute-addon-source-v1.zip。

研究所API支持房间注册、尺寸/连接器元数据、模板预览与显式放置、设备交互。示例控制台可右键开关并发出12级方块光。
命令：/enderinstitute rooms；/enderinstitute devices；/enderinstitute preview <房间ID> <x y z> [none|cw90|cw180|ccw90]；/enderinstitute place 同参数。
先在已加载的空旷区域preview；放置目标包含房间地板，不应设在已有地面内部。命令需要权限等级2。

大型中央研究所的六层48间图册另附，整座建筑仍待组装与自然生成接入。API示例房间是独立的小模块。
本地开发包的生成没有替换任何已有游戏实例，也没有创建新GitHub Release。
""".encode("utf-8")
    entries["manifest.json"] = (json.dumps({"kind": "local_development_build", "files": metadata}, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    verification = ROOT / "build/dual-version-validation/final-verification.json"
    if verification.is_file():
        assert json.loads(verification.read_text("utf-8"))["status"] == "PASS"
        entries["verification.json"] = verification.read_bytes()
    DIST.mkdir(exist_ok=True)
    bundle = DIST / "ender-infection-dual-loader-development.zip"
    with zipfile.ZipFile(bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        for path, content in entries.items():
            archive.writestr(path, content)
    with zipfile.ZipFile(bundle) as archive:
        assert archive.testzip() is None
    source = ROOT / "examples/institute-addon"
    source_bundle = DIST / "institute-addon-source-v1.zip"
    excluded = {".gradle", "build", "run", ".idea", ".bootstrap"}
    source_count = 0
    with zipfile.ZipFile(source_bundle, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(source.rglob("*")):
            rel = path.relative_to(source)
            if not path.is_file() or any(p in excluded for p in rel.parts) or path.suffix in {".log", ".lck", ".lock", ".pyc"} or "__pycache__" in rel.parts:
                continue
            archive.write(path, "institute-addon/" + rel.as_posix())
            source_count += 1
        api_doc = (ROOT / "docs/institute-api.md").read_text("utf-8").replace("../examples/institute-addon/README.md", "README.md")
        archive.writestr("institute-addon/INSTITUTE_API.md", api_doc.encode("utf-8"))
    with zipfile.ZipFile(source_bundle) as archive:
        assert archive.testzip() is None
    result = {"artifacts": [{"file": p.name, "bytes": p.stat().st_size, "sha256": sha(p.read_bytes())} for p in (bundle, source_bundle)], "addon_source_files": source_count, "published": False, "installed_to_player_profile": False}
    (DIST / "dual-loader-development.receipt.json").write_text(json.dumps(result, indent=2) + "\n", "utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
