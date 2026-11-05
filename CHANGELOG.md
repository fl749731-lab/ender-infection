# Changelog

## v0.12.0-alpha.5 - 2026-11-05

- 两版同步区分四种怪物的攻击节奏：破城巨像命中／总长为 22／58 tick，晶盾卫士为 18／44 tick，裂喉喷吐者为 24／52 tick，孢翼侦察者为 14／36 tick；其他十种设计单位保持 16／40。巨像冲刺从第 12 tick 开始，喷吐者与侦察者保留命中帧释放三弹齐射。
- 巨像恢复层叠骨甲并修正低伏裂骨面部；孢翼修正扇形翼膜、短吻、单主尾和后腹孢囊。盾卫与喷吐者恢复原有外观，保留本轮攻击骨骼和动作。四种原生攻击动画沿用实际秒数，导出校验命中关键帧与总长；紫晶暗面、尖端及原色继续完整自发光。
- 细化现有发电机的静态风扇护罩、保险丝检修窗和侧面接口，以及配电控制器的两区操作标签。发电机显示修复就绪状态，配电状态灯表示服务区供电；收容区一行说明操作方式，不表示独立实时灯态。
- 两版修复新地表扩张耗尽整轮预算、导致已开始侵蚀的树干长期无法继续转换的问题；树干加入已有有界顺序扫描，仍共用每轮 3 点预算。NeoForge 修复未挂入世界的独立节点保存 NBT 时的空世界引用，并补齐 `-PaiChecks` 测试入口。
- 增加四单位受限导出及两版独立截图入口，合并保留其他模型的导出记录。
- 本轮本地增量验收通过 269 项原生测试（Forge 98 + 裂隙／AI 36，NeoForge 99 + 裂隙／AI 36）及 76 张客户端截图（怪物姿态 32、暗室发光 16、研究所操作 20、设备细节 8），两版 `build` 与 `apiDevJar` 成功。记录见[本地验收材料](previews/model-polish-alpha5/README.md)。完整双版本干净构建和 16 组原生测试已通过，共 147 项必需测试；附属示例同步通过构建与集成测试。发布工具和工作流同步升级至 qegg v0.12.0。

## v0.12.0-alpha.4 - 2026-11-05

- 为两版中央研究所追加修复电源、服务区/收容区配电、门禁卡、显微分析、离心分离及一次性核心档案任务。四个操作点位于 D2、D1、A7、F5，设施状态与加工进度保存到存档，多人共享设施进度；修复成本为保险丝 ×1、铜锭 ×4、红石 ×4，配电控制器主手普通右键切换服务区，主手空手潜行右键切换收容区。
- 新增 7 种任务物品及起步补给；加工设备使用 3 个机器槽与 36 个玩家背包槽，断电或产物槽满时暂停。档案材料与奖励一次性原子结算，背包放不下时不扣材料、不掉物。
- 新布局管理 881 盏分区灯、40 个受管门和 42 台加工设备，外侧持卡并供电后可开门，内侧保留手动应急；120 处常亮光源及 59 处紫晶表面补光持续保留。
- 补充原生两格睡眠的医疗观察床、轨道隔帘、拼接等候沙发和薄桌面工作垫。保留原有 13 类储物库存与战利品规则；提供 25 份可编辑家具模型，其中本轮新增 7 份 `.bbmodel`。
- API v1 追加供电状态查询、可关闭的事件订阅与加工配方注册，公共契约保持 Java 17；双版附属示例追加纸张复制配方，当前示例最低依赖 alpha.4，旧 v1 附属保持兼容。
- layout v3 适用于新生成或新施工建筑；已有 alpha.3 研究所不会自动重建，玩家摆放的机器不自动绑定或取得虚拟供电。收容事故事件与研究所 Boss 战仍未实现。操作指南见 [研究所供电与样本研究](docs/institute-operations.md)。

<!-- alpha4-validation -->
alpha.4 已完成完整双版干净构建与 16 组原生测试，共 139 项必需测试通过（含两版附属各 2 项）；两版独立功能场景完成 20 张真实交互截图验收。截图与验证范围见[功能验收记录](previews/institute-operations-alpha4/README.md)。公开预发布版见 [alpha.4 发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.4)，稳定版保持 0.11.0。

## v0.12.0-alpha.3 - 2026-11-05

### 新增内容

