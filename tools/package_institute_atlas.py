"""Package the verified interior drawings without author-machine/MCP history."""
from __future__ import annotations

import hashlib
import html
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[1]
ATLAS = ROOT / "previews/central_research_institute_interiors_v01"
PDF = ATLAS / "central-institute-interior-atlas-v01.pdf"
ARCHIVE = ROOT / "dist/central-research-institute-interiors-v01.zip"


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def concept_provenance(metadata=None) -> list[dict]:
    """Read repo-relative inputs, with compatibility for the old local manifest."""
    path=Path(metadata) if metadata else ATLAS/'concepts/provenance.json'
    if not path.is_file():path=ATLAS/'concepts/generated_images.json'
    return [{"file": row['file'] if 'file' in row else f"concepts/{row['sheet']}.png",
             "rooms": row['rooms'], "kind": "AI-generated design reference"}
            for row in json.loads(path.read_text('utf-8'))]


def main() -> None:
    data = json.loads((ATLAS / "room_placements.json").read_text("utf-8"))
    rooms = data["rooms"]
    pages = json.loads((ATLAS / "page_index.json").read_text("utf-8"))
    checks = json.loads((ATLAS / "placement_validation.json").read_text("utf-8"))
    assert checks["status"] == "PASS" and len(rooms) == 48 and len(pages) == 73
    assert all((ATLAS / p["file"]).is_file() for p in pages)
    proofs = ROOT / "build/institute-atlas-proof"
    assert all((proofs / f"page-{p:02}.png").is_file() for p in range(1, 74))
    assert PDF.is_file()
    placements = sum(len(r["placements"]) for r in rooms)

    readme = f"""中央研究所 六层与48间内饰图册 v01

打开 index.html 可按楼层浏览，或打开独立 PDF（73页）。PDF 在图包旁单独提供，避免重复下载。
本图包含48间逐室 PNG/SVG、6层总图、12张四室概念图、方块图例与公共交通说明、逐室及总表 CSV。
尺寸、坐标与实际方块摆放以逐格图和 CSV 为准；概念图是细节目标，并非游戏截图。

摆放顺序
1. 用整层图确定房间原点。U向东、V向南，H0是本层地板方块。
2. 放置地板、墙面、顶棚；普通楼层净高5格，B3净高9格。
3. 按M编号摆放家具模块。图中蓝点是家具根方块，自动占位部分不要重复摆物品。
4. 按吊顶图摆灯和管线，核对门、两格主通道、楼梯及货运井净空。
5. 使用 csv/房号.csv 查完整命名空间、朝向、状态、建筑坐标和手动物品数。

文件用途
rooms/：逐室平面、吊顶、北墙、家具原点和材料数量。
floors/：六层位置与本层材料、高度。
concepts/：图像生成的室内细节参考；prompts.json 保存完整文字提示。
guides/05_compatibility：现有方块、组合搭建、待补模型的对照。
csv/ALL_ROOMS.csv：{placements:,}条方块根位置/摆放记录，含自动占位元数据。
room_placements.json：程序可读坐标；placement_validation.json：48间布局检查。
manifest.json：交付文件的字节数与 SHA256。

落地范围
66种方块ID来自当前Forge1.20.1工程和原版。显微镜、轮式推车、完整安检机、软帘和无缝大屏等尚无独立模型，按对照页替代。
组合供能装置、冷却泵组、服务器等在此图册中表达外观；功能由后续设备代码实现。
路径检查是设计网格检查，不等同于游戏寻路测试。大型研究所本体仍待组装和自然生成接入。
紫晶区域使用灯板/海晶灯补光；原版紫晶本身不因这份设计获得方块发光属性。

本交付包按用户要求使用图纸与概念图展示，不依赖 Blockbench。
"""
    (ATLAS / "README.txt").write_text(readme, "utf-8")
    room_cards = []
    for r in rooms:
        title = html.escape(f'{r["id"]} {r["name"]}')
        room_cards.append(
            f'<article data-level="{r["level"]}"><a href="rooms/{r["id"]}.png">'
            f'<img loading="lazy" src="rooms/{r["id"]}.png" alt="{title}"></a>'
            f'<h3>{title}</h3><p>{r["level"]} · {r["size"][0]}×{r["size"][1]}格 · '
            f'地板Y {r["floor_y"]:+}</p><p><a href="rooms/{r["id"]}.svg">SVG</a> · '
            f'<a href="csv/{r["id"]}.csv">逐格CSV</a></p></article>'
        )
    nav = " ".join(f'<button data-filter="{x}">{x}</button>' for x in ("全部", "G1", "G2", "G3", "B1", "B2", "B3"))
    floors = " ".join(f'<a href="floors/{x}.png">{x}整层图</a>' for x in ("G1", "G2", "G3", "B1", "B2", "B3"))
    (ATLAS / "index.html").write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>中央研究所48间内饰图册</title>
