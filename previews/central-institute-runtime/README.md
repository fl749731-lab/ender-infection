# 中央研究所运行实景

六层、48 间中央研究所将随 **0.12.0-alpha.3（待发布）** 作为实际游戏建筑交付。这里保存上一轮两个原生客户端的实景证据，使用说明见 [中央研究所](../../docs/central-institute.md)。

| 版本 | 总览 | 地上三层 | 地下三层 |
|---|---|---|---|
| Forge 1.20.1 | [外观、中庭、夜核](forge/overview.png) | [G1](forge/rooms_A_G1.png) / [G2](forge/rooms_B_G2.png) / [G3](forge/rooms_C_G3.png) | [B1](forge/rooms_D_B1.png) / [B2](forge/rooms_E_B2.png) / [B3](forge/rooms_F_B3.png) |
| NeoForge 1.21.1 | [外观、中庭、夜核](neoforge/overview.png) | [G1](neoforge/rooms_A_G1.png) / [G2](neoforge/rooms_B_G2.png) / [G3](neoforge/rooms_C_G3.png) | [B1](neoforge/rooms_D_B1.png) / [B2](neoforge/rooms_E_B2.png) / [B3](neoforge/rooms_F_B3.png) |

上一轮原生验收世界使用种子 `7312026`，通过完整适地判定的自然结构起始区块为 `(1558, -946)`。结构占地 `120×104×55`，首层地板 Y=71。每版验证整片包围盒及外圈 99 个 FULL 区块，并拍摄 61 个机位，合计 122 张实景。图片经过区块加载、渲染编译及空画面检查；夜景展示 59 处紫晶单元的实际表面补光。

各版本 `receipt.json` 保存全部 61 张原图及预览输出的相对路径和 SHA256。完整原图位于对应工程的 `build/central-institute-visual-check/screenshots`；此目录的图片由 `tools/package_central_institute_previews.py --loader forge|neoforge` 生成。上一轮另有两版合计 42 项研究所专用原生测试。本次发布前会重新执行完整双版本检查，最终测试与构建结果以本次发布验证清单及 `build/dual-version-validation` 归档为准；这里的历史截图数量不代表本次全量复验已完成。

alpha.3 发布状态目前为待发布。供电、服务器、冷却设备的组合内饰已落地；研究所电网系统、收容事件和研究所 Boss 战尚未实现。alpha.2 设计图包与 PDF 继续保存在其 [历史发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.2)。
