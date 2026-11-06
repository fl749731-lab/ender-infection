# 收容监管体增强特效运行记录

本轮在独立 Forge 1.20.1 / Java 17 与 NeoForge 1.21.1 / Java 21 工程中验收 alpha.7 开发源码。公开版本仍为 alpha.6；本轮不执行公开发布。原有完整 F8 接入结果留在[历史验收](../../institute-boss-alpha7/runtime/README.md)，不把历史 197 项全量结果计成本轮重新执行的数量。

## 画面与动作

| 内容 | Forge 实景 | NeoForge 实景 |
|---|---|---|
| 右爪三连斩迹 | [爪击](forge/warden_vfx_claw_hit.png) | [爪击](neoforge/warden_vfx_claw_hit.png) |
| 琥珀色危险圈 | [跺地预警](forge/warden_vfx_stomp_windup.png) | [跺地预警](neoforge/warden_vfx_stomp_windup.png) |
| 双层波纹与立体波脊 | [跺地释放](forge/warden_vfx_stomp_hit.png) | [跺地释放](neoforge/warden_vfx_stomp_hit.png) |
| 前卷波峰与落坡 | [第二次脉冲](forge/warden_vfx_pulse_2.png) | [第二次脉冲](neoforge/warden_vfx_pulse_2.png) |
| 抑制台成功放电 | [薄荷色电弧](forge/warden_vfx_console_discharge.png) | [薄荷色电弧](neoforge/warden_vfx_console_discharge.png) |
| 核心局部断环 | [胸甲开启](forge/warden_vfx_core_exposed.png) | [胸甲开启](neoforge/warden_vfx_core_exposed.png) |

模型沿用七组既有动作：待机、行走、横扫、跺地、晶体脉冲、核心暴露和死亡。完整时长及命中点见[操作说明](../../../docs/institute-boss.md)。本轮增加特效几何、像素遮罩和同步状态，没有替换本体模型。灾变的模型与特效研究见[对照记录](../cataclysm-reference.md)，公开模型网站见[资源清单](../../../docs/public-minecraft-models.md)。

脉冲的四段截面形成后坡、波峰、前卷和落坡，向前移动时逐渐隆起并回落；跺地波脊环绕地面向外扩散。这些波面属于释放反馈，伤害沿用原有瞬时扇区／范围及视线检查，没有添加移动波面的二次伤害。

## 验收范围

本轮针对 Warden 的 17 项原生测试在每版运行一次，共 34 项；在既有战斗／存档用例中追加技能轮次、时钟、锁定方向、服务器控制台坐标、读档取消攻击及冷却复位的同步断言。

两版客户端分别采集 24 帧，覆盖三种预警、三爪、跺地、左中右三次脉冲、抑制台放电、核心开启／关闭、死亡、领奖、背包及奖励冷却；其中跺地使用最少粒子设置，验证危险轮廓仍保留。全部原始截图不做后期增亮或绘制特效。

场景来自六片实际 F8 模板；前置研究解锁、固定战斗位置、俯视观察镜头与最终击杀是验收夹具。控制台交互走实际玩家操作及网络处理。服务端用例独立覆盖伤害与 AI，不以夹具中无敌玩家的截图证明伤害平衡。

运行日志核对每种效果实际触发，三次脉冲和两台放电分别计数；同时检查缺失贴图、实体死亡后效果清理、单监管体瞬时几何上限不超过 96、核心持续不超过 16。最终实测计数与 JAR 摘要以[本轮结果](final-verification.json)为准。几何预算不是 GPU／帧率基准，未进行双客户端联机压力测试。

主 JAR、API 开发包与两版附属示例按仓库双版入口构建。本轮是开发增量验收；公开发布前仍需运行 `python tools/build_dual_version.py --clean --gametests`。

原生截图入口是各工程的 `runClient -PwardenVfxCheck`，使用独立 `build/warden-vfx-check` 世界。日志与截图可由[客户端核验脚本](../../../tools/verify_warden_vfx_visual.py)收集；[最终核验脚本](../../../tools/verify_warden_vfx_integration.py)检查本机本轮收集目录、资源和 JAR 一致性。
