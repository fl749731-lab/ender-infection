# 公开 Minecraft 模型资源

2026-11-06 核实。优先选择能查看完整立体模型、提供 `.bbmodel`／GeckoLib 文件并明确授权的资源。公开可浏览、免费下载、允许修改及允许随模组再分发是不同条件，以每个作品页为准。

| 网站 | 适合找什么 | 已确认的入口与限制 |
|---|---|---|
| [Blockbench Workshop 免费区](https://blockbenchworkshop.com/models/free) | Minecraft 方块生物、Boss、设备与家具 | 网页可旋转模型及切换动画，列出元素、骨骼、动画和授权；有 CC0、CC BY 等筛选。部分下载需要登录 |
| [Sketchfab：Leathern Drake 示例](https://sketchfab.com/3d-models/leathern-drake-blockbench-021865367838492a9839c64c6f3cb984) | 完整立体轮廓、低多边形生物和动画参考 | 作者明确提供 Blockbench／GeckoLib 下载；其他作品不能仅凭“Download”推定包含 `.bbmodel` |
| [itch.io 的免费 Blockbench 资源](https://itch.io/game-assets/newest/free/tag-blockbench) | 作者公开的源模型与素材包 | 逐包检查格式、骨骼和授权，部分只有通用 FBX／GLB |
| [MCModels](https://mcmodels.net/models) | 成熟 Boss、怪物、动作与服务器模型参考 | 以付费市场为主，有免费条目；ModelEngine 等服务器包仍需转换到本项目的 GeckoLib 实体 |
| [Blockbench 官方作品画廊](https://blockbench.net/gallery/) | 高质量体块、材质分区及场景构图 | 展示用途，不默认提供源模型或授权复用 |

## 已核实的具体候选

| 模型 | 页面所列文件与动作 | 授权与适合用途 |
|---|---|---|
| [Skeleton Lord，@anim](https://blockbenchworkshop.com/model/anim/skeleton-lord) | 通用 Blockbench 模型，286 元素、88 骨骼、9 动画、5 贴图；含 idle、walk、run、spell knockback | CC BY 4.0，修改和再分发需署名、链接授权并说明改动。适合研究感染施法者的大体块、非对称肩部、披风及持杖动作 |
| [Ender Whale，@logan_kant](https://blockbenchworkshop.com/model/logan_kant/ender-whale) | GeckoLib 模型，55 元素、37 骨骼、6 动画；含漂浮、游动、鸣唱、翻滚、裂隙下潜和出现 | 页面标注 CC0、人工创作。适合末影空域生态或大型环境生物；尚未在本模组导入 |
| [Leathern Drake，Breathfang / Drageon DB](https://sketchfab.com/3d-models/leathern-drake-blockbench-021865367838492a9839c64c6f3cb984) | 作者说明有 18 动画、4 种贴图，并提供 Blockbench／GeckoLib 文件链接 | CC BY，保留作者署名。适合检查四足、颈尾、翼和咬击／喷吐动作的配合 |
| [Dungeon Starter，1CAMOU](https://1camou.itch.io/dungeon-starter-blockimagine) | 30 方块模型、25 个带骨骼动画，含 `.bbmodel`、GLB 与 PNG；有骷髅、兽人、宝箱怪等 | CC0；作者披露 AI 生成后人工整理。可作为可编辑原型，质量仍需逐只检查 |

本轮已在网页打开 Skeleton Lord 的立体预览并播放 `spell knockback`，检查头肩、披风、持杖和晶体部分的立体关系。其余候选核实到作者页面的格式、动画与授权信息，尚未逐一下载验骨骼或测试导出。Skeleton Lord 的匿名下载端点返回 401，因此没有取得完整文件，也没有据此宣称已完成转换。

后续采用模型时，应先检查正／侧／背面、关节枢轴、骨骼层级、动画命中时点及 UV，再决定是否修改。外部模型目前没有替换监管体或进入任何 JAR。

灾变则继续作为形体与特效参考：已阅读末影守卫的模型构造、父子骨骼与关键帧实现。其原始美术素材和源码授权不同于上述 CC0／CC BY 候选，不能把公开仓库直接当作可任意改色分发的资源库。见[本轮灾变特效对照](../previews/containment-warden-vfx-v1/cataclysm-reference.md)。
