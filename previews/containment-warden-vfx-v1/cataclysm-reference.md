# 灾变特效对照与采用点

2026-11-06 按用户要求，检查官方仓库的粒子实现与 `em_pulse`、`shock_wave`、`scylla_swing` 参考贴图。本模组的运行时资源均由本仓库生成；没有把灾变的源码、PNG 或改色版本放进资源目录或 JAR。

| 参考对象 | 观察点 | 收容监管体的实现 |
|---|---|---|
| [RingParticle](https://github.com/lender544/new1.20.1/blob/master/src/main/java/com/github/L_Ender/cataclysm/client/particle/RingParticle.java) | 环的朝向、尺寸变化与消散共同塑造空间感 | 预警固定在地面，释放后低矮波纹扩散；核心断环固定在胸前 |
| [Shock_WaveParticle](https://github.com/lender544/new1.20.1/blob/master/src/main/java/com/github/L_Ender/cataclysm/client/particle/Shock_WaveParticle.java) | 平面波纹贴图扩张，少量几何也能形成完整冲击面 | 自制不规则波纹遮罩，紫色外波与冷白内波错开，替换单一细分段环 |
| [Scylla_Swing_Particle](https://github.com/lender544/new1.20.1/blob/master/src/main/java/com/github/L_Ender/cataclysm/client/particle/Scylla_Swing_Particle.java) | 短促的弧形横扫和快速碎散 | 保留右手三条独立斩痕，增加外扩与碎屑，继续对齐第 17 tick |
| [AbstractTrailParticle](https://github.com/lender544/new1.20.1/blob/master/src/main/java/com/github/L_Ender/cataclysm/client/particle/AbstractTrailParticle.java) | 沿运动路径形成连续拖尾，避免只看见离散亮点 | 三次脉冲各由相邻曲面片形成连续窄扇面，外加低矮亮边，替换竖直细线列 |

胸口另加入四枚向内聚拢的亮点，表示脉冲蓄力。配色继续为紫晶与冷白；琥珀色专用于危险预警，薄荷色专用于抑制台成功反馈。伤害逻辑不随视觉扩散增加二次判定。

用户进一步指出缺少波浪起伏后，脉冲改为有后坡、波峰、前卷和落坡的立体网格，沿窄扇区前进并逐渐隆起、塌下。跺地也在贴地波纹上加入起伏波脊。当前的变化同时涉及几何和时间曲线。

模型本体另检查了[末影守卫模型构造及骨骼](https://github.com/lender544/new1.20.1/blob/819937203e22b4fa72fdeccc6c56ad415d65eeef/src/main/java/com/github/L_Ender/cataclysm/client/model/entity/Ender_Guardian_Model.java)：上身主块包住小头，前臂与拳分开旋转，胸腹与腿分层承担重心。完整模型网站与可修改候选见[公开模型资源](../../docs/public-minecraft-models.md)。

官方 [README](https://github.com/lender544/new1.20.1) 与 [CurseForge 授权页](https://www.curseforge.com/minecraft/mc-mods/lendercataclysm/license)列出素材权利保留及源码 CC BY-NC-ND 4.0。本次使用范围是阅读和视觉研究，采用一般的分层、扩张和消散表现方法，独立编写运行实现、生成新的像素遮罩。原素材直接改色不属于本次交付。
