# Ender Infection — NeoForge 1.21.1

此目录是 Minecraft 1.21.1 + NeoForge 版本。Forge 1.20.1 工程保留在 `../ender_infection_colored`；两版的游戏存档、运行目录和 JAR 分开使用。

该平台从 **v0.12.0-alpha.2** 起与 Forge 1.20.1 同步发行，同时提供研究所 API v1。发布附件见 [alpha.2 发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.2)。

- Java 21；NeoForge 21.1.265；GeckoLib 4.9.3。
- 版本号读取 Forge 工程的 `build.gradle`，由同一版本号维护。
- `../common/src/main/java` 提供两版共同编译的研究室扩展 API。
- 模型、贴图、NBT 建筑和数据资源以 Forge 资源目录为源。构建自动调用 `../tools/sync_neoforge_resources.py`，转换 1.21 的数据目录、进度图标及附魔条件，输出到本工程 `build/generated/sharedResources`。
- 平台 Java 代码需要两版一起修改；资源转换不负责自动移植玩法代码。

构建：

```powershell
.\gradlew.bat build apiDevJar
```

游戏用 JAR 位于 `build/libs`。`build/api/ender-infection-api-dev.jar` 是附属 Mod 编译和开发运行所用的完整开发依赖，不能替代另一加载器的 JAR。附属示例位于 `../examples/institute-addon`，公共 API 说明见 `../docs/institute-api.md`。

原生服务端回归可按组或组合运行；每次会在 `build/gametest-*` 新建隔离测试存档：

```powershell
.\gradlew.bat runGameTestServer -PriftChecks
.\gradlew.bat runGameTestServer -PinstituteChecks -PoreChecks -PlabChecks
```

`-PregressionChecks` 是导航和波动攻击的聚焦诊断组。`runClient -PdesignSmoke` 会实际检查客户端几何、动画、物品模型和发光贴图，在 `build/client-review/design-smoke-result.txt` 写入结果后退出。`-PriftVisualCheck` 可运行裂隙生物的隔离画面检查。

`runClient -PcrystalGlowCheck -PcrystalGlowSubjects=enderman_early,enderman,anchor_guard` 在独立密闭房间中检查这三个单位的零光照发光。省略 subjects 参数会检查全部晶体单位。

首次运行会下载 Minecraft 资源。空间不足时，可在当前 PowerShell 会话设置 `NFRT_ASSET_ROOT` 到有足够空间的目录；这是 NeoForm 官方支持的缓存位置选项，正常构建不需要设置。更改此变量不会移动游戏存档。

大型中央研究院目前仍是设计图册；扩展 API 的 9×7×9 示例房间不代表整座研究院已集成自然生成。