<style>body{margin:0;background:#ecf1f4;color:#203441;font:16px/1.7 system-ui,sans-serif}main{max-width:1500px;margin:auto;padding:32px}h1{margin-bottom:8px}header p{max-width:1000px}a{color:#146b87}nav{position:sticky;top:0;background:#ecf1f4ee;padding:12px 0}button{font:inherit;padding:7px 20px;margin:4px;border:1px solid #9cb6c1;border-radius:6px;cursor:pointer;background:white}button[aria-pressed=true]{background:#146b87;color:white}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(340px,1fr));gap:22px}article{background:white;border-radius:10px;padding:12px}article img{width:100%;height:auto}article h3,article p{margin:6px}article[hidden]{display:none}.links{display:flex;gap:18px;flex-wrap:wrap}.note{padding:12px 18px;background:#fff8e8;border-left:4px solid #a9772a}</style>
<main><header><h1>中央研究所 六层与48间内饰图册</h1><p>先看整层位置，再打开房间原图放大查看。每间提供逐格内饰、吊顶、北墙、家具原点和材料表。概念图表达细节目标；方块落位以左侧网格与CSV为准。</p>
<p class="note">显微镜、轮式推车、完整扫描机、软帘和无缝大屏等需补模型。大型研究所仍处于设计阶段；这套图纸尚未作为建筑接入游戏。</p>
<p class="links"><a href="README.txt">摆放说明</a><a href="guides/00_reading_guide.png">坐标读法</a><a href="guides/05_compatibility.png">现有方块与待补模型</a><a href="guides/04_circulation.png">楼梯与公共交通</a><a href="csv/ALL_ROOMS.csv">全部方块CSV</a></p><p class="links">'''+floors+'</p></header><nav>'+nav+'</nav><section class="grid">'+''.join(room_cards)+'''</section></main>
<script>const buttons=document.querySelectorAll('button[data-filter]');buttons.forEach(b=>b.addEventListener('click',()=>{buttons.forEach(x=>x.setAttribute('aria-pressed',String(x===b)));document.querySelectorAll('article').forEach(a=>a.hidden=b.dataset.filter!=='全部'&&a.dataset.level!==b.dataset.filter)}));buttons[0].setAttribute('aria-pressed','true');</script></html>''', "utf-8")

    # Publish reproducible prompt metadata, omitting author-machine absolute paths.
    provenance = concept_provenance()
    (ATLAS / "concepts/provenance.json").write_text(json.dumps(provenance, ensure_ascii=False, indent=2) + "\n", "utf-8")
    qa = {
        "status": "PASS", "rooms": 48, "floors": 6, "pages": 73,
        "placement_records": placements, "block_ids": 66,
        "pdf_pages_rendered": 73,
        "visual_review": "All five contact sheets reviewed; full-page compatibility and A3 samples checked for legibility and clipping.",
        "layout_checks": "48 rooms: no reserved furniture overlap; two-cell primary aisles; F8 circulation cores clear.",
        "scope": "Design-grid checks only; no claim of Minecraft pathfinding or completed world generation.",
        "pdf": {"file": PDF.name, "bytes": PDF.stat().st_size, "sha256": digest(PDF), "delivered_separately": True},
    }
    (ATLAS / "quality_check.json").write_text(json.dumps(qa, ensure_ascii=False, indent=2) + "\n", "utf-8")
    files = [ATLAS / x for x in ("README.txt", "index.html", "quality_check.json", "block_catalog.json", "page_index.json", "placement_validation.json", "room_placements.json")]
    for folder in ("rooms", "floors", "guides", "csv", "concepts"):
        files += [p for p in sorted((ATLAS / folder).rglob("*")) if p.is_file() and p.name != "generated_images.json"]
    entries = [{"file": p.relative_to(ATLAS).as_posix(), "bytes": p.stat().st_size, "sha256": digest(p)} for p in files]
    manifest = ATLAS / "manifest.json"
    manifest.write_text(json.dumps({"format": 1, "files": entries}, ensure_ascii=False, indent=2) + "\n", "utf-8")
    files.append(manifest)
    ARCHIVE.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(ARCHIVE, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path in files:
            z.write(path, "central-research-institute-interiors-v01/" + path.relative_to(ATLAS).as_posix())
    with zipfile.ZipFile(ARCHIVE) as z:
        assert z.testzip() is None
    receipt = {"file": ARCHIVE.name, "bytes": ARCHIVE.stat().st_size, "sha256": digest(ARCHIVE), "files": len(files), "pdf": qa["pdf"]}
    (ROOT / "dist/central-research-institute-interiors-v01.receipt.json").write_text(json.dumps(receipt, indent=2) + "\n", "utf-8")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
