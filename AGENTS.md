# 双版本维护

本仓库同时维护 Forge Minecraft 1.20.1 和 NeoForge Minecraft 1.21.1。用户要求保留并同步更新两版。

- `ender_infection_colored` 是 Forge 1.20.1 工程，使用 Java 17。
- `ender_infection_neoforge` 是 NeoForge 1.21.1 工程，使用 Java 21。
- `common/src/main/java` 是两版共用的研究所 API 源码。公共契约保持 Java 17 兼容。
- 美术与数据资源以 Forge `src/main/resources` 为源。通过 `tools/sync_neoforge_resources.py` 转换 1.21 数据目录与格式，不手改 Neo 的 `build/generated/sharedResources`。
- 玩法变更和缺陷修复同时检查两版调用点。平台 API 不同可以采用不同实现，但不能通过删掉功能或测试让移植编译通过。
- 发版版本号以 Forge `build.gradle` 为准，Neo 自动读取。发布前运行 `python tools/build_dual_version.py --clean --gametests`，两版构建与原生测试全部通过才发布两份 JAR。
- 附属 Mod 示例在 `examples/institute-addon`，公共 API 变更后也要验证两版示例构建。
- 用户已有的 `Minecraft1201` 实例与世界不能当作迁移测试目录；使用各工程独立 `build` 目录。
- 本轮建筑设计用户明确要求不用 Blockbench。继续用图纸、概念图和方块坐标交付，除非用户改变要求。

完整流程见 `docs/dual-version-maintenance.md`。