- 将六层、48 间中央研究所图纸组装为游戏内原生结构，加入双楼梯、货梯、中庭连桥、地下室净空、分类战利品及独立自然生成入口，两版同步。
- 13 类储物家具接入 27 格原版箱子界面、持久库存与一次性战利品；中央模板 1417 个储柜、六种旧小站模板合计 368 个，另保留中央 48 个补给桶。玩家摆放为空，旧世界未标记家具不补刷随机物资。
- 多人共用同一根库存，柜门与抽屉按使用人数开合；根或占位部件破坏只掉落一次，区块更新不广播库存。
- 新增展示柜、低温柜、机架、控制台、推车、隔帘、显微镜、离心机、冷却泵及工作灯，补齐五色细墙带和房间标识，调整设备布局与通路。
- 新增大型研究所放置前检查与可保存的分区块施工队列，已有容器和设备会阻止覆盖，缺少已加载区块时暂停。

### 修复与光照

- E5 培养舱阵列恢复六座大型培养舱，A3 医疗观察室配置四张观察床，纠正房间用途与实际设备不符。
- 修正 C3、D3 门内通路和 B3 核心大厅侵入竖井的布置，新增最终 NBT、完整建筑及客户端验证入口。
- 两处入口适配 8 格地形高差；紫晶组合加入不占用长芽空间的补光，原生验证实际照亮 59 处晶体表面。
- 修正五处补给容器的房内通路，并为禁邻居更新的结构放置预先写入报警灯通电状态。

### 验证与范围

- 本次发行执行完整双版本干净构建，实际 115 项必需原生测试全部通过；四个原生客户端完成 150 张截图验收：122 张中央研究所实景，加 28 张真实库存界面及开柜模型。最终 JAR 校验和、布局与图像 SHA256 见发布验证清单。
- 继续提供 Forge 1.20.1 / Java 17 和 NeoForge 1.21.1 / Java 21 两份 JAR，共用研究所 API 与美术数据；保留 alpha.2 的设计图包和 PDF 历史附件。
- 本版完成建筑、补给、通行、光照与自然生成；研究所电网系统、收容事件和研究所 Boss 战尚未实现。

## v0.12.0-alpha.2 - 2026-11-04

### Features

- 保留 Forge Minecraft 1.20.1，新增 NeoForge Minecraft 1.21.1；两版共用版本号、模型贴图资源和研究所 API 源码。
- 新增研究所 API v1：注册房间与连接口、预览和显式放置 NBT 模板、分发设备交互；提供带内饰分析室及可开关发光控制台的双版附属 Mod 示例。
- 提供中央研究所六层、48 间详细内饰与方块坐标，完整图包和 73 页 PDF 随 Release 发布。建筑主体仍待组装及自然生成接入。

### Bug Fixes

- 两版同步修复运送单位停在母体交付范围外、宽体近战单位在攻击范围外停止，以及射手过高瞄准和友军弹道间隙不足的问题。
- 适配 1.21 数据目录、进度图标、丝触附魔条件、草贴图与盔甲接口。
- Forge 原生测试采用固定种子超平坦隔离世界，避免地下落砂干扰战斗回归；发布检查同时构建和测试两版及附属示例。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.12.0-alpha.1...v0.12.0-alpha.2

<!-- qegg:release 0.11.0 -->
## v0.11.0 - 2026-11-04

### Features

