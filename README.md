# YuiMorph

[English](README_en.md) | 中文

Minecraft 伪装插件：让玩家伪装成原版生物或其他玩家。服务端插件基于 Folia + PacketEvents，另有可选的 Fabric 客户端 Mod。

<!-- 效果图占位：请替换为 GIF（见下方"需要录制的画面"） -->
![YuiMorph 演示](docs/images/demo.gif)

## 30 秒快速开始

1. 下载 [最新 Release](https://github.com/Cherry7593/YuiMorph/releases/latest) 中的 `YuiMorph-<版本>.jar`，放入服务端 `plugins/`
2. 同时安装 [PacketEvents](https://modrinth.com/plugin/packetevents) 2.14.0 或更高版本
3. 启动服务器，进游戏输入 `/ym` 打开伪装菜单，或 `/ym morph pig`
4. （可选）客户端安装 `yuimorph-fabric-<版本>.jar`，按 `N` 打开带实时模型的选择界面

默认情况下，玩家需要先击杀某种生物（或玩家）才能解锁对应伪装；OP 拥有 `yuimorph.morph.any`，可以直接使用任意伪装。

## 兼容性

| 组件 | 版本 | 状态 |
|---|---|---|
| Folia | 26.3 | 已测试 |
| Paper | 26.3 | 理论可用（Paper 插件格式，API 26.3），**未完整测试** |
| PacketEvents | 2.14.0+ | 必需 |
| Java | 25 | 必需 |
| Fabric 客户端 Mod（可选） | Minecraft 26.3，Fabric Loader 0.19.5+ | 需要 Fabric API、Fabric Language Kotlin |

不装客户端 Mod 的玩家也能正常伪装和看到别人的伪装。

## 功能

**伪装**
- 伪装成原版生物，或使用 Mojang 皮肤伪装成玩家（`player:<名字>`）
- 服务端发包渲染，其他玩家无需安装任何东西

**属性**（`/ym property`，按生物类型提供）
- 幼体、颜色、剪毛、大小（史莱姆 / 岩浆怪 / 幻翼）、变种（猫、狼、青蛙、猪、牛、鸡、美西螈、鹦鹉、兔子、狐狸、马等）、闪电苦力怕、雪傀儡南瓜头、熊猫基因、村民类型与职业
- 自定义名字、玩家伪装换皮肤

**动作**（`/ym action`，按生物类型提供）
- 坐下、躺下、睡觉、跳舞、怒吼、爬行、挖掘、进食、嗅探、蛙鸣等

**获取方式**
- 击杀解锁（带 `yuimorph_nogrant` 标签的实体不给予解锁）
- 玩家之间交换伪装（`/ym request`）

**游戏体验**
- 碰撞箱随伪装体型缩放（使用原版 scale 属性），变大前检查空间
- 血量缩放：`none` / `capped` / `unlimited`
- 怪物仇恨（实验性）：伪装成生物后，怪物只主动攻击原版里本来会攻击的生物
- 环境音效、受击和死亡音效
- 下线记住伪装，进服自动恢复
- 强制伪装：所有玩家进服时变成指定伪装
- 可禁用指定伪装或整个分类、可在指定世界禁止伪装

**界面**
- 无 Mod：箱子 GUI（`/ym`）
- 装了 Mod：实时模型选择界面（属性所见即所得、预设、最近使用）、动作轮盘、状态 HUD、F5 可看到自己的伪装、第一人称显示伪装的手臂 / 前腿

## 命令

主命令 `/yuimorph`，别名 `/ym`。补全中只显示有权限的子命令，`/ym help` 只列出你能用的命令。

| 命令 | 说明 | 权限 |
|---|---|---|
| `/ym` | 打开伪装菜单 | `yuimorph.morph` |
| `/ym morph <伪装>` | 伪装，例如 `pig`、`minecraft:fox`、`player:Notch` | `yuimorph.morph` |
| `/ym unmorph` | 解除伪装 | `yuimorph.unmorph` |
| `/ym property [属性] [值]` | 查看 / 设置 / 重置属性 | `yuimorph.property` |
| `/ym action [动作]` | 查看 / 播放动作（`stop` 停止持续动作） | `yuimorph.action` |
| `/ym request send <玩家>` | 发送伪装交换请求 | `yuimorph.request.send` |
| `/ym request accept <玩家>` | 接受交换请求 | `yuimorph.request.accept` |
| `/ym request deny <玩家>` | 拒绝交换请求 | `yuimorph.request.deny` |
| `/ym help` | 帮助 | 无 |
| `/ym admin status` | 插件状态 | `yuimorph.admin` |
| `/ym admin reload` | 重载配置 | `yuimorph.admin` |
| `/ym admin grant <玩家> <伪装>` | 给予解锁 | `yuimorph.admin` |
| `/ym admin revoke <玩家> <伪装>` | 收回解锁 | `yuimorph.admin` |
| `/ym admin force <玩家> <伪装>` | 强制伪装 | `yuimorph.admin` |
| `/ym admin unmorph <玩家>` | 强制解除 | `yuimorph.admin` |
| `/ym admin query <玩家>` | 查看当前伪装 | `yuimorph.admin` |
| `/ym admin queryall` | 列出所有正在伪装的玩家 | `yuimorph.admin` |
| `/ym admin lookup <玩家>` | 查看已解锁的伪装 | `yuimorph.admin` |
| `/ym admin ban <伪装>` / `unban <伪装>` | 禁用 / 解禁伪装 | `yuimorph.admin` |
| `/ym admin bans` | 列出禁用的伪装 | `yuimorph.admin` |
| `/ym admin import feathermorph [confirm]` | 从 FeatherMorph 导入数据 | `yuimorph.admin` |

客户端 Mod 默认按键（可在"按键设置"里修改）：`N` 打开选择界面，按住 `G` 动作轮盘，`↓` 解除伪装。

## 权限

| 权限 | 说明 | 默认 |
|---|---|---|
| `yuimorph.morph` | 使用 `/ym`、`/ym morph` | 所有人 |
| `yuimorph.unmorph` | 解除伪装 | 所有人 |
| `yuimorph.acquire` | 通过击杀解锁伪装 | 所有人 |
| `yuimorph.morph.any` | 无需解锁即可使用任意伪装 | OP |
| `yuimorph.morph.as.<命名空间>.<名称>` | 单个伪装；未设置时视为允许，设为 false 可单独禁止 | 未设置 |
| `yuimorph.property` | 使用 `/ym property` | 所有人 |
| `yuimorph.property.name` | 设置自定义名字 | 所有人 |
| `yuimorph.property.skin` | 玩家伪装换皮肤 | OP |
| `yuimorph.action` | 播放动作 | 所有人 |
| `yuimorph.request.send` / `.accept` / `.deny` | 伪装交换 | 所有人 |
| `yuimorph.forced.bypass` | 不受强制伪装影响 | OP |
| `yuimorph.admin` | 管理命令 | OP |

## 配置

`plugins/YuiMorph/config.yml` 主要选项：

| 选项 | 默认 | 说明 |
|---|---|---|
| `unlock-on-kill` | `true` | 击杀解锁 |
| `banned-disguises` | 末影龙、凋灵 | 禁用列表，支持 `minecraft:@all` / `player:@all` |
| `disabled-worlds` | `[]` | 禁止伪装的世界 |
| `bounding-box.enabled` | `true` | 碰撞箱缩放（范围 `min-scale` 0.25 ～ `max-scale` 2.0） |
| `health-scale.mode` | `none` | 血量缩放：`none` / `capped` / `unlimited` |
| `mob-aggro.enabled` | `true` | 怪物仇恨（实验性） |
| `restore-on-join` | `true` | 进服恢复伪装 |
| `unmorph-on-death` | `false` | 死亡时解除伪装 |
| `forced-disguise` | `""` | 强制伪装，留空关闭 |
| `client-mod.enabled` | `true` | 允许客户端 Mod 增强 |

客户端设置在 `config/yuimorph/settings.json`（可关闭第一人称伪装手臂等）。

## 从 FeatherMorph 迁移

保留 `plugins/FeatherMorph/` 数据目录（FeatherMorph 插件 jar 可以删掉），启动 YuiMorph 后在控制台执行：

```text
ym admin import feathermorph           # 预览，只统计不写入
ym admin import feathermorph confirm   # 执行导入
```

- 导入已解锁的伪装和下线时的伪装；常见属性（变种、马的颜色花纹、史莱姆大小、闪电苦力怕）会转换，玩家皮肤等不迁移
- 合并而不是覆盖，可以重复执行；在线玩家也能导入
- 只读取 FeatherMorph 数据，导入前自动备份 `plugins/YuiMorph/playerdata`

## 常见问题

**必须装客户端 Mod 吗？**
不用。Mod 只提供自身伪装渲染、选择界面、快捷键和 HUD。

**能在 Paper 上用吗？**
插件按 Paper 插件格式编写，理论上可以，但目前只在 Folia 26.3 上测试过。遇到问题欢迎提 Issue。

**怎么关掉怪物仇恨？**
`config.yml` 中设置 `mob-aggro.enabled: false`，然后 `/ym admin reload`。

**怎么禁止某个伪装？**
`/ym admin ban minecraft:warden`，或把权限 `yuimorph.morph.as.minecraft.warden` 设为 false。

**消息是什么语言？**
服务端消息目前只有简体中文（`lang/zh_cn.yml`），客户端 Mod 有中文和英文。

## 构建

需要 JDK 25。

```bash
./gradlew build          # 所有模块 + 单元测试
./gradlew :plugin:deploy # 构建插件并拷贝到本地测试服（设置 yuimorph.testServerDir）
```

升级 Minecraft 版本后，需要用新服务端 jar 重新生成实体数据表并跑测试：

```bash
python3 scripts/gen-entity-layouts.py <服务端 jar>
./gradlew :plugin:test
```

## 许可

[MIT](LICENSE) © Yui

功能设计参考了 [FeatherMorph](https://github.com/NiFeather/FeatherMorph)。
