# Forge 与 NeoForge 同步维护

末影感染保留 Forge 1.20.1，并增加 NeoForge 1.21.1。两版使用同一发行版本号；新设备、房间模块和附属 Mod 使用同一套研究所 API。

## 代码与资源

| 内容 | 维护位置 | 同步方式 |
|---|---|---|
| 公共 API 定义、注册和校验 | `common/src/main/java` | 两个主工程直接编译同一源码 |
| Forge 平台桥和玩法实现 | `ender_infection_colored/src/main/java` | 修改玩法时同步检查 Neo 对应类 |
| NeoForge 平台桥和玩法实现 | `ender_infection_neoforge/src/main/java` | 适配 1.21 API，保留玩法与测试 |
| 模型、贴图、动画、语言和数据 | Forge `src/main/resources` | 构建时由 `tools/sync_neoforge_resources.py` 转换到 Neo |
| 附属 Mod 示例 | `examples/institute-addon` | 共用示例逻辑，分别构建两个 loader |
| 发行版本号 | Forge `build.gradle` | Neo 构建直接读取相同值 |

资源转换处理 1.21 的数据目录单数命名、配方结果格式与 loader 命名空间。贴图、模型和动画从同一源文件复制；不要在生成目录单独修图。

## 本地构建

设置 `JAVA17_HOME` 与 `JAVA21_HOME`，分别指向两个 JDK。GitHub Actions 的 `JAVA_HOME_17_X64` 和 `JAVA_HOME_21_X64` 也受支持。在仓库根目录运行：

```text
python tools/build_dual_version.py --clean --gametests
```

统一构建脚本默认把 NeoForm 的 Minecraft 资源缓存放在工作区 `build/minecraft-assets-1.21.1`。单独运行 Neo 工程时，也可设置 `NFRT_ASSET_ROOT` 指向有空间的目录，避免系统盘缓存不足。

Forge 原生测试的每个新目录都会写入固定种子 0、关闭结构生成的超平坦配置，与 NeoForge 测试场地对齐。统一脚本串行构建与测试，Forge 测试进程设置内存上限，避免普通地形的落砂和并发开发进程影响验证。

该命令先检查中央研究所最终 NBT、公共源、现有类与注册 ID 是否保留、转换资源是否一致，再分别构建两个主工程及 API 开发 JAR、两个附属示例，并运行两版 AI、裂隙、研究所接口、完整中央研究所与附属集成测试。验证会核对实际执行的测试数量，零项测试不能视为通过。日志与 JAR 校验和写入 `build/dual-version-validation`。静态清单检查只能发现遗漏；行为是否一致需要原生测试和涉及画面时的客户端对照。

附属示例的构建方式见 `examples/institute-addon` 的说明。`build/api` 中的开发 JAR 供示例编译，发行物是主工程 `build/libs` 内的 JAR。

## 发版

**0.12.0-alpha.3** 同时提供 Forge 1.20.1 / Java 17 与 NeoForge 1.21.1 / Java 21，安装包与发行验证记录见 [alpha.3 发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.3)。alpha.3 为两版加入六层、48 间中央研究所实际建筑、自然生成与手动施工命令；安装包按对应 Minecraft 和加载器分别使用。

储物更新同步覆盖两版：13 类家具使用 27 格原版箱子界面和延迟战利品；中央模板 1417 个储物根、六种旧小站模板合计 368 个，玩家自行放置为空。公共接口仍为 v1；库存和战利品是平台实现，不把 1.21 的物品组件或注册表参数写进公共 Java 17 契约。每轮需验证首次开奖、重复打开、读档、多玩家、根/部件掉落及模型开合，不以编译通过代替交互验收。

本轮共享布局同时恢复 E5 的六座大型培养舱和 A3 的四张观察床；两版使用同一份最终清单，不以旧储柜数或床数锁定测试结果。

在 Forge 工程更改一次版本号，两版构建将使用相同版本。CI 对两个 loader 及附属示例分别检查，发行资产附带两版主 JAR。`0.12.0-alpha.2` 首次提供双版本发行与研究所 API v1；附属示例的最低核心依赖为 `0.12.0-alpha.2`，不能使用缺少 API 的 `0.12.0-alpha.1`。

Forge 与 NeoForge 的 Mod JAR、GeckoLib 前置必须匹配对应 Minecraft 和 loader。两套实例独立测试；不把现有 1.20.1 世界直接转换为 1.21.1 测试世界。

本次发行执行完整双版本干净构建，实际 115 项必需原生测试全部通过；四个原生客户端完成 150 张截图验收：122 张中央研究所实景，加 28 张真实库存界面及开柜模型。最终 JAR 校验和、布局与图像 SHA256 见发布验证清单。 后续发行仍须重新运行统一验证命令，不沿用本轮通过数量。研究所电网、收容事件和 Boss 战尚未实现。

## 研究所设计交付

从 [alpha.2 发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.2) 下载 `central-research-institute-interiors-v01.zip`，解压后打开 `index.html`，可浏览六层与 48 间内饰、逐格坐标、方块清单和待补模型对照。`central-institute-interior-atlas-v01.pdf` 是独立的 73 页图册附件，完整图包包含原始 PNG、SVG、CSV。alpha.2 附件继续作为设计阶段的历史图纸保留；alpha.3 实际建筑、放置命令及验证方法见 [中央研究所](central-institute.md)，双版实景见 [运行实景](../previews/central-institute-runtime/README.md)。
