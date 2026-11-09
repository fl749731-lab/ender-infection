# 中央研究所：游戏内建筑

适用于主模组 **0.12.0-alpha.7 / layout v5**。Forge 1.20.1 / Java 17 与 NeoForge 1.21.1 / Java 21 使用同一布局、NBT 和战利品数据。[安装包与更新说明](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.7)。完整建筑最初于 alpha.3 接入，alpha.2 的 JAR 不含整栋研究所。

场地 120×104，地基至中庭顶共 55 格；主楼 104×80。地上三层、地下三层，共 48 间有内饰的房间，保留原有的小型实验室及导入建筑。

alpha.7 使用 layout v5，在 B3 / F8 保留内饰并增加收容监管体启动终端与两座抑制台，操作与实际坐标见 [F8 Boss 指南](institute-boss.md)。旧 v4 研究所仍可完成 E8，但不会自动增加 F8 终端。

| 楼层 | 相对首层地板 Y | 房间与用途 |
|---|---:|---|
| G1 | 0 | A1–A8：门卫、登记、医疗观察、消毒更衣、装卸收发、接待、入口大厅、器材储藏 |
| G2 | +7 | B1–B8：菌体、显微、晶体、组织培养、试剂、办公室、档案、湿法分析 |
| G3 | +14 | C1–C8：通讯、总控、安保、宿舍、餐厅、植物温室、记录、菌体温室 |
| B1 | −7 | D1–D8：配电、应急电源、服务器、冷却、过滤、备件、仓库、维修 |
| B2 | −14 | E1–E8：低温封存、隔离监控、收容、解剖、培养舱、样本、气闸、事故实验室 |
| B3 | −25 | F1–F8：核心控制、裂隙样本、晶体供能、冷却应急、档案、最终气闸、污染隔离、裂隙大厅 |

双楼梯、连桥、中央中庭与货梯连接楼层。对货梯右键上行，潜行右键下行；到顶后普通右键返回下方。实验室门与隐私隔帘可开合，储柜和抽屉打开真实箱子界面；每间房另有独立分类补给桶。供电、门禁、样本研究、[E8 收容事故](containment-incident.md)和 [F8 Boss](institute-boss.md)已落地；现有旧建筑不会自动覆盖。

## 储物家具与内饰

13 类储物家具共用原版 27 格箱子界面：储柜及侵蚀储柜、工作台及侵蚀工作台抽屉、冷柜、货箱、货架、洗手盆下柜、玻璃展示柜、低温柜、服务器机架、控制台和推车。点击多方块家具的任一有效部件，打开的是同一个根库存；多人同时存取不会生成独立副本。柜门和抽屉随使用开合，物品持久保存，破坏根或部件时内容只掉落一次。

新生成的中央模板含 1417 个按房型分配战利品的储物根，另有 48 个独立补给桶；六种旧小站模板合计含 368 个战利品储柜。采用原版延迟战利品：首次使用后保存实际物品，后续打开和读档不重新抽取，漏斗与破坏按原版容器规则处理。玩家自行放置的家具为空。更新前已经摆放、没有战利品标记的家具可用作储物，但不会自动补刷随机物资。

玻璃展示柜、低温柜、机架、控制台、推车与隔帘提供对应外形；显微镜、离心机、冷却泵和工作灯补齐专用设备，工作灯发出 14 级方块光。五色细墙带和房间标识替代整格深色条与不明确的房间入口，设备按用途重新布置并保留站立通路。监视器和普通样本小架等没有储柜结构的摆件不提供虚构库存。研究所的受管供电与加工操作见[操作指南](institute-operations.md)；E8 冷却泵另承担事故维修交互。

E5 培养舱阵列恢复六座大型培养舱，A3 医疗观察室配置四张观察床；两处按原房间用途布置，保留设备前方和床边的站立通路。

### 工作区建筑优化（architecture-v1）

当前开发迭代已覆盖全部 48 间房，增加符合房型的医疗、办公、档案、住宿、餐饮、种植、机务和仓储陈设，总计 2645 个储物家具、76 个床半块。最底层 F7 新增第二阶段母体与真实绑定的裂隙锚点：完整收容时暂停孵化和扩张，破舱后恢复母体行为，死亡与读档不重复生成。此功能目前仅接入和验证 NeoForge 1.21.1；房间预览和具体规则见[全房间与母体收容舱](../previews/institute-rooms-v2/README.md)。下段保留首轮入口与科研区改造说明。

