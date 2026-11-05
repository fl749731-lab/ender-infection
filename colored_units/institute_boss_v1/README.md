# 收容监管体 · 可编辑资源

这是研究所 Boss 的可编辑开发资产，已通过对应导出接入本地 alpha.7，未包含在公开 alpha.6 发布包中。玩法见[操作指南](../../docs/institute-boss.md)，效果图与检查见[预览索引](../../previews/institute-boss-alpha7/README.md)。

## 文件

| 文件 | 用途 |
|---|---|
| `containment_warden.bbmodel` | Blockbench Headless MCP 创建、修改和绑定动画的可编辑源 |
| `export/containment_warden.geo.json` | MCP 导出的骨骼几何 |
| `export/containment_warden.animation.json` | MCP 导出的七段骨骼动画 |
| `export/containment_warden.png` | 基础颜色贴图，256×256 |
| `export/containment_warden_glow.png` | 相同 UV 的透明发光遮罩，256×256 |

## 建模规格

目标为桌面版 Java Minecraft 实体，Forge 1.20.1 与 NeoForge 1.21.1 共用美术资源，后续分别接入 GeckoLib 4。单场景以一个 Boss 为预算，优先保证设计外观。

- 128 个方块、21 根骨骼，三角形上限 1536。
- 16 模型单位为一格，站立约 3.4 格高。
- 每面 UV 按实际长宽分配，目标密度为每模型单位 2 像素，约 32 像素／格；小面有取整。
- 不透明颜色贴图加独立自发光层，不依赖法线、PBR 或透明混合。
- 装甲划痕、警示条和编号绘在颜色图中；护臂内槽、分离胸甲和外轮廓使用几何。
- 晶体所有明暗面均进入发光遮罩，不以亮度阈值漏掉暗面。
- 使用刚性骨骼动画，仅数值位置、旋转和缩放，线性插值；没有 Molang、Bezier、声音、粒子或时间轴事件。

## 动画契约

完整名称统一为 `animation.containment_warden.<名称>`。

| 名称 | 秒数 | 播放 | 预定用途 |
|---|---|---|---|
| `idle` | 3 | 循环 | 原地待机，不整体抬高躯干 |
| `walk` | 1.2 | 循环 | 左右腿和双臂交替 |
| `claw` | 1.8 | 一次 | 0.45 秒蓄力，0.85 秒出手 |
| `stomp` | 2.4 | 一次 | 抬右腿，1.1 秒落地 |
| `burst` | 2.6 | 一次 | 开甲，1.0／1.3／1.6 秒核心脉冲 |
| `exposed` | 3 | 循环 | 胸甲保持展开、核心呼吸 |
| `death` | 3 | 一次 | 屈膝倒地 |

MCP 的基础 Bedrock 导出用于这里的数值线性变换子集。导出本身不携带 Java 事件、控制器、渲染层或攻击判定；这些现由模组中的 `ContainmentWarden`、`ContainmentWardenRenderer` 和 `WardenEncounter` 实现。模型缺少 `geckolib_modid` 的编辑器项目元数据，模型检查仍有该条警告，游戏中的导出文件不依赖此字段。

运行时几何、动画与两张贴图以 Forge `src/main/resources/assets/ender_infection` 为源，NeoForge 由仓库同步工具生成。发光遮罩运行时名为 `containment_warden_glowmask.png`，遵循项目现有 GeckoLib 发光层约定。

操作生成器位于 [几何与 UV 规划](../../tools/blockbench-mcp/plan_containment_warden.py)和[动画规划](../../tools/blockbench-mcp/animate_containment_warden.py)，只产生操作 JSON／纹理，不直接改写 `.bbmodel`。实际模型变更经 MCP 批量提交并校验版本号。

导出契约检查：在仓库根运行 `python tools/blockbench-mcp/verify_containment_warden.py`。它核对骨骼、动画时间与变换、UV 密度、所有晶体面发光覆盖以及文件摘要；不代替双版本原生验收。
