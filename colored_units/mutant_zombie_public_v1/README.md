# 畸变僵尸：公开底模适配

底模为 [Purple zomie — @elma_taburlas](https://blockbenchworkshop.com/model/elma_taburlas/purple-zomie)，采用 [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。原作者声明 Human-created / Original work；本次修改由 AI 辅助。原作名称、作者、许可与修改记录随模组收入 `assets/ender_infection/third_party/model_credits.json`。

公开源 JSON 和原有晶蚀追猎者留在 `source`，可编辑 MCP 项目在 `models`，原始 MCP 导出在 `mcp_exports`。`provenance.json` 记录具体来源和取舍，`export-lock.json` 防止源文件、导出与验证版本错配。原作者的皮肤、衣物和紫色感染图案逐像素保留；没有把作品改署成本项目原创。

去掉原作的手枪及特效平面，缩小挡脸的感染组织，改成前倾上身和单侧感染。补齐肘、腕、膝、踝关节，分开两腿，沿用本项目旧模型有用的分节晶爪，并添加头侧、肩部、背部的晶体。最终 41 方块、18 骨骼、7 动作；未删成原版六块人形。两张源贴图合并为 256×128 图册，只让实际晶体 UV 发光，紫色皮肤保持普通材质。

| 动作 | 时间契约 |
|---|---|
| idle | 3 秒循环，呼吸与单侧手臂细动 |
| drag_walk | 1.2 秒拖步循环，独立膝踝 |
| claw_sweep | 第 12 tick 命中，总长 26 tick；旋身横扫 |
| heavy_slam | 第 18 tick 命中，总长 40 tick；举臂重砸 |
| crystal_burst | 第 28 tick 触发，总长 50 tick；蜷缩蓄力再展开 |
| infection | 32 tick 起身 |
| death | 18 tick 倒伏，在原版移除前结束 |

三种攻击各自有独立源动画，不再对同一段挥臂重复拉伸时间。注册 ID、生命、伤害、攻击范围、碰撞体和存档字段保持原有契约。死亡控制器两版同步接入，关闭原版额外的整身翻转。

`tools/export_mutant_rework.py` 负责对已验证的 MCP 输出做坐标缩放和材质打包，并检查原作贴图、动作名、命中时刻及发光。全量设计导出会再次应用此适配，避免旧晶蚀追猎者覆盖新版。

MCP 48 Hz 动画检查：7 段均为 0 错误、0 警告，无跳过表达式和近似 Bezier。几何检查有 22 个警告，主要为连接处包围盒重叠及刻意的不对称；无悬空、退化或缺失节点。两个 GeckoLib 元数据警告由最终的路径绑定和统一图册打包处理，未把编辑器检查冒充无警告。

已完成两版真实客户端、共 72 项原生测试、30 张截图及主模组／API／附属示例开发构建；另有 18 项导出回归通过。静态图与源码不能替代这组实景；[验收及原始记录](../../previews/mutant-public-v1/README.md)。攻击验收直接调用原有服务端 startAttack 选择招式，伤害仍由实际 tick 处理；普通 AI 同时由原生 AI 用例覆盖。当前仍为 alpha.8 开发分支，未公开发版，也未将增量检查称为干净发布门槛。
