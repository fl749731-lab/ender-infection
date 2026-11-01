# Changelog

<!-- qegg:release 0.7.0 -->
## v0.7.0 - 2026-11-01

### Features

- [`dc95ad2`](https://github.com/fl749731-lab/ender-infection/commit/dc95ad2746d3ca7b8acce847a716f42885694c2a): 畸变单位每次出手会从多种攻击模式中随机选择：重砸、前冲、范围震荡与战吼（给周围友军加速和力量）。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.6.0...v0.7.0
<!-- qegg:release:end -->

<!-- qegg:release 0.6.0 -->
## v0.6.0 - 2026-11-01

### Features

- [`bd19b44`](https://github.com/fl749731-lab/ender-infection/commit/bd19b4426606afda308df27f1142b0a302092fe3): 畸变单位整体加强：血量与伤害上调约 50–80%，新增护甲，攻击间隔从 35 tick 缩到 26 tick；母体与攻城兽受到的单次伤害按最大生命封顶（母体 8%、攻城兽 10%），不会再被一击带走。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.5.2...v0.6.0
<!-- qegg:release:end -->

<!-- qegg:release 0.5.2 -->
## v0.5.2 - 2026-11-01

### Bug Fixes

- [`8103c62`](https://github.com/fl749731-lab/ender-infection/commit/8103c6257735f1dd6b7b19a83582fcb33647f45b): 孵化与地表产卵在和平时期必须给节点留 16 生物质，扩张不再被自家兵源饿死；母体受袭时该保底解除。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.5.1...v0.5.2
<!-- qegg:release:end -->

<!-- qegg:release 0.5.1 -->
## v0.5.1 - 2026-11-01

### Bug Fixes

- [`32a6a30`](https://github.com/fl749731-lab/ender-infection/commit/32a6a30a1bfb656c5a264afa32412fb85a9de5c9): 雪层盖住的感染地表现在照常计入土地收入；之前雪会让整片殖民地收不到生物质，扩张因此停摆。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.5.0...v0.5.1
<!-- qegg:release:end -->

<!-- qegg:release 0.5.0 -->
## v0.5.0 - 2026-11-01

### Features

- [`0c5722f`](https://github.com/fl749731-lab/ender-infection/commit/0c5722fa1c65939d2a6cd45abb46abfc9bf47d64): 进入被侵蚀区域的所有非本阵营单位都会获得末影侵蚀：凋零、反胃，以及本模组自己的末影菌丝感染（持续伤害、减速、饥饿）。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.4.1...v0.5.0
<!-- qegg:release:end -->

<!-- qegg:release 0.4.1 -->
## v0.4.1 - 2026-11-01

### Bug Fixes

- [`e42e71e`](https://github.com/fl749731-lab/ender-infection/commit/e42e71eb23abb6d0f9968aad17b69e26371cff3f): 侵蚀的垂直扫描从 +3/-4 扩到 +3/-10：雪坡、坑底和落差较大的雪面现在也会被侵蚀，地表雪层不再阻挡转换。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.4.0...v0.4.1
<!-- qegg:release:end -->

<!-- qegg:release 0.4.0 -->
## v0.4.0 - 2026-11-01

### Features

- [`f13d23c`](https://github.com/fl749731-lab/ender-infection/commit/f13d23c8413778dc5c0f5cd980a4b3dd270f7a90): 雪块、细雪与冰现在和其它地面一样会被侵蚀，盖在地表上的雪层会被一并化掉。
- [`f13d23c`](https://github.com/fl749731-lab/ender-infection/commit/f13d23c8413778dc5c0f5cd980a4b3dd270f7a90): 三阶之后母体对所有非本阵营生物宣战：守卫也会猎杀动物，猎手不再受活动范围束缚。

**Full Changelog**: https://github.com/fl749731-lab/ender-infection/compare/v0.3.0...v0.4.0
<!-- qegg:release:end -->

<!-- qegg:release 0.3.0 -->
## v0.3.0 - 2026-11-01

### 新功能

- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 猎手会循着可达的猎物气味外出，而不是只在视线内才追；每只母体新增攻击性与四臂经济策略学习，战果会改变嗅觉半径、围攻上限以及孵化/扩张/进阶/储备之间的取舍。
- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 三阶后不再限制节点数量与铺设距离；成熟地表会自己长出感染植物，并能让母体付出生物质产出单位。
- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 攻城兽模型、动画与粒子重做，并修复冲击波漏伤：伤害带窄于波前步长，导致部分距离永远打不中。

### 修复

- [`04612f3`](https://github.com/fl749731-lab/ender-infection/commit/04612f3f33e0288c1eb6e6f814c355bfae1e3765): 地表改为整列向下侵蚀，悬崖与挖开的洞不再露出原版泥土；成熟化改走独立且免费的通道，老地块会真正推进到深色成熟地表。

### 杂项

- [`657a68d`](https://github.com/fl749731-lab/ender-infection/commit/657a68dce2e62d977e8063d049e1aac63f8d206b): Automate version bumps and GitHub releases for the mod.
<!-- qegg:release:end -->
