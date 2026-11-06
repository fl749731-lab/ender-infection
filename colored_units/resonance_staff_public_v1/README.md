# 共振法杖：公开底模改造

底模为 [Void Staff — @alexander](https://blockbenchworkshop.com/model/alexander/void-staff)，[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)。作者声明为原创作品；网站不独立核实该声明。`source/void-staff.viewer.json` 保存公开预览模型，来源 URL 和 SHA-256 见 `provenance.json`。未绕过登录下载，也未引入其他作者的未授权美术。

保留作者的 25 块握柄、金属连接件和叉形杖头，原始 64×64 材质像素不变。导入时将公开预览的半尺寸 UV 转为实际像素坐标，再无损打包进 256×128 图集。移除原方块球体和反向壳体，使用本项目既有紫晶材质制作三段晶核；晶体采用连续不透明区域、每模型单位 0.5 像素的低频渐层。金属使用原有约每单位 1 像素的作者绘制纹理，端盖保留原图的映射差异。

最终模型为 28 方块、4 骨骼，建模、UV 改动、动作和几何／动画导出均通过 Blockbench MCP 完成。`models` 是可编辑源，`mcp_exports` 是原始 MCP 编译结果，`export-lock.json` 锁定三份文件；`tools/export_staff_rework.py` 只负责材质打包、统一尺寸和运行路径。所有改造为 AI 辅助制作。

四秒待机动作使晶核慢转、轻微悬浮；一秒施法动作让两侧叉臂张开，晶核放大、旋转并复位。没有预蓄力时间：原技能仍在点击时立即发射，动作表现释放和余振。动画由服务器成功施法后触发，每个物品栈使用独立 GeckoLib ID；冷却失败不触发。

注册 ID `ender_infection:resonance_staff`、原有 10 点投射物伤害、25 tick 冷却、每次 2 点耐久、合成与存档兼容保持原实现。Forge 使用 `IClientItemExtensions`，NeoForge 使用当前 GeckoLib 4.9.3 的 `GeoRenderProvider`。紫晶才进入发光蒙版，金属／握柄不自发光；未添加环境动态光源。

MCP 48 Hz 动作检查无错误／警告、无跳过表达式或曲线。静态几何有 8 个警告：连接处保留的 AABB 重叠与原始左右命名未配对；无反向、退化或散落方块。不能将此说成静态零警告。原始报告见 `mcp-verification.json`。

游戏内验收和源文件、截图、构建摘要见 [运行记录](../../previews/staff-public-v1/README.md)。这属于 alpha.8 开发内容，未创建新公开发行。
