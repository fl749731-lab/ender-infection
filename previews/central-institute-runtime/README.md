# 中央研究所运行实景

**0.12.0-alpha.3 · central-institute-v2**。本次发行执行完整双版本干净构建，实际 115 项必需原生测试全部通过；四个原生客户端完成 150 张截图验收：122 张中央研究所实景，加 28 张真实库存界面及开柜模型。最终 JAR 校验和、布局与图像 SHA256 见发布验证清单。

| 版本 | 建筑与中庭 | 地上三层 | 地下三层 |
|---|---|---|---|
| Forge 1.20.1 | [外观、中庭、夜核](forge/overview.png) | [G1](forge/rooms_A_G1.png) / [G2](forge/rooms_B_G2.png) / [G3](forge/rooms_C_G3.png) | [B1](forge/rooms_D_B1.png) / [B2](forge/rooms_E_B2.png) / [B3](forge/rooms_F_B3.png) |
| NeoForge 1.21.1 | [外观、中庭、夜核](neoforge/overview.png) | [G1](neoforge/rooms_A_G1.png) / [G2](neoforge/rooms_B_G2.png) / [G3](neoforge/rooms_C_G3.png) | [B1](neoforge/rooms_D_B1.png) / [B2](neoforge/rooms_E_B2.png) / [B3](neoforge/rooms_F_B3.png) |

| 版本 | 真实 27 格库存 | 实际开柜模型 |
|---|---|---|
| Forge 1.20.1 | [储柜界面](forge/cabinet_inventory.png) | [柜门打开](forge/cabinet_open.png) |
| NeoForge 1.21.1 | [储柜界面](neoforge/cabinet_inventory.png) | [柜门打开](neoforge/cabinet_open.png) |

截图覆盖最终六层、48 间房及 13 类真实储物家具；每版包含 61 张建筑实景和 14 张库存/开柜原图。中央模板有 1417 个战利品储物根，另保留 48 个补给桶；六种旧站模板合计有 368 个储柜。E5 恢复六座大型培养舱，A3 配置四张观察床；设备、细墙带、隔帘和通路采用最终布局。

两版 `receipt.json` 与 `storage-receipt.json` 保存原图和预览 SHA256，库存界面及开柜副本与原图字节相同。完整验证见 [verification.json](verification.json)，其中测试数量来自最终原生日志，JAR、56 片模板、生产输入和图像指纹相互对应。预览仅等比缩放排版，原图像素未修饰。

完整图包见 [alpha.3 实景附件](https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.3/central-institute-runtime-0.12.0-alpha.3.zip)，使用方法见 [中央研究所](../../docs/central-institute.md)。本地证据状态为 ready_for_release，不代表此工具执行了发布。研究所电网、收容事件和 Boss 战尚未实现；alpha.2 设计图册仍作为 [历史附件](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.2) 保留。