- [`8eec4e0`](https://github.com/fl749731-lab/ender-infection/commit/8eec4e05af7e6ccf28bd83e07996b2a026ead594): Fix the natural laboratory chunk-lighting deadlock and open-door clearance. Import attributed HBM laboratory, ruined factory and arctic station structures, plus the MIT Church of Sin; preserve upstream architecture, licenses and source NBTs. Add chunk-bounded, resumable /enderbuild placement and optional local Lua Lab import. Stop naturally generating the old box-shaped laboratories while retaining six debug layouts, lift platforms and metal service ladders. GeckoLib 4.4.9 for Forge 1.20.1 remains required: https://modrinth.com/mod/geckolib/version/RBA7lJaW
- [`4d1dd4a`](https://github.com/fl749731-lab/ender-infection/commit/4d1dd4ad34ec0db5c477467f1fa69292c52f3a7d): 为共鸣、余烬和虚空三套晶甲加入立体肩甲与背晶。按原始设计图加深 14 个怪物的轮廓，补上孵化舱、环形骨颚和破边翼膜，并细化侦察、搬运、回防、远程牵制与突袭等行动。
- [`91d09c9`](https://github.com/fl749731-lab/ender-infection/commit/91d09c9ae682bb8b5f267ba9e8d4138b5b6770b9): 为共鸣晶、余烬晶和虚空晶新增三套装备。每套包括四件护甲、四种工具和一把剑。共鸣装备强化抗感染和矿石开采，余烬装备强化下界采掘和防火，虚空装备强化空中作业和末影机动。

新增九项末影感染进度。新世界开局以坠落陨石引出母体。早期侵蚀与畸变单位免疫弹射物，移动单位可闪现追击与闪避。
- [`67e1a1a`](https://github.com/fl749731-lab/ender-infection/commit/67e1a1af0a141d80ab092bce1ff6c9e44bcb6320): 新增主世界共鸣晶矿及深板岩变种、下界余烬晶矿和末地外岛虚空晶矿。矿石使用独立的像素贴图与自然矿脉，支持时运、精准采集和经验。共鸣碎片可供晶刺弩使用，余烬晶体用于实验室灯板，虚空晶体用于抑制粉。
- [`21d996c`](https://github.com/fl749731-lab/ender-infection/commit/21d996c0582047b59bec07b50b76f25e2045e3ff): Add ten original editable room-detail models, themed loot, functional room partitions, a fitted entrance, dynamic part collision and native-world visual checks. Reference actual Spore structures without copying assets.
- [`8019a83`](https://github.com/fl749731-lab/ender-infection/commit/8019a83a7a52861157d28192a2a8c78dec6c29ad): 接入 63 个 Blockbench 设计源：14 个新专职单位、母体及攻城单位重做、两套护甲、四种武器、掉落材料与 28 个实验室方块；新增四层原生研究实验室、资源导出、73 项 GameTest 和客户端资源验证，更新仓库说明与 E 盘启动位置。
- [`275b564`](https://github.com/fl749731-lab/ender-infection/commit/275b564425159c1c08c0c90ce40d33463b393a7c): 实验室重做为有细节的封闭建筑：加厚地基、四角活体组织立柱、两层染色玻璃窗、晶体檐口与带肋屋顶、门廊与天线，内部三间房各有操作台、培养罐、灵魂灯与两个战利品箱；同时把出现概率降到 1/600 区块。
- [`f736b96`](https://github.com/fl749731-lab/ender-infection/commit/f736b96e9f2730960f0fe151229934612a88b706): 按真菌的做法把实验室拆成三款并按群系投放：蘑菇岛=白瓷砖研究站、平原=病房、丛林=牢房；新增 grid 放置修饰符（32/10），彻底解决实验室扎堆。

### Bug Fixes

- [`f44e1a4`](https://github.com/fl749731-lab/ender-infection/commit/f44e1a434e9ac8c97f36032a0f35bc8e05c12cce): 修复节点忽略扩张计时、只搜索上方三格和偏向旧覆盖的问题。新增连通坡面与崖面攀升、高台前哨、旧感染带接力、按未覆盖地形与肥力选址、付费补建和旧节点记录兼容；半径只为可用前沿付费，母体升级/和平孵化保留网络周转金，高地收入与扩张搜索高度一致。增加原生 Forge 扩张回归与 /enderstrategy 诊断。
- [`2eecf5a`](https://github.com/fl749731-lab/ender-infection/commit/2eecf5a186491bc774fc39da5e7b8462534eb0ea): 按生态、战斗和巨兽设计图重建 14 种怪物的主体轮廓、骨甲与功能器官，保留紫晶自发光、原有体型高度和攻击时序，并加入正反面与攻击姿态的实机检查。

通过 Blockbench MCP 原生编辑与保存进一步细化分层骨甲、圆润囊体、低伏头部和眼窝；调整喷吐者前倾姿态与背晶、搬运兽背囊、蛛母腹部以及卫士盾牌。导出器兼容 Blockbench 5 的独立 groups 数据，默认直接导出已保存原件，避免覆盖 MCP 编辑结果。
- [`2eecf5a`](https://github.com/fl749731-lab/ender-infection/commit/2eecf5a186491bc774fc39da5e7b8462534eb0ea): 补齐新增怪物、重做怪物、旧感染生物及母体各阶段的紫晶自发光蒙版，保留晶体暗面与尖端的完整覆盖；飞行晶刺保持自发光，并增加零环境光的正反面实机检查。
- [`c8da949`](https://github.com/fl749731-lab/ender-infection/commit/c8da94973198327c276ec7cd646705aca6eb7846): 重绘三套晶体护甲的穿戴贴图和物品图标。细化头盔、背甲、肩臂、关节和靴子，分别采用共鸣紫晶纹、余烬橙色熔纹和虚空青色晶脊。
- [`2eecf5a`](https://github.com/fl749731-lab/ender-infection/commit/2eecf5a186491bc774fc39da5e7b8462534eb0ea): 修复寄生蛛母等新近战单位寻路到达目标附近后仍不出手的问题：攻击起手与命中统一按目标碰撞箱表面判断距离。晶背搬运兽的防御响应范围从 4 格扩大至 12 格，避免受击后被击退出范围而立刻放弃反击。

新增 14 种新单位的原生 Forge AI 回归：战斗单位自行锁定原版动物并造成伤害，再自动转向原版敌对怪物；孵化囊自行响应附近目标并付费产兵。
- [`3d4ee59`](https://github.com/fl749731-lab/ender-infection/commit/3d4ee59d66f420bd441de4e5c37621dd8aa92e23): 感染的实验室出现概率从 1/42 区块提高到 1/30，便于在正常探索中遇到。
- [`c8da949`](https://github.com/fl749731-lab/ender-infection/commit/c8da94973198327c276ec7cd646705aca6eb7846): 开局陨石以真实爆炸撞击地表。冲击击退并伤害附近生物，在自然地面炸出陨石坑，随后在坑内生成母体。地形破坏遵守 mobGriefing，容器与设备保留，读档继续时不会重复爆炸。
- [`0d316ac`](https://github.com/fl749731-lab/ender-infection/commit/0d316ac623e21d1cf8d847cc547c6b39f7060ad3): 修复实验室通风口等完整设备的全透明发光蒙版导致 GeckoLib 注册纹理崩溃；为全部发光渲染层增加有效像素检查和重载缓存清理，导出器不再生成空蒙版，客户端验证实际注册发光纹理。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.10.0...v0.11.0
<!-- qegg:release:end -->

<!-- qegg:release 0.10.0 -->
## v0.10.0 - 2026-11-02

### Features

- [`82ce615`](https://github.com/fl749731-lab/ender-infection/commit/82ce6157356eab44d1f5e82e4ddb5b77ca01f96d): 新增世界生成结构「感染的实验室」：三间房、培养罐、宝箱与野生母体，按 1/42 的稀有度出现在主世界地表；/enderlab 可就地生成用于查看。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.9.0...v0.10.0
<!-- qegg:release:end -->

<!-- qegg:release 0.9.0 -->
## v0.9.0 - 2026-11-02

### Features

- [`e5aef2f`](https://github.com/fl749731-lab/ender-infection/commit/e5aef2f6f4645eeb2c24589d73f02c41222eeabf): 成熟地表自己孵出的畸变兽不再向母体收生物质；人口上限与三十秒节拍仍然生效。另修复末影阵营单位离开视野后被按距离刷掉。

### Bug Fixes

- [`e5aef2f`](https://github.com/fl749731-lab/ender-infection/commit/e5aef2f6f4645eeb2c24589d73f02c41222eeabf): 阶段刷怪蛋改用原版刷怪蛋渲染：五个阶段各一套配色，并补上 template_spawn_egg 模型，不再是缺省占位图。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.8.0...v0.9.0
<!-- qegg:release:end -->

<!-- qegg:release 0.8.0 -->
## v0.8.0 - 2026-11-02

### Features

- [`f10e600`](https://github.com/fl749731-lab/ender-infection/commit/f10e600b9d12b63e260438f19ef01ab0313bc2f7): 新增独立的末影感染创造模式物品栏，内含五个阶段的母体刷怪蛋与全部单位刷怪蛋、节点和地形方块。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.7.0...v0.8.0
<!-- qegg:release:end -->

<!-- qegg:release 0.7.0 -->
## v0.7.0 - 2026-11-01

### Features

- [`dc95ad2`](https://github.com/fl749731-lab/ender-infection/commit/dc95ad2746d3ca7b8acce847a716f42885694c2a): 畸变单位每次出手会从多种攻击模式中随机选择：重砸、前冲、范围震荡与战吼（给周围友军加速和力量）。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.6.0...v0.7.0
<!-- qegg:release:end -->

<!-- qegg:release 0.6.0 -->
## v0.6.0 - 2026-11-01

### Features

- [`bd19b44`](https://github.com/fl749731-lab/ender-infection/commit/bd19b4426606afda308df27f1142b0a302092fe3): 畸变单位整体加强：血量与伤害上调约 50–80%，新增护甲，攻击间隔从 35 tick 缩到 26 tick；母体与攻城兽受到的单次伤害按最大生命封顶（母体 8%、攻城兽 10%），不会再被一击带走。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.5.2...v0.6.0
<!-- qegg:release:end -->

<!-- qegg:release 0.5.2 -->
## v0.5.2 - 2026-11-01

### Bug Fixes

- [`8103c62`](https://github.com/fl749731-lab/ender-infection/commit/8103c6257735f1dd6b7b19a83582fcb33647f45b): 孵化与地表产卵在和平时期必须给节点留 16 生物质，扩张不再被自家兵源饿死；母体受袭时该保底解除。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.5.1...v0.5.2
<!-- qegg:release:end -->

<!-- qegg:release 0.5.1 -->
## v0.5.1 - 2026-11-01

### Bug Fixes

- [`32a6a30`](https://github.com/fl749731-lab/ender-infection/commit/32a6a30a1bfb656c5a264afa32412fb85a9de5c9): 雪层盖住的感染地表现在照常计入土地收入；之前雪会让整片殖民地收不到生物质，扩张因此停摆。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.5.0...v0.5.1
<!-- qegg:release:end -->

<!-- qegg:release 0.5.0 -->
## v0.5.0 - 2026-11-01

### Features

- [`0c5722f`](https://github.com/fl749731-lab/ender-infection/commit/0c5722fa1c65939d2a6cd45abb46abfc9bf47d64): 进入被侵蚀区域的所有非本阵营单位都会获得末影侵蚀：凋零、反胃，以及本模组自己的末影菌丝感染（持续伤害、减速、饥饿）。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.4.1...v0.5.0
<!-- qegg:release:end -->

<!-- qegg:release 0.4.1 -->
## v0.4.1 - 2026-11-01

### Bug Fixes

- [`e42e71e`](https://github.com/fl749731-lab/ender-infection/commit/e42e71eb23abb6d0f9968aad17b69e26371cff3f): 侵蚀的垂直扫描从 +3/-4 扩到 +3/-10：雪坡、坑底和落差较大的雪面现在也会被侵蚀，地表雪层不再阻挡转换。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.4.0...v0.4.1
<!-- qegg:release:end -->

<!-- qegg:release 0.4.0 -->
## v0.4.0 - 2026-11-01

### Features

- [`f13d23c`](https://github.com/fl749731-lab/ender-infection/commit/f13d23c8413778dc5c0f5cd980a4b3dd270f7a90): 雪块、细雪与冰现在和其它地面一样会被侵蚀，盖在地表上的雪层会被一并化掉。
- [`f13d23c`](https://github.com/fl749731-lab/ender-infection/commit/f13d23c8413778dc5c0f5cd980a4b3dd270f7a90): 三阶之后母体对所有非本阵营生物宣战：守卫也会猎杀动物，猎手不再受活动范围束缚。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.3.0...v0.4.0
<!-- qegg:release:end -->

<!-- qegg:release 0.3.0 -->
## v0.3.0 - 2026-11-01

### 新功能

- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 猎手会循着可达的猎物气味外出，而不是只在视线内才追；每只母体新增攻击性与四臂经济策略学习，战果会改变嗅觉半径、围攻上限以及孵化/扩张/进阶/储备之间的取舍。
- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 三阶后不再限制节点数量与铺设距离；成熟地表会自己长出感染植物，并能让母体付出生物质产出单位。
- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 攻城兽模型、动画与粒子重做，并修复冲击波漏伤：伤害带窄于波前步长，导致部分距离永远打不中。

### 修复

- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 地表改为整列向下侵蚀，悬崖与挖开的洞不再露出原版泥土；成熟化改走独立且免费的通道，老地块会真正推进到深色成熟地表。

### 杂项

- [`657a68d`](https://github.com/fl749731-lab/ender-infection/commit/657a68dce2e62d977e8063d049e1aac63f8d206b): Automate version bumps and GitHub releases for the mod.
<!-- qegg:release:end -->
