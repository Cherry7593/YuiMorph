# YuiMorph 需求范围

> 参考：[NiFeather/FeatherMorph](https://github.com/NiFeather/FeatherMorph)（已归档，`mc_version=26.1.2`，PacketEvents 渲染）
> 确认日期：2026-10-18

## 目标平台与技术栈

| 项 | 选择 |
|---|---|
| 服务端 | **Shiroha 26.3**（Folia 下游分支，[ShirohaDevelop/Shiroha](http://repo.zakozako0721.lol:38080/ShirohaDevelop/Shiroha) `dev/26.3`） |
| 编译 API | `dev.folia:folia-api:26.3.build.3-alpha`（repo.papermc.io 已发布；不依赖 Shiroha 专有 API，`paper-plugin.yml` 声明 `folia-supported: true`） |
| 测试服 | 本地测试服，目录由 `yuimorph.testServerDir` 指定（换内核用 `start.sh` 的 `-jar` + `restart-folia.sh`） |
| 客户端 | Fabric 26.3 Mod（**可选增强**，不装也能完整游玩） |
| 语言 / 构建 | Kotlin + Gradle（Kotlin DSL） |
| JDK | Java 25（MC 26.3 最低要求） |
| 渲染 | PacketEvents 2.14.0+（已支持 26.3 与 Folia） |
| 存储 | 本地 JSON 文件 |
| 文本 | 中文为主，全部抽到语言文件（预留多语言） |
| 工程结构 | 单仓库多模块：`protocol`（共享协议）+ `plugin`（Folia）+ `mod`（Fabric） |

## 功能范围（保留）

### 伪装核心
- **A1** 伪装为原版生物 / 玩家，服务端 PacketEvents 发包渲染虚拟实体
- **A3** 伪装属性：变种、颜色、大小、自定义名、玩家皮肤
- **A4** 伪装动作（各生物专属动画）
- **A5** 环境音效；碰撞箱随伪装改变（实验性，与反作弊冲突需可关闭）
- **A6** 按生物类型缩放最大血量（不改变 / 不超过玩家上限 / 不限制）
- **A7** 离线伪装状态恢复；进服强制伪装
- **A8** 怪物仇恨：伪装成生物后，怪物只主动攻击原版里会攻击的生物（只取消索敌事件，不改 AI；设计见 [`MOB_AGGRO.md`](MOB_AGGRO.md)）

### 获取与管理
- **B1** 击杀生物 / 玩家解锁伪装（支持实体标签排除）
- **B2** 玩家间伪装交换请求（send / accept / deny）
- **B5** 管理命令：发放、撤销、强制伪装 / 解除、查询、按物种 / 分类封禁

### 交互
- **C2** 箱子 GUI 伪装选择（无 Mod 玩家使用）
- **C3/C4** 玩家命令 `/morph` `/unmorph` `/request` 等 + 权限节点体系

### 客户端 Mod（G2）
- 客户端 ↔ 服务端自定义协议（`protocol` 模块共享定义，带版本协商）
- 第一 / 第三人称看到自身伪装
- 原生伪装选择界面（替代箱子 GUI）
- 快捷键：打开选择 / 取消伪装 / 播放动作
- 状态 HUD（当前伪装等）

## 明确不做
主动技能、被动能力、魔法瓶、头颅变身、伪装工具物品、生物 AI 修改（包括被动生物躲避、新增仇恨）、揭穿机制（揭示值）、交互镜像、Waypoint 伪装、多实例同步、第三方插件集成（PAPI/Towny 等）、对外 API/事件、伪装属性编辑 UI（Mod 侧）。
