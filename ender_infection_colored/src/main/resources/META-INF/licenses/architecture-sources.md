# 导入建筑来源

这些是用户要求直接复用的上游建筑，不声明为末影感染原创建筑。

| 文件 | 作者与来源 | 许可 |
|---|---|---|
| `hbm/laboratory.nbt`、`factory.nbt`、`abandoned_arcticstation.nbt` | HbmMods / HBM's Nuclear Tech；[原始结构目录](https://github.com/HbmMods/Hbm-s-Nuclear-Tech-GIT/tree/master/src/main/resources/assets/hbm/structures) | 本次取得的仓库 LICENSE 为 GPL-3.0，原文见 `hbm/LICENSE` |
| `church/church_of_sin.nbt` | bulochka_s_tiltom；[Church of Sin — Vanilla](https://modrinth.com/mod/dark-fantasy-church-of-sin-vanilla)，1.0.0 | 作者页面与 JAR 的 mods.toml 均声明 MIT，见 `church/LICENSE` |

2026-11-03 转换：保留原建筑坐标、体量与主要构造。HBM 旧版方块按 `tools/import_architecture.py` 显式映射为 1.20.1 原版或本 Mod 装饰方块，重建门的占位和栏杆连接。容器改用本项目或原版战利品表；移除上游实体、刷怪器的自定义 NBT（原有刷怪器方块替换为哭泣的黑曜石）、指令与绘画实体；不包含原 Mod 的 Java、声音和贴图。教堂底部不可破坏的基岩替换为深板岩。

原文件在本目录保留，转换文件位于 `data/ender_infection/structures/imported/`。修改后的 HBM 结构与映射转换工具按上游 GPL-3.0 提供；教堂结构延续 MIT。各资产的独立许可不表示其他项目代码或资产的许可发生改变。发布包内附来源与许可。

Lua Lab 作者为 LuaKatt / Luakkat_，署名 ChaparritaStudios，[作者页](https://www.planetminecraft.com/project/lua-lab/)。下载与转换结果放在被 Git 忽略的 `imports/`；该页面没有注明再分发许可，公开仓库不包含原文件或其转换件。
