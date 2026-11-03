# 区块生成循环等待修复

修复前在独立自然生成世界复现。服务端等待区块完成，而 Worker-Main 灯光任务沿以下调用链再次请求相邻区块：

```
BlockLightEngine / LightEngine.getOpacity
BlockBehaviour.getLightBlock
Block.propagatesSkylightDown
LabPartBlock.getShape
Level.getBlockState
ServerChunkCache.getChunk
CompletableFuture.join
```

占位方块现在覆盖光照与遮挡方法，固定为透明/空形状，不读根方块。根方块未加载时碰撞查询返回空形状；移除占位与设备时不强制加载邻区块。严格 BlockGetter 回归会在任何邻方块读取时立即失败。

原研究站与恶地布局修复后各通过 36 个 FULL 区块自然生成验证。最终导入的教堂和 HBM 实验室各通过 56 个 FULL 区块验证；教堂检查 11,428 个主要建筑方块。新选址在不读取真实区块的前提下比较噪声生成器的地表和水底高度，并检查占地九点高差，跳过水域和超过 5 格高差的场地。

原始本地证据：`build/natural-worldgen-before.log`、`natural-worldgen-thread.txt`、`natural-worldgen-after.log`、`natural-worldgen-badlands.log`、`natural-final-church.log`、`natural-imported-lab-dry.log`。截图见仓库 docs/images。测试未打开或修改玩家存档。
