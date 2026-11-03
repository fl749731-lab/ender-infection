# 末影感染 · Ender Infection

Minecraft **1.20.1 / Forge 47.3.12 / GeckoLib 4.4.9 / Java 17** 阵营 Mod。当前开发版本：**0.11.0-alpha.10**。

感染从母体与扩张节点开始。每个母体拥有独立的生物质储备；召唤、进阶与节点扩张消费储备，土地收益随领土扩大而增加，重复收割同一片土地的收益逐渐下降。

## 必装前置：GeckoLib

**安装末影感染前，必须同时安装 GeckoLib 的 Forge 1.20.1 版本。客户端和服务端都需要安装。**

| 前置模组 | 版本要求 | 已测试版本 | 下载 |
|---|---|---|---|
| GeckoLib | `4.4.9 ≤ 版本 < 5`，对应 Minecraft 1.20.1 / Forge | **4.4.9** | [GeckoLib 4.4.9（Forge 1.20.1）](https://modrinth.com/mod/geckolib/version/RBA7lJaW) |

将 `geckolib-forge-1.20.1-4.4.9.jar` 和本 Mod 的 JAR 放入同一个实例的 `mods` 文件夹；专用服务器也放入其 `mods` 文件夹。GeckoLib 需单独下载，未打包进本 Mod。缺少前置或版本不满足要求时，Forge 会阻止加载并提示依赖错误。

## 晶体装备与开局陨石

共鸣晶、余烬晶和虚空晶各有四件护甲、镐、斧、铲、锄和剑，共 27 件。用对应晶体按原版装备形状合成，工具和剑另用木棍。铁砧可用对应晶体修理。创造栏和物品提示中能看到三套装备及其能力。三套穿戴贴图重绘了头盔、背甲、肩臂、关节和靴子。虚空甲有青色晶脊，共鸣甲有紫色晶纹，余烬甲有橙色熔纹。

| 系列 | 四件套护甲 | 工具 | 剑 |
|---|---|---|---|
| 共鸣晶 | 来自本 Mod 感染单位的伤害降低 20% | 使用适配工具采掘矿石，或站在感染地面上采掘时，速度提高 35% | 对感染单位额外造成 25% 伤害并标记 3 秒 |
| 余烬晶 | 火焰类伤害降低 40% | 在下界使用适配工具时，速度提高 25% | 命中点燃目标 3 秒 |
| 虚空晶 | 摔落伤害减半，弹射物伤害降低 20% | 空中使用适配工具时，采掘速度乘以 3 | 右键抛出末影珍珠，消耗 2 点耐久，冷却 12 秒，按珍珠规则传送 |

共鸣晶与余烬晶工具采用铁镐级采集等级，虚空晶工具采用钻石镐级采集等级。三套的材料来自相应矿石，共鸣系列使用共鸣碎片。

新世界首位玩家进入主世界约 5 秒后，聊天栏提示落点，紫色陨石从附近天空下坠。约 2.2 秒后，陨石以威力 5.5 的爆炸撞击地面，伤害并击退附近生物。在允许地形破坏时，冲击会在自然地面炸出陨石坑，随后优先在坑内生成第一阶段母体。地形破坏遵守 `mobGriefing`。事件保存在世界数据中，飞行和撞击状态会随读档继续。和平难度期间暂停。

可移动的侵蚀和畸变单位会在安全落点之间闪现，追击可见目标或躲避弹射物。冷却中也会免疫箭矢、三叉戟等弹射物伤害。母体、孵化囊和根刺炮台保持扎根，享有同样的弹射物免疫。母体目击被闪避的弹射攻击时仍会学习远程战术。

进度界面新增「末影感染」页，共九项，涵盖见证陨石坠落、取得三种晶体、集齐三套护甲及击杀母体。

## alpha.8 晶矿

| 矿石 | 矿脉起始高度 | 最低镐等级 | 用途 |
|---|---|---|---|
| 共鸣晶矿、深层共鸣晶矿 | 主世界 Y -56 到 48，矿脉在 Y -4 附近更常见 | 铁镐 | 掉落共鸣碎片，用作晶刺弩弹药和抑制粉原料 |
| 余烬晶矿 | 下界地狱岩中，Y 20 到 100 | 铁镐 | 用余烬晶体替代灯板配方的萤石粉，每次做出两块实验室灯板 |
| 虚空晶矿 | 末地外岛高地和中地，Y 32 到 72 | 钻石镐 | 一颗虚空晶体和一个雪球做出六份抑制粉 |

矿脉随新区块自然生成。矿石支持时运和精准采集，普通开采会掉落经验。四种矿石方块和两种晶体可在「末影感染」创造栏找到。

## alpha.7：连通扩张与高处蔓延

节点按生物质储备计时扩张，修复之前每个 tick 都扫描和扣费的问题。连续坡面与裸露崖面都能向上感染；搜索高度随节点范围增长，高台也能成为子节点的落脚点。新前哨按**尚未被节点覆盖的可感染地形和剩余肥力**评分，扣除母节点本身的覆盖，优先向新领土推进。年轻节点可沿相连的旧感染带向外增长，避免被母节点的覆盖困住。

地表优先于树冠维护；前方无可感染地形时不购买空半径。前哨被毁后可付费补建，未加载的子节点保留记录。母体随节点数量保留 16～64 生物质作为扩张周转金；升级也要留下这笔钱。高处感染领土计入收益，土地衰竭与各母体独立储备规则继续保留。

机制、费用、限制和测试方法见 [扩张说明](docs/expansion.md)。使用 `/enderstrategy` 查看附近节点的搜索高度、扩张轮次与下一轮等待时间。

## alpha.6：区块加载修复与导入建筑

修复靠近自然实验室时远处地形空白的问题：大型设备的占位方块在灯光线程读取邻区块，导致区块生成与灯光互相等待。现在光照路径使用固定透明形状，碰撞仍跟随已加载的设备根方块。修复前已在独立噪声世界复现，修复后重新检查原生自然生成。

打开门后仍无法通过的问题也已修复：上下门框从 1.7 像素缩到 1 像素，模型与碰撞同步，净高 **1.875 格**。站立玩家实际移动验证覆盖完整/侵蚀门的四种朝向和六类旧入口。

按用户选择，建筑改用真实的上游结构文件。保留原体量与构造，转换方块和容器；不把导入建筑声明为原创。源文件、映射脚本、作者和许可均保留在 [third_party/architecture](third_party/architecture/README.md)。

| 建筑 | 来源 | 大小（宽×高×长） | 指令 |
|---|---|---|---|
| 废弃工业实验室 | HBM | 19×17×21 | `/enderbuild hbm_laboratory` |
| 废弃工厂 | HBM | 38×41×19 | `/enderbuild hbm_factory` |
| 极地科考站 | HBM | 38×14×24 | `/enderbuild hbm_arctic` |
| 废弃教堂 | Church of Sin | 37×58×70 | `/enderbuild church` |

![白桦林中自然生成的教堂](docs/images/imported_church_natural.png)

![游戏内教堂主厅](docs/images/imported_church_nave.png)

四套建筑跳过水域和大于 5 格高差的陡坡，分别在对应群系尝试自然生成，完整群系和转换说明见 [导入建筑说明](docs/imported-buildings.md)。旧方盒布局已停止自然生成，原有六套 `/enderlab` 调试布局与旧存档中的建筑保留。

手动放置在玩家东侧 64 格开始，会替换建筑空间中的原有方块。每两个 tick 处理一个区块，进度保存到存档；`/enderbuild status` 查看进度，`/enderbuild cancel` 停止后续放置。请在测试世界或空旷区域使用。

**Lua Lab** 已准备本地 `.litematic` 转换与加载入口，当前尚未取得作者下载文件，因此没有打包或声称安装了这套园区。导入方法见 [说明](docs/imported-buildings.md#lua-lab)。其转换件不随公开 JAR 分发。

旧调试布局增加了双向升降平台和金属检修梯，保留 30 种房间源布局、56 个功能区和 9 处破损区域。平台通过玩家传送连接楼层；右键上行、潜行右键下行，顶层右键下行，目标落点堵塞时拒绝传送。已有旧建筑不会自动重建。

## alpha.4 崩溃修复

修复实验室通风口、储柜、门、检修口、观察窗、工作台和手术台的空发光蒙版崩溃。无有效发光像素时跳过发光层；有效晶体发光效果保留。新增资源重载缓存清理，导出器与校验器拒绝空发光蒙版。

客户端验证现在实际执行 GeckoLib 发光纹理注册：33 张有效蒙版注册通过，7 种无发光设备被跳过。此前仅烘焙模型和贴图的检查未覆盖这条崩溃路径。

## 本轮接入

- 19 款怪物设计进入游戏：14 种新专职单位，以及重做的追猎者、棱骨术士、攻城兽、母体与灾变母体。灾变母体对应第五阶段，沿用母体阵营和储备。第一至第四阶段目前共用同一设计并逐级放大，外观区分主要来自体型。
- 新单位使用骨骼模型、逐面 UV、待机、移动、攻击、登场和死亡动画，攻击关键帧与服务端命中时机对应。
- 侦察者标记目标，搬运兽回收材料，晶盾卫士正面减伤，喷吐者发射带毒晶体，祭司治疗与减伤支援，孵化单位按母体储备付费并遵守人口上限。
- 2 套四件式护甲、4 种武器和 8 类掉落材料；包含合成、修理、耐久、冷却和晶刺弩弹药消耗。
- 14 类实验室构件的完整/侵蚀版本，加上 12 件功能与装饰摆件，共 40 个方块。门和储柜可开合，培养舱有动态标本，手术台提供治疗，升降平台连接楼层。
- 四套署名导入建筑进入自然生成；旧六套实验室调试布局保留大厅、中庭、连桥、竖井、破损区域、升降平台和分类补给箱。

## 单位

| 单位 | 主要作用 | 最低母体阶段 |
|---|---|---|
| 潜伏觅食虫 | 近战、传递目击目标 | 1 |
| 孢翼侦察者 | 飞行、目标标记与晶体攻击 | 2 |
| 晶背搬运兽 | 回收感染组织和浓缩生物质并运回母体 | 2 |
| 脉动孵化囊 | 固定孵化阵地，付费生产觅食虫 | 2 |
| 菌丝掘地者 | 近战与目标周围晶刺 | 3 |
| 寄生蛛母 | 近战、付费孵化小型蜘蛛 | 3 |
| 晶盾卫士 | 正面减伤、近战防守 | 3 |
| 裂喉喷吐者 | 带毒晶体投射物 | 3 |
| 跃刃伏击者 | 跃击 | 3 |
| 根刺炮台 | 固定远程晶刺阵地 | 3 |
| 共鸣祭司 | 支援治疗、抗性和晶体攻击 | 4 |
| 破城巨像 | 范围重击与晶刺 | 4 |
| 行巢巨蟹 | 巨型近战、付费孵化 | 5 |
| 深渊掘王 | 高生命值、远程晶刺法阵 | 5 |

专职单位复用狩猎、回防、母体归属和人口限制。低于 64 生物质时，母体优先使用原有低成本兵种；大型单位还需要足够的出生空间。

## 装备

| 物品 | 操作 |
|---|---|
| 棱晶长枪 | 左键近战；右键前方窄角度突刺 |
| 裂地战锤 | 左键近战；右键范围震击 |
| 晶刺弩 | 按住右键至少 15 tick 后松开；消耗 1 个晶体碎片，发射 3 枚晶体 |
| 共鸣法杖 | 右键发射魔法晶体并显示法阵 |
| 晶壳轻甲 | 四件套持续清除本 Mod 的末影侵蚀效果 |
| 母巢重甲 | 四件套濒危时提供临时吸收与本次减伤，20 秒冷却 |

## 建筑调试指令

需要开启作弊或拥有管理员权限。手动放置会替换对应区域的方块。

```mcfunction
/enderbuild list
/enderbuild hbm_laboratory
/enderbuild hbm_factory
/enderbuild hbm_arctic
/enderbuild church
/enderbuild status
/locate structure ender_infection:imported/church
```

新建筑使用原生结构集，40 / 14 区块间距；群系条件仍需满足。已生成的旧区块不会新增建筑。

旧实验室调试命令仍可用：`/enderlab research|swamp|forest|medical|snow|badlands|auto`。其聊天栏给出入口坐标。`hospital` 和 `prison` 分别兼容映射到旧医疗站和采掘设施。

## 安装与 E 盘位置

本机启动入口为 `E:\mc\启动MC.cmd`，Prism Launcher 与游戏实例位于 `E:\mc\Minecraft1201`。账号、存档和启动器数据属于本机文件，不进入 Git 仓库。

编译后的 `ender-infection-0.11.0-alpha.10.jar` 与 `geckolib-forge-1.20.1-4.4.9.jar` 放入同一个 Forge 1.20.1 实例的 `mods` 目录。更新时只保留一个版本的 Ender Infection JAR。模型改变需要重新启动游戏。

## 工程与可编辑资源

| 位置 | 内容 |
|---|---|
| [ender_infection_colored](ender_infection_colored) | Forge / Java 工程 |
| [Java 源码](ender_infection_colored/src/main/java/dev/hui/enderinfection) | 实体、AI、生物质、战斗、实验室与测试 |
| [游戏资源](ender_infection_colored/src/main/resources/assets/ender_infection) | Gecko 模型、动画、贴图、烘焙模型与语言 |
| [结构与配方](ender_infection_colored/src/main/resources/data/ender_infection) | 实验室 NBT、世界生成、掉落和合成 |
| [Blockbench 设计包](colored_units/design_pack_2026-11-02_v2) | 63 个可编辑模型、贴图与设计源脚本 |
| [新增实验室摆件](colored_units/lab_details_v3) | 12 件原创功能与细节模型、贴图 |
| [转换工具](tools) | 导出游戏资源、生成结构与验证资源 |
| [验证结果](ender_infection_colored/verification/RESULTS.txt) | 实际构建、GameTest 和客户端加载记录 |

Blockbench 原件保留完整的逐面 UV。墙地使用 Forge 烘焙模型，大型实验设备使用 GeckoLib 方块实体；实验室整栋场景转换为原生方块结构。

alpha.10 的矿石、装备、闪现和陨石专项 **4/4** 通过，qegg Forge 构建通过。陨石测试实际炸开岩石，验证了坑内生成、伤害、击退和读档。护甲使用正反面贴图预览检查。历次记录见 [RESULTS.txt](ender_infection_colored/verification/RESULTS.txt)。

## 构建与验证

使用 JDK 17：

```powershell
cd ender_infection_colored
.\gradlew.bat build
.\gradlew.bat runGameTestServer
.\gradlew.bat runGameTestServer -PexpansionChecks
.\gradlew.bat runGameTestServer -PoreChecks
.\gradlew.bat runClient -PdesignSmoke
.\gradlew.bat runClient -ParchitectureCheck
.\gradlew.bat runClient -PnaturalLabCheck -PnaturalBiome=birch_forest -PnaturalBuilding=imported/church
```

首次构建需要联网；已有依赖缓存可添加 `--offline`。产物位于 `ender_infection_colored/build/libs`。GameTest 使用 `build` 下的新世界；客户端资源检查使用独立开发目录。

重新导出模型和数据需要 Python、Pillow 与 NumPy：

```powershell
python tools/export_design_assets.py
python tools/build_design_data.py
python tools/import_architecture.py
python tools/validate_design_assets.py
python tools/validate_laboratory.py
```

项目使用 **qegg** 管理变更记录和发布验证：

```powershell
qegg check
qegg verify
qegg plan
```

变更记录位于 [.changes](.changes)，验证命令在 [.quailegg.toml](.quailegg.toml)。当前版本仍处于 alpha，长期平衡与多人表现需要继续实玩验证。
