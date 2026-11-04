末影裂隙篇：可编辑模型包 / 2026-11-04

模型由 Blockbench 5.2.2 中的 Blockbench MCP v1.10.1 原生创建和保存。
骨骼、方块体、逐面 UV、贴图和动画均在实际编辑器中建立，编辑记录见 verification。
models/*.bbmodel 是最终原件；双击或在 Blockbench 中打开即可编辑。
纹理内嵌于模型，同时在 textures/ 中提供独立 PNG。

模型                       方块体   骨骼   基础动画
初期感染末影人                59      16    idle / walk / attack / phase
畸变末影人                   153      22    idle / walk / attack / phase
晶壳锚卫                     185      23    idle / walk / attack / deploy

锚卫的 deploy 展示甲瓣开启、晶核暴露与尾部锚定；walk 为四足移动/重新部署原型。
模型包内动画保留原始制作时序；v0.12.0-alpha.1 导出时已匹配游戏服务端攻击命中帧。
紫晶和眼睛使用独立 emissive 纹理，全部对应面保留逐面 UV。
游戏已接入发光图，实体高度分别校准到 3.2 / 3.6 / 2.75 格。

建模参考：previews/ender_rift_concepts_2026-11-04/ 下的三张设计图。
实际视口预览：previews/ender_rift_models_2026-11-04/。
ZIP 中另附 concepts/ 与 previews/，分别为设计参考与实际 Blockbench 视口图。

结构检查：所有方块体为正尺寸；UV 在 512x512 画布内；骨骼层级与动画绑定完整。
静止姿态的正面积同向共面重叠检查为 0；该检查不等同于所有动画帧的碰撞测试。
实际检查包含正交正/侧/背、透视，以及行走、攻击、相位/开甲采样，共 21 帧。
生成图中的局部细节已经统一到三维模型；这是方块体实现，不能视为逐像素复刻。

本包已接入 v0.12.0-alpha.1，运行时导出和检查见 tools/export_rift_assets.py 与 tools/validate_rift_runtime.py。
游戏内体验与预发布范围见 ender_infection_colored/RIFT_PREVIEW.txt。
后续修改请继续在 Blockbench MCP 中编辑并保存原件。
tools/blockbench-mcp/build_rift_models.* 和 refine_*.py 是一次性制作记录，勿重复应用相对修改。
