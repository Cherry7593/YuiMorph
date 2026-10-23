# 怪物仇恨设计

> 参考：FeatherMorph `events/CommonEventProcessor#onEntityTarget`、`utilities/EntityTypeUtils`、`RevealingHandler`
> 原版索敌关系用 Folia 26.3 服务端 jar 的字节码核对过（各怪物的 `NearestAttackableTargetGoal` 目标类）。

## 目标

伪装成生物后，怪物**只会主动攻击原版里它本来就会攻击的生物**：

- 伪装成僵尸 → 僵尸、骷髅、苦力怕都不会主动追你
- 伪装成村民 → 僵尸和灾厄村民仍会追你
- 伪装成铁傀儡 → 僵尸、骷髅、蜘蛛会追你
- 伪装成玩家 → 和原版完全一致
- 你先动手打了某只怪 → 它照常反击

只做“取消不该发生的主动索敌”，**不修改生物 AI**，也不新增原版没有的仇恨（例如不会让铁傀儡去打伪装成僵尸的玩家）。

## FeatherMorph 的做法

只监听 `EntityTargetEvent`，满足下面任一条件就放行，否则取消：

1. 目标不是玩家，或者来源是监守者
2. 来源是猪灵蛮兵，且 `piglin_brute_ignore_disguises: true`（默认）
3. 来源是猪灵，且玩家最近 8 tick 内挖过金块
4. 玩家是这只怪最后一次受到伤害的来源（`getLastDamageCause`）
5. 索敌原因是反击或协助类：`TARGET_ATTACKED_ENTITY`、`TARGET_ATTACKED_NEARBY_ENTITY`、`REINFORCEMENT_TARGET`、`FOLLOW_LEADER`、`DEFEND_VILLAGE`、`TARGET_ATTACKED_OWNER`、`OWNER_ATTACKED_TARGET`、`CUSTOM`、`UNKNOWN`
6. 玩家没在伪装
7. 按「来源怪物 → 伪装类型」查表命中
8. 开启揭示值（默认关闭）时，按揭示值随机放行

另外还有两块：**揭示值**（交互、挖方块、受伤会让怪物逐渐“识破”你）和 **AvoidPlayerGoals**（用 NMS 替换猫、兔子等生物的逃跑目标，让它们按伪装来判断要不要躲开玩家）。

### 照搬时需要修正的问题

| 问题 | 说明 |
|---|---|
| 凋灵判断反了 | 原版凋灵攻击 `WITHER_FRIENDS`（亡灵）**以外**的生物；FeatherMorph 写成只攻击亡灵 |
| 雪傀儡 | 原版僵尸、骷髅、灾厄村民不会主动打雪傀儡（只打铁傀儡），FeatherMorph 把两者混在一起 |
| 缺少的目标 | 海龟（僵尸、骷髅类）、美西螈（溺尸）、史莱姆/岩浆怪 → 铁傀儡、蜘蛛 → 铁傀儡 |
| 反击判定永久有效 | `getLastDamageCause` 不会过期，打过一次的怪永远会追你；改成有时间窗口 |

## 规则表

「会主动攻击」= 伪装成这些生物时放行；不在表里的伪装一律取消主动索敌。伪装成玩家始终放行。

| 来源怪物 | 会主动攻击的伪装 |
|---|---|
| 僵尸、尸壳、僵尸村民 | 村民、流浪商人、铁傀儡、海龟 |
| 溺尸 | 村民、流浪商人、铁傀儡、海龟、美西螈 |
| 骷髅、流浪者、沼骸 | 铁傀儡、海龟 |
| 凋灵骷髅 | 铁傀儡、海龟、猪灵、猪灵蛮兵 |
| 蜘蛛、洞穴蜘蛛 | 铁傀儡 |
| 史莱姆、岩浆怪 | 铁傀儡 |
| 掠夺者、卫道士、唤魔者、幻术师、劫掠兽 | 村民、流浪商人、铁傀儡 |
| 守卫者、远古守卫者 | 鱿鱼、发光鱿鱼、美西螈 |
| 凋灵 | 除 `WITHER_FRIENDS`（亡灵）外的所有生物 |
| 猪灵 | 凋灵骷髅、凋灵（金子引起的激怒不处理） |
| 猪灵蛮兵 | 凋灵骷髅、凋灵；默认不接管（配置项） |
| 僵尸疣猪兽 | 除苦力怕、恶魂、僵尸疣猪兽外的所有生物 |
| 末影人 | 末影螨 |
| 苦力怕、烈焰人、恶魂、幻翼、潜影贝、蠹虫、末影螨、女巫、恼鬼、旋风人、疣猪兽、嘎吱怪 | 无 |
| **表外的所有生物** | 不处理，始终按原版 |

