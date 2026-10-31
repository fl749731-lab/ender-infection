> 当前文件位置：工程 `E:\mc\ender_infection_colored`；可编辑模型 `E:\mc\colored_units`。

# 末影感染 0.2.1

新增植物的初期保色侵蚀、进一步侵蚀及完全感染，修复菌丝体上方植物阻断地表扩散；自动扩张节点替换地表方块。完全畸变单位获得不同晶刺技能，骷髅改用魔法阵与水晶施法模型，初期骷髅仍使用弓箭。

详细范围、规则和调试指令见《植物侵蚀与晶刺技能_0.2.1.md》。五阶段母体、生态收入、AI、Boss、掉落和配方见《生态与高阶母体_0.2.0.md》。50 项隔离 Forge GameTest 全部通过，资源检查结果见 verification/RESULTS.txt。贴图、模型离线预览不代表客户端实机验收。

Minecraft 1.20.1 / Forge 47.3.12 / Java 17 / GeckoLib 4.4.9。运行 `.\gradlew.bat build` 编译；`.\gradlew.bat runGameTestServer` 会在 build 下创建全新隔离测试世界。玩家存档没有用于自动测试。

安装状态见工作区 outputs/安装状态.txt；重启 Minecraft 后才会加载新 JAR。
