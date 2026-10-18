# YuiMorph

《我的世界》伪装插件：伪装成生物或玩家。功能参考 [FeatherMorph](https://github.com/NiFeather/FeatherMorph)。

- 服务端：Folia / Shiroha 26.3 插件（前置：[PacketEvents](https://modrinth.com/plugin/packetevents) 2.14.0+）
- 客户端：Fabric 26.3 Mod（**可选**，不装也能完整游玩）

需求范围见 [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md)，设计与里程碑见 [`docs/PLAN.md`](docs/PLAN.md)。

## 功能

| 类别 | 内容 |
|---|---|
| 伪装 | 原版生物、玩家（Mojang 皮肤）；服务端发包渲染 |
| 属性 | `/ym property`：幼体、颜色、剪毛、大小、变种、村民类型/职业、自定义名字、换皮肤 |
| 动作 | `/ym action`：坐下、睡觉、跳舞、怒吼、缩成球、爬行等 |
| 获取 | 击杀解锁、玩家间交换请求（`/ym request`） |
| 体验 | 碰撞箱随体型缩放、血量缩放、环境 / 受击 / 死亡音效、下线恢复、强制伪装 |
| 界面 | 无 Mod：箱子 GUI（`/ym`）；装了 Mod：原生选择界面（N）、动作（G）、解除（↓）、HUD、F5 看到自己 |
| 管理 | `/ym admin status / query / queryall / lookup / grant / revoke / force / unmorph / ban / unban / bans / import / reload` |

## 从 FeatherMorph 迁移

保留 `plugins/FeatherMorph/` 数据目录（插件 jar 可以移除），启动 YuiMorph 后在控制台执行：

```text
ym admin import feathermorph           # 预览：统计会导入多少，不写入
ym admin import feathermorph confirm   # 执行导入
```

- 导入已解锁伪装（ID 格式相同）和下线时的伪装（下次进服恢复）；常见属性（变种、马的颜色花纹、史莱姆大小、闪电苦力怕）会转换，`player/skin` 等不迁移
- **合并而不是覆盖**：重复执行是安全的，玩家在 YuiMorph 里已经解锁的不会丢；在线玩家也可以直接导入
- 只读取 FeatherMorph 数据；导入前自动备份 `plugins/YuiMorph/playerdata` 为 `playerdata-backup-<时间>`

## 指令

所有功能都在 `/ym`（全名 `/yuimorph`）下，没有权限的子指令不会出现在补全里。`/ym help` 只列出你能用的指令。

| 指令 | 说明 | 权限（默认） |
|---|---|---|
| `/ym` | 打开伪装选择界面 | `yuimorph.morph`（所有人） |
| `/ym morph <伪装>` | 伪装，例如 `pig`、`player:Notch` | `yuimorph.morph`（所有人） |
| `/ym unmorph` | 解除伪装 | `yuimorph.unmorph`（所有人） |
| `/ym property [属性] [值]` | 查看 / 设置 / 重置属性 | `yuimorph.property`（所有人） |
| `/ym action [动作]` | 查看 / 播放动作 | `yuimorph.action`（所有人） |
| `/ym request send\|accept\|deny <玩家>` | 伪装交换 | `yuimorph.request.*`（所有人） |
| `/ym help` | 帮助 | 无 |
| `/ym admin ...` | 管理指令 | `yuimorph.admin`（OP） |

## 构建

需要 JDK 25。

```bash
./gradlew build                 # 全部模块 + 单元测试
./gradlew :plugin:deploy        # 构建插件并拷贝到本地测试服（gradle.properties 中的 yuimorph.testServerDir）
```

产物：

- 插件：`plugin/build/libs/YuiMorph-<版本>.jar`
- Mod：`mod/build/libs/yuimorph-fabric-<版本>.jar`（客户端还需要 Fabric API 与 Fabric Language Kotlin）

## 升级 Minecraft 版本

实体元数据布局和尺寸 / 血量表是从服务端字节码生成的，**升级后必须重新生成**并跑测试：

```bash
python3 scripts/gen-entity-layouts.py <测试服>/versions/<版本>/shiroha-<版本>.jar
./gradlew :plugin:test
```
