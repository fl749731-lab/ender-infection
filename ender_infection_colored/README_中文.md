# 末影感染 · Ender Infection

Minecraft 1.20.1 / Forge 47.3.12 / GeckoLib 4.4.9 / Java 17。项目在 `E:\mc\ender_infection_colored`，可编辑 Blockbench 模型在 `E:\mc\colored_units`，可玩实例在 `E:\mc\Minecraft1201`。

完整玩法、下载、建筑来源与前置说明见[仓库首页](../README.md)。本轮扩张修复涵盖连通坡面和崖面、高台前哨、未覆盖地形与肥力评分、旧感染带接力、付费补建、网络周转金与高处土地收益。规则和费用见[扩张说明](../docs/expansion.md)。管理员使用 `/enderstrategy` 查看附近节点的高度范围和计时。

使用 JDK 17 运行 `gradlew.bat build` 编译。`gradlew.bat runGameTestServer -PexpansionChecks --offline` 运行扩张专项；不带该参数运行全量。测试世界位于 `build`，不会打开玩家存档。实际测试与编译记录见 [RESULTS.txt](verification/RESULTS.txt)。

游戏实例使用 `Minecraft1201\instances\EnderInfection1201\.minecraft\mods`，必须同时保留 GeckoLib 前置。新 JAR 需要重启 Minecraft 才会加载。