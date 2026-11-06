# 公开模型改造：第一批

本目录包含不可变的作者源文件、通过 Blockbench MCP 修改的工程、MCP 原生导出及校验记录。面向 Forge 1.20.1 / NeoForge 1.21.1 的 GeckoLib 4，外观与动作优先。

| 游戏内角色 | 底模与作者 | 本轮改造 |
|---|---|---|
| 共鸣祭司 | [Skeleton Lord — @anim](https://blockbenchworkshop.com/model/anim/skeleton-lord) | 缩短并固定法杖握点；共振晶冠与胸前晶簇；保留披风、关节和施法动作；对齐第 16 tick 施法；感染、死亡与接地补偿 |
| 晶背搬运兽 | [Leathern Drake — Breathfang / Drageon DB](https://sketchfab.com/3d-models/leathern-drake-blockbench-021865367838492a9839c64c6f3cb984) | 保留四足、牙齿、骨甲、绑带与鞍架；缩短尾部；增加紫晶负载；恢复作者导出的五节尾部 IK 动作；攻击、感染、死亡与接地补偿 |

两项均按作品页的 **CC BY 4.0** 使用，署名、作品链接、许可链接和修改说明也进入两版 JAR 的 `assets/ender_infection/third_party/model_credits.json`。许可文本见 [Creative Commons Attribution 4.0](https://creativecommons.org/licenses/by/4.0/)。上游作品的许可独立于本仓库其他代码及资源的许可。

- `source/skeleton_lord.bbmodel` 来自作品页公开且完整的预览模型 JSON；不是需要登录的下载接口。与作品页列出的 286 方块、88 骨骼、9 动作相符。原始公开地址记录在下方。
- `source/leathern_drake.bbmodel` 和 `source/leathern_drake_geckolib.animation.json` 来自作者在作品页提供的 [Blockbench / GeckoLib 包](https://www.mediafire.com/file/a0cf1lycqb4gh1e/Leathern_Drake_Blockbench_Model.zip/file)。尾部 IK 使用作者已烘焙的数值通道，未把 IK 控制器直接丢进游戏。
- `models/` 是修改后的可编辑工程。原始源文件保持不变。上游的 AI 使用情况不作额外推断；本轮修改由 Codex 辅助完成。
- `mcp_exports/` 来自 Blockbench MCP 的几何与动画导出。采用的待机、行走和施法数值通道保留 Catmull-Rom；未采用源文件其他动作中的 Bezier 通道或未支持的事件通道。
- 待机与行走循环接点已修正；两模型四条循环的首尾骨骼通道差均为零。接地修正后的 24 Hz 采样仍通过。
- `export-lock.json` 将改造工程与导出绑定。工程变化后必须重新通过 MCP 导出、验证并更新锁定摘要，旧导出不能继续打包。
- 运行时转换只拼接原始像素、处理有效逻辑 UV 尺寸、裁掉未使用的材料区、缩放几何／位移动画并补齐动画名前缀。没有把模型拍成贴图替代立体结构。
- 紫晶材质全透明度覆盖自发光；搬运兽原有皮肤、骨甲和金属不发光。自发光不产生动态世界光源。

公开预览源：[Skeleton Lord viewer JSON](https://blockbenchworkshop.com/cdn/previews/y2BntgWXrPICFijrO0OqZ3jOhNPzLLEE/15506186-efdc-4be1-9c31-50125e63d3a8.json?v=5)。原始 SHA-256：`bb3d8b25927d875708af27a66068572ee75555aff213f922e8cf8a17ed11b68e`。Drake 原始 ZIP SHA-256：`cfac3b438896f6c473dc1f1d5157c76b6a5bba81ae5df0432c66f18a6cc91903`。

资源转换：`python tools/export_design_assets.py --units carrier,resonance_priest`；独立验证：`python tools/export_public_model_adaptations.py --verify`。随后通过 `tools/sync_neoforge_resources.py` 同步第二版，不手改 Neo 生成目录。

完整怪物、感染阶段、装备和特效评审见[统一评审说明](../../docs/model-visual-review.md)。