- **只接管表内的敌对怪物**。中立生物（狼、蜜蜂、僵尸猪灵、铁傀儡等）被激怒后的持续追击同样是 `CLOSEST_PLAYER`，如果默认取消会把它们打断；监守者、硫磺立方等未知行为的新生物也留给原版。
- 凋灵用 Bukkit 的 `Tag.ENTITY_TYPES_WITHER_FRIENDS` 判断，跟随数据包变化；其余关系写在 `AggroRules`。
- 僵尸疣猪兽的过滤在 Sensor 里，没有逐项核对，沿用 FeatherMorph 的排除名单。
- 恼鬼复制主人目标用的是 `OWNER_ATTACKED_TARGET`，会被放行；它自己找目标时只找玩家。
- Brain 型生物（猪灵、疣猪兽、僵尸疣猪兽）在 Paper 的 `StartAttacking` 里同样会触发 `EntityTargetEvent`（`CLOSEST_PLAYER` / `CLOSEST_ENTITY`），不需要额外处理。

## 判定流程

```
EntityTargetEvent（只看 target 是 Player 的情况）
├─ 功能关闭 / 来源不在规则表 / 猪灵蛮兵且配置忽略 → 放行
├─ 原因属于反击或协助类（同 FeatherMorph 的列表）→ 放行
├─ 玩家没在伪装，或伪装成玩家 → 放行
├─ 玩家在 provoke-seconds 内打过这只怪 → 放行
├─ 规则表命中 → 放行
└─ 取消
```

- **挑衅记录**：在 `EntityDamageByEntityEvent` 里记录「怪物 UUID → 玩家 UUID + 过期时间」，弹射物算发射者。用 `ConcurrentHashMap` 存，读取时清理过期项，怪物死亡时移除。
- **变身前已有的仇恨保留**：事件只在“换目标”时触发，正在追你的怪变身后会继续追，避免“被追就变身脱战”。不额外处理。
- **解除伪装**：不需要处理，怪物下次索敌会按原版逻辑找你。
- **线程**：Folia 下事件在怪物所在区域线程触发；会话表和挑衅表都是并发容器，只读不改世界，安全。

## 配置

```yaml
# 怪物仇恨：伪装成生物后，怪物只会主动攻击原版里它本来就会攻击的生物
# 例如伪装成僵尸后不会被僵尸、骷髅追；伪装成村民仍会被僵尸追。伪装成玩家时和原版一致
mob-aggro:
  enabled: true
  # 主动攻击某只怪物后，它在多少秒内仍会主动追击你
  provoke-seconds: 30
  # 猪灵蛮兵无视伪装（原版里它几乎攻击所有人）
  piglin-brute-ignore-disguise: true
```

## 不做（与 REQUIREMENTS「明确不做」一致）

- **揭示值 / 被识破**：属于“揭穿机制”；FeatherMorph 默认也是关闭的
- **猪灵因金子激怒**：伪装期间开箱子、挖金块不会引来猪灵主动攻击（直接打猪灵仍会被反击）
- **被动生物躲避（AvoidPlayerGoals）**：需要用 NMS 替换生物 Goal，属于“生物 AI 修改”
- **新增仇恨**：例如铁傀儡攻击伪装成僵尸的玩家、猫让伪装成苦力怕的玩家逃跑，同样需要修改 AI

## 实现拆分

| 文件 | 内容 |
|---|---|
| `effect/AggroRules.kt` | 纯函数：`handles(source)`、`shouldTarget(source, disguise, witherFriend)`，键是完整实体 ID |
| `listener/AggroListener.kt` | `EntityTargetEvent` + `EntityDamageByEntityEvent` + `EntityDeathEvent`；挑衅表，每 5 秒异步清理过期项 |
| `config/MorphConfig.kt` | `MobAggroConfig(enabled, provokeSeconds, bruteIgnoresDisguise)`；`config-version` 5 |
| `effect/AggroRulesTest.kt` | 规则表逐类覆盖；凋灵 / 雪傀儡这两处和 FeatherMorph 不同的地方单独断言 |

## 待实机验证

- 伪装僵尸穿过僵尸群、骷髅群不被追
- 伪装村民被僵尸追；伪装铁傀儡被骷髅射
- 打一只骷髅后被追，脚离视线再回来仍被追；`provoke-seconds` 到期后不再主动追
- 变身前已被追的怪变身后继续追
- 伪装凋灵骷髅靠近猪灵会被攻击；猪灵蛮兵默认照常攻击伪装者
- 打一只僵尸猪灵后，整群僵尸猪灵照常围攻（确认中立生物没被影响）