当前开发资源在 v5 布局上增加宽门廊、发光入口标识、外墙分段翼片、分层遮阳檐与阶梯式屋顶设备罩；中庭增加镂空悬挂灯架和两组等候座椅，A7 补充四组等候座椅。科研层采用浅灰地面和局部浅蓝工作区，B1、B2、B4、B5、B8 共补充 22 组带照明罩的双面实验台。

本轮仅验证 NeoForge 1.21.1，保持原有场地、48 间房、楼梯、货梯及任务设施坐标；保留原 1417 个储物家具和 48 个补给桶，新增 88 个台下冷柜。新增结构由建筑 MCP 制作，源文件与可重建脚本保存在 `tools/institute_architecture` 和 `tools/create_institute_architecture_mcp.py`；正常生成只读取提交的 NBT，不要求安装 MCP。新版只影响新生成或新施工的建筑，已有建筑不会自动改写。此节描述工作区开发资源，不代表已更新 alpha.7 的公开发行附件。

自然生成候选位于平原、向日葵平原、沙漠、热带草原、高原和草甸。结构集采用 64 区块间距、32 区块分隔；候选还需通过覆盖整片场地的干地、高度与不超过 8 格高差检查，因此不会在每个候选位置都出现。只影响新生成区块，旧世界已经探索的区域不自动增加研究所。

59 处紫晶单元使用灯板、海晶灯及原版无碰撞光源补光。额外光源位于距晶体两步的空气格，保留所有相邻空气和芽生长空间；五处玻璃展柜采用两步折线路径。验收测量真实暴露面的方块光照，要求至少达到 12，不以光源距离清单代替实际光照。

## 管理员检查与放置

`origin` 是整个结构包围盒的最小角，首层地板在 `originY + 28`。例如想让首层地板位于 Y=64，可将 originY 设为 36。首版固定朝向，不提供旋转参数。

```mcfunction
/enderinstitute previewcentral 0 36 0
/enderinstitute status
/enderinstitute cancel
/enderinstitute build 0 36 0
/locate structure ender_infection:central_institute
```

`previewcentral` 逐区块检查目标区域，不写入方块；`build` 先完成全场检查，再逐区块施工。两者都只处理已加载区块，遇到未加载区块会暂停并报告坐标，需要玩家靠近加载后继续。放置会替换允许的被动地形和基础建材；已有方块实体、容器、实验室设备占位方块、活动红石及未知外部方块会阻止施工；检查覆盖整个包围盒，包括室外保留区域。检查失败后先查看状态及冲突位置。取消保留已经完成的部分，不回滚。正常保存会保留施工进度；崩溃后发现当前施工区块存在设备或容器时会停止，避免重置物品。

既有 `/enderinstitute preview`、`place` 仍用于公共 API 注册的房间；`/enderlab` 仍用于原先的小型调试布局。

## 资源与验证

建筑首次落地的 alpha.3 验收为 115 项必需测试和 150 张截图；这是历史建筑验收记录。当前 alpha.6 通过 163 项测试和 32 张功能／模型截图，见[当前验收](../previews/incident-alpha6/README.md)。最终 JAR 校验和、布局与图像 SHA256 见发布验证清单。 对应图像与回执见 [运行实景](../previews/central-institute-runtime/README.md)，完整原图见 [alpha.3 实景图包](https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.3/central-institute-runtime-0.12.0-alpha.3.zip)。

`tools/generate_central_institute.py` 从房间设计生成整体体素图，再拆为 56 个结构模板。模板包含地下室清空用的空气方块、家具完整占位、床的两半和容器数据。布局入口为 `data/ender_infection/central_institute.json`；NeoForge 数据由共享资源转换脚本生成。

```text
python tools/generate_central_institute.py
python tools/verify_central_institute.py
python tools/build_dual_version.py --clean --gametests
```

完整建筑原生测试独立运行，避免与其他测试场地重叠：在相应工程中执行 `runGameTestServer -PcentralInstituteChecks`。画面检查使用隔离的新世界：`runClient -PcentralInstituteVisualCheck`。不使用玩家现有实例或存档作为测试目录。

客户端通过要求日志出现自然生成与全部 61 个机位完成标记；仅有 Gradle `BUILD SUCCESSFUL` 不代表验收成功。可用 `tools/verify_central_institute_visual.py <日志> <screenshots目录>` 检查完整性。完整截图保存在各工程的 `build/central-institute-visual-check/screenshots`，包括 48 间房、六层、外观、楼梯及夜景。

实景总览及逐层房间表由两个版本的原生客户端拍摄。alpha.2 的设计阶段资料继续保留：[完整图包](https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.2/central-research-institute-interiors-v01.zip)、[73 页 PDF](https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.2/central-institute-interior-atlas-v01.pdf)。历史图包不包含本次原生客户端验收结果。
