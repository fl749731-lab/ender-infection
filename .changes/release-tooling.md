---
type: chore
scope: build
packages:
  java@ender_infection_colored: patch
---

将 qegg 发布、PR 和状态检查工作流同步升级到 v0.12.0。本地构建脚本支持通过环境变量设置 Gradle 守护进程内存和初始化脚本，便于内存受限机器执行完整双版本构建与原生测试；默认行为和测试集合保持不变。
