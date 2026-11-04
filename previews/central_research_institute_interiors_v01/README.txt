中央研究所 六层与48间内饰图册 v01

下载 v0.12.0-alpha.2 附件
完整图包（PNG、SVG、CSV、浏览索引）：
https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.2/central-research-institute-interiors-v01.zip
73页 PDF：
https://github.com/fl749731-lab/ender-infection/releases/download/v0.12.0-alpha.2/central-institute-interior-atlas-v01.pdf

源仓库保存概念图、提示词、方块图例、布局校验和生成脚本。完整逐室图、CSV及PDF作为Release附件提供。
下载完整图包并解压后，打开 index.html 按楼层浏览；下文的 rooms/、floors/、guides/、csv/ 均指解压后的目录。
PDF 单独提供，避免在完整图包内重复下载。
本图包含48间逐室 PNG/SVG、6层总图、12张四室概念图、方块图例与公共交通说明、逐室及总表 CSV。
尺寸、坐标与实际方块摆放以逐格图和 CSV 为准；概念图是细节目标，并非游戏截图。

摆放顺序
1. 用整层图确定房间原点。U向东、V向南，H0是本层地板方块。
2. 放置地板、墙面、顶棚；普通楼层净高5格，B3净高9格。
3. 按M编号摆放家具模块。图中蓝点是家具根方块，自动占位部分不要重复摆物品。
4. 按吊顶图摆灯和管线，核对门、两格主通道、楼梯及货运井净空。
5. 使用 csv/房号.csv 查完整命名空间、朝向、状态、建筑坐标和手动物品数。

文件用途
rooms/：逐室平面、吊顶、北墙、家具原点和材料数量。
floors/：六层位置与本层材料、高度。
concepts/：图像生成的室内细节参考；prompts.json 保存完整文字提示。
guides/05_compatibility：现有方块、组合搭建、待补模型的对照。
csv/ALL_ROOMS.csv：86,906条方块根位置/摆放记录，含自动占位元数据。
room_placements.json：程序可读坐标；placement_validation.json：48间布局检查。
manifest.json：交付文件的字节数与 SHA256。

落地范围
66种方块ID来自当前Forge1.20.1工程和原版。显微镜、轮式推车、完整安检机、软帘和无缝大屏等尚无独立模型，按对照页替代。
组合供能装置、冷却泵组、服务器等在此图册中表达外观；功能由后续设备代码实现。
路径检查是设计网格检查，不等同于游戏寻路测试。大型研究所本体仍待组装和自然生成接入。
紫晶区域使用灯板/海晶灯补光；原版紫晶本身不因这份设计获得方块发光属性。

本交付包按用户要求使用图纸与概念图展示，不依赖 Blockbench。

从源码重新生成
在仓库根目录运行脚本，需要 Python 3.10或以上、Pillow；生成PDF还需要reportlab。
脚本默认使用 Windows 的微软雅黑字体 C:/Windows/Fonts/msyh.ttc 和 msyhbd.ttc。
家具模型输入使用仓库现有的 colored_units/design_pack_2026-11-02_v2/laboratory 与 colored_units/lab_details_v3。
原版方块模型与贴图读取本机 Minecraft 1.20.1 客户端JAR，预期路径：
Minecraft1201/libraries/com/mojang/minecraft/1.20.1/minecraft-1.20.1-client.jar
该客户端JAR不随源码或图包分发；请从自己的Minecraft安装中提供。
概念图来源采用 concepts/provenance.json 的仓库相对路径，不依赖作者机器的生成图片目录。

python -m pip install pillow reportlab
python tools/institute_room_atlas.py --pdf

打包脚本 tools/package_institute_atlas.py 另外要求完整图册和 build/institute-atlas-proof/page-01.png 至 page-73.png 已完成PDF渲染检查。
