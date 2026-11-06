# 末影感染 · Ender Infection

Minecraft 感染阵营与研究所探索模组。感染母体通过生物质和扩张节点发展领地；玩家探索中央研究所、恢复供电、研究样本并处理收容事故。

## 下载与安装

| 内容 | 版本 | 下载 |
|---|---|---|
| 主模组预发布 | **0.12.0-alpha.7** | [Forge 1.20.1](https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.7/ender-infection-0.12.0-alpha.7.jar) · [NeoForge 1.21.1](https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.7/ender-infection-neoforge-1.21.1-0.12.0-alpha.7.jar) |
| 可选科技附属：共振工业 | **0.1.0-alpha.1** | [双版本安装包与图纸](https://github.com/fl749731-lab/ender-infection/releases/tag/resonant-industry-v0.1.0-alpha.1) |
| 历史稳定版 | 0.11.0，Forge 1.20.1 | [稳定版发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.11.0) |

1. 按 Minecraft 版本选择一个加载器分支，安装对应的 Java 和 GeckoLib。
2. 把同一分支的主模组与 GeckoLib 放进实例的 `mods` 文件夹；客户端和服务端均需安装。
3. 更新时移除旧主模组 JAR，再放入新包并重启游戏。共振工业另加一份对应加载器的附属 JAR。

| 分支 | Java | 已验收加载器 | 已验收 GeckoLib |
|---|---|---|---|
| Minecraft 1.20.1 / Forge | 17 | 47.3.12 | [4.4.9](https://modrinth.com/mod/geckolib/version/RBA7lJaW) |
| Minecraft 1.21.1 / NeoForge | 21 | 21.1.265 | [4.9.3](ender_infection_neoforge/README.md) |

两种加载器的 JAR 分别使用。研究所新布局出现在新区块或新施工建筑中；更新不会自动重建已有研究所，也不会重抽已领取的战利品。

## 主线怎么玩

**获取物资 → 修复研究所电源 → 分区送电与门禁 → 样本分析和离心 → 核心档案 → E8 收容事故 → F8 收容监管体。**

中央研究所已有六层、48 间内饰房间、可开启的战利品储柜、分区照明、门禁和研究设备。完成 E8 的警报与维修后，可在 v5 建筑的 F8 启动监管体，操作两座抑制台暴露核心并领取共享奖励。完整步骤见[主线流程](docs/main-progression.md)。

感染阵营、晶体装备、矿石、怪物与管理员指令见[生存玩法手册](docs/gameplay-guide.md)。共振工业的多方块、FE 电网与流体加工使用[独立指南](addons/resonant-industry/README.md)。

## 这一版有什么

- B3 / F8 收容监管体、v5 研究所启动终端与两座抑制台；旧研究所保持原样。
- 七套 Boss 动作，以及有预警的爪击、立体卷曲晶体扇浪、冲击环、符文和电弧。
- 共鸣祭司与晶背搬运兽使用署名公开底模改造，适配五套动作、持杖握点、尾部、接地及循环；紫晶材质自发光。
- 储柜门向外开启，显示器补齐落台支架；相邻同类、同朝向的研究所门组成左右同步的[双开门](docs/lab-fixtures.md)。
- 事件、奖励和 Boss 状态持久化；两版完整干净构建、原生测试与重启／区块卸载验收，详细结果随发行附件提供。

[alpha.7 更新说明与附件](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.7) · [监管体指南](docs/institute-boss.md) · [模型实景](previews/public-model-adaptations-v1/README.md) · [完整更新日志](CHANGELOG.md)

本版完成两只模型的首批替换。其他感染／畸变生物、基础武器、护甲、普通特效和设备仍按[全体评审](docs/model-visual-review.md)继续改造；评审清单不代表全部对象已替换。alpha.6 及更早安装包不包含 F8 Boss。

## 文档入口

| 用途 | 文档 |
|---|---|
| 从哪里开始、每一步做什么 | [主线流程](docs/main-progression.md) |
| 研究所位置、建筑和管理员施工 | [中央研究所](docs/central-institute.md) |
| 修复供电、门禁、加工和档案 | [研究所操作](docs/institute-operations.md) |
| E8 启动、维修、领奖与存档规则 | [收容事故](docs/containment-incident.md) |
| 晶体装备、怪物与基础玩法 | [生存玩法手册](docs/gameplay-guide.md) |
| 科技附属、三种多方块与面板 | [共振工业](addons/resonant-industry/README.md) |
| 扩展接口与示例 | [研究所 API](docs/institute-api.md) · [附属示例](examples/institute-addon/README.md) |
| 源码构建与双版本维护 | [开发流程](docs/dual-version-maintenance.md) |

[全部文档](docs/README.md) · [历史发行与设计资料](docs/history.md)
