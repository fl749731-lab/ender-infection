# 第二批生物改造：畸变蜘蛛

alpha.8 开发内容，尚未作为公开发行发布。Forge 1.20.1 与 NeoForge 1.21.1 共用资源。

原模型：[Spider — @elma_taburlas](https://blockbenchworkshop.com/model/elma_taburlas/spider)，[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。作品页声明 Human-created；本次适配为 AI 辅助。完整公开 viewer JSON 留在 `source`，来源地址、原始 SHA256 和修改项见 `source-provenance.json`。原作者的作品未改为本项目原创。

保留原作的条纹、八只分节足、足尖、红眼和口器；腹部向后展开，晶体长在腹部和头侧，避免堆成直立方柱。新增六处晶体生长点，共 69 个方块、61 个骨骼。原始身体贴图像素直接保留，新增晶体沿用本模组原有素材，合并为 256×128 图册；只让实际使用的晶体 UV 发光，身体和红眼不加入发光蒙版。

| 动作 | 运行契约 |
|---|---|
| 待机 | 合并原作的呼吸、眨眼和口器细动；首尾闭合 |
| 行走 | 八足分节交替，0.8 秒循环；首尾闭合 |
| 攻击 | 1.5 秒，0.7 秒对应现有第 14 tick 命中；不改变战斗数值 |
| 感染 | 1.6 秒，与现有出场控制器对应 |
| 死亡 | 0.9 秒收足倒伏，在原版 20 tick 移除前结束；关闭重复的原版整身翻转 |

可编辑项目通过 Blockbench headless MCP 创建、调整、检查并导出。`mcp_exports` 为 MCP 原始几何／动画导出；`export-lock.json` 锁定项目与两份导出的 SHA256。`mcp-verification.json` 保留检查结果。导出器按材质合图、平移 UV、归一尺寸并绑定运行 ID；不从脚本重新生成本体几何。

240 FPS 的编辑吸附精度用于保留 24／48 Hz 采样和 20 Hz 的命中时点。动画以 48 Hz 检查，0 错误、0 警告；几何检查有 113 个深层包围盒重叠警告，原作基线为 91 个，主要是分节连接、眼部和新增晶体的嵌入。几何 0 错误；独立同向共面面检查没有重复覆盖。这些包围盒警告并未宣称全部消除。

`infected_spider` 注册 ID、2×1.6 游戏碰撞体、攻击范围和伤害保持原样；展开足部的视觉宽度约 3.02 格，属于原种蜘蛛的外伸足。初期感染蜘蛛是另一个形态，本批尚未替换。

```text
python tools/export_creature_reworks.py --verify
python tools/test_creature_reworks.py
python tools/validate_design_assets.py
```

修改项目后，先通过 MCP 重新检查／导出并更新锁定记录，再运行导出器。Forge 资源为源，经 `tools/sync_neoforge_resources.py` 转换到 NeoForge；禁止手改 Neo 生成目录。完整资源导出与旧的 silhouette 重建入口均保留本批和上一批的 MCP 替换资源。

[双版运行实景与验收记录](../../previews/creature-rework-v2/README.md)。
