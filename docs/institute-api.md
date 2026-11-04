# 研究所扩展 API v1

研究所 API v1 从 **v0.12.0-alpha.2** 开始提供，`v0.12.0-alpha.1` 不包含这些类。附属模组需依赖 `0.12.0-alpha.2` 或后续兼容版本。Forge 1.20.1 与 NeoForge 1.21.1 使用同一份 `common/src/main/java/dev/hui/enderinfection/api/institute` 源码，分别编译为各自平台的 JAR；一个平台的 JAR 不能直接装到另一个平台。

完整、可独立构建的附属模组示例见 [`examples/institute-addon`](../examples/institute-addon/README.md)。它注册自己的控制台方块和一个房间模板，不把本体代码打包进附属模组。

## 注册时机与命名

在附属模组的主类构造函数中调用 `InstituteApi.registerAddon`。各模组构造结束后，本体在 common setup 工作阶段冻结注册表；之后注册会明确报错。注册回调抛出异常时，该回调的所有新增内容均不提交。房间和设备 ID 必须属于传入的附属模组命名空间，重复 ID 或重复方块绑定会报错。

```java
InstituteApi.registerAddon("my_lab", registrar -> {
    registrar.room(new RoomDefinition(
        "my_lab:analysis_room", "my_lab:rooms/analysis_room",
        new RoomSize(9, 7, 9),
        List.of(new RoomConnector("entrance", 4, 1, 0,
            Direction.NORTH, "corridor_3x3")),
        Set.of("my_lab:laboratory")
    ));
    registrar.device(new DeviceDefinition(
        "my_lab:analysis_console", Set.of("my_lab:analysis_console"),
        context -> {
            context.player().displayClientMessage(Component.literal("控制台已响应"), true);
            return InteractionResult.SUCCESS;
        }
    ));
});
```

以上类型均在 `dev.hui.enderinfection.api.institute` 包中，方向、交互结果和聊天组件使用 Minecraft 对应类型。`InstituteApi.API_VERSION` 当前为 `1`。

`RoomDefinition` 的 `id`、`templateId`、`tags` 和 `DeviceDefinition` 的 `id`、`blockIds` 使用完整的 `namespace:path`。`RoomSize(x,y,z)` 每个维度允许 1～128 方块，必须与 NBT 模板实际尺寸完全一致。连接点的坐标从模板原点开始，必须位于尺寸范围内；同一房间内的连接点名称必须唯一。`socket` 是供附属模组自行匹配的接口类型标识，不会自动创建走廊。

`InstituteApi.rooms()` 和 `.devices()` 返回不可修改的快照，可供房间选择器、设备界面或其他模组发现已注册内容。加载器可能并行构造模组，因此不要依赖注册到达顺序；需要固定顺序的界面或按种子生成的拼装器应先按 ID 排序。

## 真实房间模板与放置

模板位置：

- Forge 1.20.1：`src/main/resources/data/<namespace>/structures/<path>.nbt`
- NeoForge 1.21.1：`src/main/resources/data/<namespace>/structure/<path>.nbt`

本体资源以 Forge 目录为唯一编辑源，由 `tools/sync_neoforge_resources.py` 转换到 NeoForge 构建目录。附属示例也以共享资源为源，在两个工程的构建任务中分别生成适合的目录。

`InstituteApi.preview(level, roomId, origin, rotation)` 计算旋转后的包围盒、世界坐标连接点和朝向，不修改世界。`InstituteApi.place(...)` 在相同检查通过后放置 NBT。两者返回 `PlacementResult`：`success`、可读的 `message`、`bounds` 和 `connectors`。模板缺失或未知 ID 时，`bounds` 可能为空。

检查包括：服务器主线程、已注册 ID、模板存在、尺寸一致、世界高度/边界、所有涉及区块已加载、目标区域没有固体或方块实体/流体。失败会在写入方块前返回；API 不会主动加载区块。放置忽略模板中的实体。调用方负责玩家权限、领地权限和自己的业务规则；API 本身不绕过区块检查，也不提供强制覆盖选项。

管理员命令需要权限等级 2：

```text
/enderinstitute rooms
/enderinstitute devices
/enderinstitute preview ender_infection:api_demo_room ~ ~1 ~ cw90
/enderinstitute place ender_infection:api_demo_room ~ ~1 ~ cw90
```

旋转参数可省略，支持 `none`、`cw90`、`cw180`、`ccw90`。原点对应模板的 `(0,0,0)`，旋转围绕原点，因此旋转后包围盒可能延伸到原点负方向。先在已加载的空旷区域执行 `preview`，再在同一位置执行 `place`。内置 `api_demo_room` 只是一个 9×7×9 的 API 演示房间，不是完整的 48 房间大型研究所，也不会加入自然生成。

## 设备交互

`DeviceContext` 提供服务端 `level`、`player`、`pos`、`state`、`hand` 和 `face`。本体在 loader 的 `RightClickBlock` 事件末级执行派发，跳过客户端、已取消事件、已拒绝方块交互的事件，以及玩家不能使用的位置。只有明确绑定到 `blockIds` 的方块才派发给扩展设备。

- 返回 `InteractionResult.PASS`：保留后续普通方块/物品交互。
- 返回 `SUCCESS` 或 `CONSUME`：接管此次交互。
- 返回 `FAIL`：终止此次交互。

处理器须在服务器线程中执行，避免阻塞。如果需要持久化状态，请使用附属模组自己的方块状态、方块实体或 SavedData；API 不会替设备保存任意数据。`PASS` 允许原版交互继续，因此附属模组应根据 `hand` 和自身逻辑决定是否处理副手。保护模组若在本体之后的相同事件优先级才修改结果，仍应遵循其 loader 的事件排序约定。

## 构建与验证

两个本体工程都提供 `apiDevJar` 任务，输出到各自 `build/api/ender-infection-api-dev.jar`。这是对应平台的开发类和资源，用于示例编译/开发运行；发布附属模组时不要把它 shade 或复制进附属 JAR。最终玩家安装本体正常发布 JAR 和对应平台的附属 JAR。

Forge 的隔离验证命令为：

```text
gradlew runGameTestServer -PinstituteChecks
```

该测试运行单独注册缺模板、错误尺寸和可控设备夹具，不会在普通游戏进程中注册这些内容。测试覆盖注册事务、不可变快照、冻结、连接点校验、设备重复绑定、实际 NBT 放置/旋转、预览不写、占用保护、未加载区块保护，以及 loader 事件的成功、PASS、取消和拒绝派发。原有战斗回归使用 `-PriftChecks`。
