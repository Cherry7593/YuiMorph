# YuiMorph 开发计划

## 工程结构

```
YuiMorph/
├── protocol/   纯 Kotlin，客户端↔服务端协议定义与编解码（不依赖 MC）
├── plugin/     Folia/Shiroha 26.3 插件（Paper plugin，PacketEvents 渲染）
└── mod/        Fabric 26.3 客户端 Mod（可选增强）
```

- 包名：`moe.yui.morph`；Mod ID：`yuimorph`；插件消息频道：`yuimorph:main`
- 插件把 `protocol` 和 Kotlin 标准库打包进 jar（shadow，并重定位 Kotlin）；Mod 使用 fabric-language-kotlin，`protocol` 用 jar-in-jar 打包进去
- `./gradlew :plugin:deploy` 构建后把插件 jar 拷贝到测试服 `plugins/`

## 关键版本

| 依赖 | 版本 |
|---|---|
| Gradle | 9.8.1 |
| Kotlin | 2.4.21 |
| folia-api | 26.3.build.3-alpha |
| PacketEvents | 2.14.0（服务器作为前置插件安装） |
| Fabric Loader / Loom / API | 0.19.5 / 1.19.1 / 0.163.0+26.3 |
| fabric-language-kotlin | 1.14.2+kotlin.2.4.21 |

## 渲染方案（服务端）

和 LibsDisguises / FeatherMorph 一样，改写**发给其他玩家**的数据包，伪装实体沿用玩家的 entity id，因此移动、转头这类包不用处理就能生效：

- `SPAWN_ENTITY`（玩家）→ 取消，改发伪装实体的生成包、完整元数据和装备
- `ENTITY_METADATA` → 只保留 Entity / LivingEntity 公共字段，去掉玩家专属字段，再叠加伪装自己的属性
- 伪装成玩家时 → 先发假的 `PLAYER_INFO_UPDATE`（伪装名和皮肤，使用虚拟 UUID），再生成玩家实体
- 变身或取消时 → 对当前观察者先发 `DESTROY_ENTITIES`，再按新状态重新生成
- 只用 PacketEvents 和 Folia 调度 API，所有状态都放在线程安全的容器里，不在 Netty 线程里读写世界

## 实体数据表

`entity-layouts.tsv`（元数据字段）和 `entity-traits.tsv`（尺寸、默认最大生命值）都在 `plugin/src/main/resources/`，由 `scripts/gen-entity-layouts.py` 从服务端 jar 的字节码生成。属性按字段名（例如 `Sheep.DATA_WOOL_ID`）查找索引，不写死数字。

**升级 MC 版本时必须重新生成**：

```bash
python3 scripts/gen-entity-layouts.py <测试服>/versions/<版本>/shiroha-<版本>.jar
./gradlew :plugin:test   # 包含“任何属性都不会发出越界索引”的全量检查
```

## 已知限制（M1）

- 伪装者自己看不到自己的伪装（要等 M6 的客户端 Mod）
- 脚步声沿用玩家的（由方块决定，和生物一致）；受击音效靠坐标匹配改写，极少数同一刻同一位置多人受伤时可能漏改
- 插件停用时只能尽力恢复玩家本体（Folia 停用阶段不能调度区域任务），失败时重新进入视野就会恢复

## 里程碑

| # | 内容 | 对应需求 |
|---|---|---|
| M0 ✅ | 多模块骨架，三个模块都能编译；插件能在测试服上加载 | — |
| M1 ✅ | 伪装成生物（发包渲染）、`/morph` `/unmorph`、权限、JSON 存储、击杀解锁 | A1 B1 C3/C4 |
| M2 ✅ | 伪装成玩家（皮肤）、伪装属性（变种 / 颜色 / 大小 / 名字） | A1 A3 |
| M3 ✅ | 箱子 GUI、交换请求、管理命令、封禁列表、语言文件 | B2 B5 C2 |
| M4 ✅ | 碰撞箱、血量缩放、环境音效、离线恢复、强制伪装 | A5 A6 A7 |
| M5 | 伪装动作 | A4 |
| M6 | 协议与 Fabric Mod：自身伪装渲染、选择界面、快捷键、HUD | G2 |
