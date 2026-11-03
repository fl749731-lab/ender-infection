---
type: fix
scope: rendering
packages:
  java@ender_infection_colored: patch
---

修复实验室通风口等完整设备的全透明发光蒙版导致 GeckoLib 注册纹理崩溃；为全部发光渲染层增加有效像素检查和重载缓存清理，导出器不再生成空蒙版，客户端验证实际注册发光纹理。
