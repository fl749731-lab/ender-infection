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

该命令先检查公共源、现有类与注册 ID 是否保留、转换资源是否一致，再分别构建两个主工程及 API 开发 JAR、两个附属示例，并运行两版 AI、裂隙、研究所接口与附属集成测试。验证会核对实际执行的测试数量，零项测试不能视为通过。日志与 JAR 校验和写入 `build/dual-version-validation`。静态清单检查只能发现遗漏；行为是否一致需要原生测试和涉及画面时的客户端对照。

附属示例的构建方式见 `examples/institute-addon` 的说明。`build/api` 中的开发 JAR 供示例编译，发行物是主工程 `build/libs` 内的 JAR。

## 发版

在 Forge 工程更改一次版本号，两版构建将使用相同版本。CI 对两个 loader 及附属示例分别检查，发行资产附带两版主 JAR。`0.12.0-alpha.2` 首次提供双版本发行与研究所 API v1；附属示例的最低核心依赖为 `0.12.0-alpha.2`，不能使用缺少 API 的 `0.12.0-alpha.1`。

Forge 与 NeoForge 的 Mod JAR、GeckoLib 前置必须匹配对应 Minecraft 和 loader。两套实例独立测试；不把现有 1.20.1 世界直接转换为 1.21.1 测试世界。

## 研究所设计交付

从 [alpha.2 发布页](https://github.com/fl749731-lab/ender-infection/releases/tag/v0.12.0-alpha.2) 下载 `central-research-institute-interiors-v01.zip`，解压后打开 `index.html`，可浏览六层与 48 间内饰、逐格坐标、方块清单和待补模型对照。`central-institute-interior-atlas-v01.pdf` 是独立的 73 页图册附件，完整图包包含原始 PNG、SVG、CSV。这套建筑仍需要组装和自然生成接入，公共 API 与示例房间不等同于完成大型研究所。
