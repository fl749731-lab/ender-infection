# 研究所扩展 API v1

研究所 API v1 从 **v0.12.0-alpha.2** 开始提供，`v0.12.0-alpha.1` 不包含这些类。基础房间和设备接口可依赖 `0.12.0-alpha.2` 或后续兼容版本；**加工配方、研究所供电查询和事件订阅是从 `v0.12.0-alpha.4` 开始追加的 v1 接口**，使用它们的附属需将最低核心依赖设为 `[0.12.0-alpha.4,)`。`API_VERSION` 仍为 `1`，已有 v1 附属无需改写。公共契约保持 Java 17 兼容。Forge 1.20.1 与 NeoForge 1.21.1 使用同一份 `common/src/main/java/dev/hui/enderinfection/api/institute` 源码，分别编译为各自平台的 JAR；一个平台的 JAR 不能直接装到另一个平台。

完整、可独立构建的附属模组示例见 [`examples/institute-addon`](../examples/institute-addon/README.md)。当前示例要求 `alpha.4`，注册自己的控制台方块、一个房间模板和一项加工配方，不把本体代码打包进附属模组。

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

## 研究所状态查询与事件（alpha.4 起）

`InstituteApi.facility(ServerLevel, BlockPos)` 返回当前位置所属研究所的 `Optional<FacilityStatus>`，只读查询不会修复、供电或绑定附属设备。必须在服务端主线程调用；没有登记的研究所时返回空值，不能把空值当作“此研究所未供电”。可在设备处理器中查询：

```java
InstituteApi.facility(context.level(), context.pos()).ifPresent(status -> {
    context.player().displayClientMessage(Component.literal(
        "服务区供电：" + status.servicePowered()
        + "，收容区供电：" + status.containmentPowered()), true);
});
```

`FacilityStatus` 是不可变记录，包含原点 `origin()`、修复状态 `repaired()`、两区供电 `servicePowered()` / `containmentPowered()`、分析进度 `analyzed()`、分离进度 `separated()` 和档案领取状态 `archiveClaimed()`。服务区对应 G1、G2、G3、B1，收容区对应 B2、B3。`InstituteApi.isPowered(level, pos)` 只查询布局清单已登记的受控设备坐标；普通房间坐标和自定义设备位置返回 `false`，加工机器还必须具有与该位置对应的自身设施绑定。自定义设备应通过 `facility(level, pos)` 返回的 `servicePowered()` / `containmentPowered()` 只读查询两区状态，这不会自动将其接入电网。示例控制台只显示状态，仍使用自己的开关与照明。

通过 `InstituteApi.onFacilityChange(Consumer<FacilityEvent>)` 订阅变化，返回的 `AutoCloseable` 用于移除监听器。事件在服务端线程中、状态持久变更之后触发，提供 `level()`、变更前 `previous()`、变更后 `current()` 和原因 `reason()`。处理器应快速返回；需要保存附属自己的进度时，仍使用附属自己的存储。不要依赖原因字符串来取代状态字段判断。

```java
// 保存在附属的服务端生命周期对象中，避免重复订阅。
private AutoCloseable facilitySubscription;

public void startFacilityListener() {
    if (facilitySubscription != null) return;
    facilitySubscription = InstituteApi.onFacilityChange(event -> {
        if (!event.previous().servicePowered() && event.current().servicePowered()) {
            // 在这里通知附属自己的设备；事件本身不会绑定或改写它们。
        }
    });
}

public void stopFacilityListener() throws Exception {
    if (facilitySubscription != null) {
        facilitySubscription.close();
        facilitySubscription = null;
    }
}
```

在服务端启动时订阅，在对应服务器停止时关闭；同一进程再次开世界时应建立新订阅。监听回调中的异常会被本体记录，不应将异常作为控制后续监听器执行的方式。

## 扩展加工配方（alpha.4 起）

在构造期的同一个 `registerAddon` 回调中调用 `registrar.processing`。当前示例给本体显微镜追加一项使用纸的配方，不会匹配原始组织样本的处理流程：

```java
InstituteApi.registerAddon("institute_addon", registrar -> {
    registrar.processing(new ProcessingRecipe(
        "institute_addon:archive_copy",
        Set.of("ender_infection:lab_microscope"),
        "minecraft:paper", 2,
        "minecraft:redstone", 1,
        "minecraft:book", 1,
        80
    ));
});
```

`ProcessingRecipe` 按顺序接收 `id`、`devices`、`inputId`、`inputCount`、`reagentId`、`reagentCount`、`outputId`、`outputCount`、`ticks`。设备和物品使用完整资源 ID；配方 ID 属于附属自己的命名空间，设备集合可引用本体已实现的加工设备。无试剂配方使用空字符串 `""` 和数量 `0`。输入、产物及非空试剂的数量为 1～64，设备集合为 1～16 项，耗时为 20～72000 tick。80 tick 在正常 20 TPS 下为 4 秒。

`InstituteApi.processingRecipes()` 返回不可修改的快照，可按 ID 发现和展示配方。注册仍受冻结、命名空间和事务校验约束。该接口注册加工规则，不会把任意方块自动变成加工机器；实际处理由设备实现负责，并遵守其供电、输入/试剂消耗与产物槽规则。附属应避免为同一设备注册含义相同的输入组合，不依赖模组构造顺序选择配方。

## 构建与验证

两个本体工程都提供 `apiDevJar` 任务，输出到各自 `build/api/ender-infection-api-dev.jar`。这是对应平台的开发类和资源，用于示例编译/开发运行；发布附属模组时不要把它 shade 或复制进附属 JAR。最终玩家安装本体正常发布 JAR 和对应平台的附属 JAR。

Forge 的隔离验证命令为：

```text
gradlew runGameTestServer -PinstituteChecks
```

该测试运行单独注册缺模板、错误尺寸和可控设备夹具，不会在普通游戏进程中注册这些内容。测试覆盖注册事务、不可变快照、冻结、连接点校验、设备重复绑定、实际 NBT 放置/旋转、预览不写、占用保护、未加载区块保护，以及 loader 事件的成功、PASS、取消和拒绝派发。原有战斗回归使用 `-PriftChecks`。
