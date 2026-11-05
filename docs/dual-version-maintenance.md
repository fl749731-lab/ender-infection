# 双版本构建与维护

主模组当前版本：0.12.0-alpha.6。Forge 1.20.1 使用 Java 17，NeoForge 1.21.1 使用 Java 21。

## 源码与资源

| 内容 | 源位置与规则 |
|---|---|
| 公共研究所 API v1 | `common/src/main/java`；保持 Java 17 兼容 |
| Forge 玩法 | `ender_infection_colored/src/main/java` |
| NeoForge 玩法 | `ender_infection_neoforge/src/main/java`；对应功能与测试同时维护 |
| 模型、贴图、动画与数据 | Forge `src/main/resources` 为源，由 `tools/sync_neoforge_resources.py` 转换到 Neo 的 `build/generated` |
| 主模组版本 | Forge `build.gradle` 为准，Neo 自动读取 |
| API 附属示例 | `examples/institute-addon`，公共 API 变化后也要验证两版 |
| 共振工业 | `addons/resonant-industry`；独立版本、独立 JAR，使用自己的共享源转换与验证入口 |

不要手改 Neo 生成目录，也不要通过移除功能或测试让另一版编译通过。

## 主模组发布门槛

设置 `JAVA17_HOME` 和 `JAVA21_HOME`，在仓库根目录运行：

```text
python tools/build_dual_version.py --clean --gametests
```

此入口检查布局、共享源和注册 ID，构建两版主模组、API 开发包与附属示例，运行原生测试及 E8 的真实服务器重启／区块卸载验收。当前 alpha.6 通过 18 组共 163 项必需测试、每版 3 次专用服务器启动；另保留 32 张客户端截图。[完整验收记录](../previews/incident-alpha6/README.md)。

结果位于 `build/dual-version-validation`。后续实现变化需要重新验证；历史版本的通过结果不替代新代码验收。原生测试必须核对实际执行数，零项不算通过。

## 共振工业

```text
python addons/resonant-industry/tools/build_dual.py --clean --gametests --clients
```

附属的构建、接口、图纸、打包和独立 qegg 发布流程见[共振工业说明](../addons/resonant-industry/README.md)。

## 本地开发约定

- 两版各自使用工程 `build` 目录下的新测试世界，不把已有游戏实例或世界当成迁移目录。
- NeoForm 资源缓存默认在 `build/minecraft-assets-1.21.1`，可用 `NFRT_ASSET_ROOT` 覆盖。
- 内存不足时可设置 `ENDER_GRADLE_JVM_ARGS` 和 `ENDER_GRADLE_INIT_SCRIPT`；只调整进程资源，不删减检查集合。
- 建筑通过图纸、方块坐标和原生结构维护；实体美术通过可编辑模型与匹配的运行时导出维护。

历史发行和旧设计附件见[历史资料](history.md)。
