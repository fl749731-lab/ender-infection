# 中央研究所专用家具

源码入口为 `tools/build_institute_furniture.py`。16 模型单位对应一个方块；独立的门、抽屉和隔帘骨骼用于实际开合，单方块储物家具使用 OPEN 烘焙状态。

包含玻璃样本柜、低温封存柜、服务器机柜、控制台、显微镜、离心机、冷却泵、工作灯、推车和隔帘，并重做现有柜子、工作台、抽屉柜、器材箱、货架及洗手台。白色瓷砖、蓝绿色玻璃和细色带对应研究所概念图的材质与功能分区。

`.bbmodel` 与 PNG 是可编辑的几何及逐面 UV 源副本；游戏资源导出到 Forge canonical assets，再由双版本同步脚本转换至 NeoForge。重新导出基础设计包后，再运行本脚本保留本轮家具修订。

```text
python tools/build_institute_furniture.py
python tools/sync_neoforge_resources.py
```
