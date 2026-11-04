---
type: feat
scope: institute
packages:
  java@ender_infection_colored: minor
---

将中央研究所图纸组装为 120×104 场地、地上三层和地下三层共 48 间有内饰的游戏建筑。加入双楼梯、货梯、中庭连桥、48 个分类补给桶，以及适配平坦干地的原生自然生成；保留原有小型实验室。

新增 `/enderinstitute previewcentral <origin>`、`build <origin>`、`status` 和 `cancel`。先检查完整区域，再按已加载区块施工；保护已有容器、设备和活动方块，支持保存进度与保守恢复，取消保留已经完成的部分。

修正房门、竖井和补给桶通路；入口阶梯及地下支撑适配 8 格地形高差。紫晶组合采用不挤占长芽空间的补光，检查真实暴露面亮度，而非只检查光源自身。

Forge Minecraft 1.20.1 与 NeoForge Minecraft 1.21.1 同步更新，提供两版原生功能测试、真实主世界自然生成和全房间实景验收。公共 API 维持 v1，附属 Mod 示例保留兼容。

本轮完成建筑、内饰、通行、补给和生成；整楼电网联动、收容事件与 Boss 机制尚未接入。
