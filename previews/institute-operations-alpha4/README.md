# alpha.4 研究所功能验收截图

这组截图来自 Forge 1.20.1 与 NeoForge 1.21.1 的独立功能验收场景，每版 10 张，共 20 张。场景用简化地台、墙面与设备检查真实交互、菜单和奖励，**不是完整房间或中央研究所的美术展示**；空背景不代表正式房间布局。对应版本为 **0.12.0-alpha.4，2026-11-05**，安装包及公开发行说明见 [alpha.4 发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.4)。

推荐先看 **显微镜处理中、离心机处理中、医疗家具**，分别展示加工槽位与进度、试剂输入，以及两格医疗床和拼接沙发。两版菜单标签、产物图标、门形态及家具材质均已逐图复核，未发现缺贴图或明显穿插。

两种机器均将中间通用试剂槽标为 `Reagent`，以兼容附属加工配方；本体的显微分析配方不需要试剂，该槽留空即可。

| 场景 | Forge | NeoForge |
| --- | --- | --- |
| 初始断电 | [图片](forge/power_off.png) | [图片](neoforge/power_off.png) |
| 分区供电 | [图片](forge/power_on.png) | [图片](neoforge/power_on.png) |
| 无卡拒绝开门 | [图片](forge/door_denied.png) | [图片](neoforge/door_denied.png) |
| 持卡解锁开门 | [图片](forge/door_unlocked.png) | [图片](neoforge/door_unlocked.png) |
| **显微镜处理中** | [图片](forge/microscope_running.png) | [图片](neoforge/microscope_running.png) |
| 显微分析完成 | [图片](forge/microscope_done.png) | [图片](neoforge/microscope_done.png) |
| **离心机处理中** | [图片](forge/centrifuge_running.png) | [图片](neoforge/centrifuge_running.png) |
| 离心分离完成 | [图片](forge/centrifuge_done.png) | [图片](neoforge/centrifuge_done.png) |
| 核心档案与虚空晶奖励 | [图片](forge/archive_reward.png) | [图片](neoforge/archive_reward.png) |
| **医疗床与等候沙发** | [图片](forge/medical_furniture.png) | [图片](neoforge/medical_furniture.png) |

`power_off` / `power_on` 使用日光环境和同一面板顶面机位，肉眼亮度差异有限；实际供电、方块状态与发光值由原生 GameTest 和场景状态断言佐证，不能仅凭两张日光图判断照明变化。

[Forge 验收记录](forge/verification.json)与 [NeoForge 验收记录](neoforge/verification.json)分别保存截图数量、两种加工菜单、真实网络交互、一次性档案奖励和各 PNG 的 SHA256；[输入清单](source-inputs.json)记录本轮快照来源。玩法与适用范围见[研究所供电与样本研究](../../docs/institute-operations.md)。

完整双版干净构建及 16 组原生测试共 **139 项必需测试通过**，包含两版附属各 2 项；结果、安装包校验值与验证范围见[验证总表](validation.json)。存档检查覆盖 NBT 读写往返，附属加工配方检查覆盖注册与字段；真实客户端流程覆盖本体的显微分析和离心分离。

`validation.json` 中的 `published: false` 保留发布前验收快照的原始状态，不随发布改写；公开版本与最终发行资产见上方 GitHub 标签发布页。本轮玩法需要新生成或新施工的 layout v3，已有 alpha.3 建筑不会自动重建；收容事故事件和研究所 Boss 战仍未实现。
