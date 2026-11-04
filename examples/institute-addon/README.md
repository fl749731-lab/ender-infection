# 研究所附属 Mod 示例

这是一套能独立构建的附属 Mod 源码，包含 Forge 1.20.1 与 NeoForge 1.21.1 两个工程。两版共用 `shared/` 中的玩法代码和外观资源，各自使用对应加载器的注册入口。

**依赖含研究所 API v1 的核心版本 `v0.12.0-alpha.2` 或后续兼容版本。旧版 `v0.12.0-alpha.1` 没有此 API，不能运行本示例；两版元数据均将最低依赖设为 `0.12.0-alpha.2`。** 玩家安装对应平台的本体与附属发布 JAR；`ender-infection-api-dev.jar` 用于编译和开发运行。

## 示例内容

- `institute_addon:research_console`：附属 Mod 自己注册的研究控制台。右键通过核心的设备 API 切换工作状态，开启时发出 12 级方块光，并显示状态提示。副手交互返回 `PASS`，避免一次点击切换两次。
- `institute_addon:analysis_room`：9 × 6 × 9 的分析室 NBT 模板，有地面、窗户、照明、工作台、显示器、样本架、储物箱和控制台。两端各有 3 × 3 门洞，连接器位于局部 `(4,1,0)` 和 `(4,1,8)`。
- 房间与设备在 Mod 构造时通过 `InstituteApi.registerAddon` 注册，核心完成 common setup 后关闭注册。
- 模板由 `generate_room.py` 统一生成两版的数据包目录。生成结果已包含在源码中，日常编译不需要 Python。

房间属于显式放置的模块示例。注册房间不会自动生成大型研究所，也不会修改已有存档。

## 目录

| 路径 | 内容 |
| --- | --- |
| `shared/src/main/java` | 同一份 API 注册和控制台逻辑 |
| `shared/src/main/resources` | 同一份模型、方块状态和中英文文本 |
| `forge/` | Java 17、Minecraft 1.20.1、Forge 47.3.12 |
| `neoforge/` | Java 21、Minecraft 1.21.1、NeoForge 21.1.265 |
| `generate_room.py` | 生成两版 NBT、掉落表及采掘标签 |

## 构建

先在仓库根目录 `E:\mc` 打开 PowerShell。Forge 构建使用 Java 17，NeoForge 构建使用 Java 21；把 `JAVA_HOME` 指向相应 JDK 并将其 `bin` 放入 `PATH`。

Forge：

```powershell
Push-Location ender_infection_colored
.\gradlew.bat apiDevJar
Pop-Location
Push-Location examples/institute-addon/forge
.\gradlew.bat build
Pop-Location
```

NeoForge：

```powershell
Push-Location ender_infection_neoforge
.\gradlew.bat apiDevJar
Pop-Location
Push-Location examples/institute-addon/neoforge
.\gradlew.bat build
Pop-Location
```

NeoForge 首次开发运行会下载 Minecraft 资源。可以在运行 Gradle 前指定缓存所在磁盘，例如 `$env:NFRT_ASSET_ROOT='E:/mc/build/minecraft-assets-1.21.1'`；本工作区的双版本验证使用这个位置。该设置用于开发资源缓存，不影响附属 JAR 内容。

产物分别是：

```text
forge/build/libs/institute-addon-forge-1.20.1-0.1.0.jar
neoforge/build/libs/institute-addon-neoforge-1.21.1-0.1.0.jar
```

工程默认读取仓库内对应平台的 `build/api/ender-infection-api-dev.jar`。将本示例整个目录复制到其他位置时，可显式指定开发依赖：

```powershell
.\gradlew.bat build '-PcoreDevJar=C:/dev/ender-infection-api-dev.jar'
```

这个开发 JAR 包含核心的开发类和资源，供编译及开发运行使用。构建时会先复制到本工程的 `build/coreDependency/`，让开发进程使用稳定快照。依赖通过 `compileOnly` / `runtimeOnly` 引用，构建出的附属 JAR 不会打包核心代码。正式运行需同时安装对应加载器的新核心构建、GeckoLib 和本附属 Mod；Forge 版还沿用核心的 mclib 运行依赖。两版 JAR 不能互换。

## 在开发环境验证

在对应的 `forge/` 或 `neoforge/` 目录运行：

```powershell
.\gradlew.bat runClient
```

运行不依赖手动操作的原生集成检查：

```powershell
.\gradlew.bat runGameTestServer
```

两项检查会实际预览/放置附属房间，并通过核心设备分发器检查主手切换、副手放行和 12 级照明。测试世界写入对应工程的 `build/addon-gametest-*`，不会使用玩家存档。Forge 的测试入口会在每次新目录中写入固定种子 `0` 的超平坦世界配置，关闭地形装饰与建筑生成，使测试场地上方保持空置；已有测试世界会被拒绝复用。NeoForge 使用其原生超平坦测试世界。

如需自动核对两版测试数量及附属 JAR 是否误打包核心代码，可在各工程内把 `build runGameTestServer` 的完整输出重定向到 `gametest-validation.log`，随后在本目录执行 `python verify_examples.py`。它要求两版都出现 `All 2 required tests passed`，会拒绝零测试或服务器启动失败的日志，并生成 `validation.json`。

创建测试世界并打开作弊权限。可先取出控制台检查右键切换和照明：

```mcfunction
/give @s institute_addon:research_console
/enderinstitute rooms
/enderinstitute devices
```

在已经加载的、9 × 6 × 9 范围完全空置的位置预览，再放置房间。例如在平坦空地上方：

```mcfunction
/enderinstitute preview institute_addon:analysis_room ~ ~1 ~ none
/enderinstitute place institute_addon:analysis_room ~ ~1 ~ none
```

命令需要权限等级 2。原点包含地板层；范围内有实体方块、流体或方块实体时，核心会拒绝放置，因此不要把原点设在已有地面内部。支持 `none`、`cw90`、`cw180`、`ccw90` 旋转，旋转围绕模板局部原点进行，范围可能延伸到原点的负方向。预览结果会给出实际包围盒和世界坐标连接器。

## 扩展示例

1. 复制 `AddonRegistration.register()` 中的房间定义，给每个房间分配自己 Mod 命名空间下的 ID，尺寸与 NBT 的 `size` 必须相同。
2. 在自己 Mod 的构造函数中调用注册函数；设备方块本身仍由对应加载器的注册系统注册。
3. 为设备绑定明确的方块 ID。处理器只在服务端执行：返回 `PASS` 保留正常交互，`CONSUME` 表示已处理，`FAIL` 表示失败。
4. 房间的标签使用完整资源 ID；连接器坐标必须位于模板内部。连接器是接口元数据，后续建筑拼装器可按 `socket` 匹配，核心当前不会据此自动扩建房间。
5. 保持两版共享代码一致，修改房间后运行 `python generate_room.py`，随后分别构建并验证两版。

示例代码使用 MIT 许可证，见 `LICENSE`。核心 Mod 自身的许可证及第三方建筑素材许可保持各自声明。
