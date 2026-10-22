# 第一人称伪装手臂

> 状态：已实现（P1 + P2）。针对 Fabric 26.3。
> 已定：生物伪装用捕获式提交；没有可用手臂时默认不画。
> 实现时相对设计稿的调整都已写回对应章节，第 9 节有汇总。
> 目标：装了 Mod 的玩家伪装后，第一人称看到的手换成伪装的手 / 前腿；不改服务端和协议。

## 1. 现状

- 第一人称时代理实体被 `EntityRenderDispatcherMixin` 隐藏，屏幕上仍是**玩家自己的手**
- 代理实体是服务端只发给本人的真实客户端实体（玩家伪装 = `RemotePlayer`，生物伪装 = 对应生物），带完整元数据：变种、幼体、颜色、皮肤都已经在上面了，**新功能直接以代理实体为数据源**，不需要协议改动

## 2. 原版 26.3 的渲染路径

`ItemInHandRenderer` 在 26.3 改名为 `FirstPersonHandsAndItemsRenderer`，并拆成了「提取 → 提交」两个阶段：

```
LevelExtractor.extractPlayerState()          // 提取：本地玩家 → PlayerRenderState.avatarRenderState
GameRenderer.renderItemInHand()
 └ FirstPersonHandsAndItemsRenderer.submitHandsWithItems()
    └ submitArmWithItem()
       ├ 空主手            → renderPlayerArm() → renderPlayerHand()
       ├ 地图（单手/双手）  → renderMapHand()   → renderPlayerHand()
       └ 其他物品           → 只画物品，不画手
renderPlayerHand()
 └ AvatarRenderer.renderRightHand / renderLeftHand(pose, collector, light, skinTexture, sleeve)
    └ arm.resetPose(); arm.zRot = ±0.1; submitModelPart(arm, entityTranslucent(skin))
```

要点：

- **只有空主手和拿地图时才画手**，所以只需要替换 `renderPlayerHand` 里那两次 `renderRight/LeftHand` 调用，摆动、换手、视角晃动全部沿用原版
- 手臂在玩家模型里的位置：右臂枢轴 `(-5, 2, 0)`，方块相对枢轴 `x∈[-3,1] y∈[-2,10] z∈[-2,2]`（像素，细手臂 x 宽 3）
- ⚠️ **猪 / 牛 / 鸡的变种模型（例如寒带猪有专门的 `ColdPigModel`）、幼体模型都是在 `submit()` 里才选的**：`PigRenderer.submit` 里 `this.model = models.get(variant).getModel(isBaby)`。在 `submit` 之前调用 `renderer.getModel()`，拿到的是**上一只被渲染的同类生物**的模型，和贴图对不上

## 3. 开源实现对比

| | Woodwalkers 8.4（26.3） | FeatherMorphClient（26.1） | iChun Morph（1.16） |
|---|---|---|---|
| 钩子 | `AvatarRenderer.renderHand` HEAD 取消 | `@Redirect` `renderPlayerArm` / `renderMapHand` 里的 `renderRight/LeftHand` | `PlayerRenderer.renderItem` HEAD 取消 |
| 玩家伪装 | 不特殊处理 | 用伪装实体的 `AvatarRenderer` + 伪装皮肤 | 用伪装玩家的 renderer |
| 选手臂 | `HumanoidModel` 直接用左右臂；其他按**模型类**查表（~25 条），`GenericEntityArm` 按 part 路径 | 人形直接用；其他按**名字候选**（`right_arm` → `right_front_leg` → …）在 `bakeRoot()` 出来的新模型里找，按实体类型缓存 | 每个模型类一个 JSON（左右手 part + 位置修正），沿模型类继承链查找，资源包可加 |
| 位置调整 | 每条表项一个手写变换 + 全局 `translate(0,-.35,.5)` | 每个实体类型一个 offset + scale，默认 `(0,-0.6,0.45)` | JSON 里的 translate / rotate 序列 |
| 贴图 | `getTextureLocation(createRenderState())` | `getRenderType(state, …)`（会跟着隐身、发光） | `getEntityTexture` |
| 没有可用手臂 | 不画 | 不画 | 不画 |
| 其他 | `GenericEntityArm` **每帧 `bakeLayer`**，开销大 | 用伪装实体的挥手进度；悦灵 / 恼鬼强制全亮 | 变身过渡时两个模型插值 |

共同问题（也是本设计要解决的）：

1. **模型选错**：都是在 `submit` 之外拿模型，26.x 的变种 / 幼体模型会错（见上节）；FMC 自己 `bakeRoot()` 的新模型没有变种信息
2. **图层丢失**：只画主体模型的一个 part，羊毛、村民职业服、溺尸外层、马的花纹都不会出现
3. **全靠手调表**：每种生物都要手调位置，表外的（新版本加的生物）位置随缘

## 4. 方案

### 4.1 总体

```
提取阶段  LevelExtractor.extractPlayerState  TAIL
          └ 有代理实体 && 第一人称 → extractEntity(proxy, partialTick)
            → 存进 PlayerRenderState（duck 接口字段 yuimorph$proxyState）

提交阶段  FirstPersonHandsAndItemsRenderer.renderPlayerHand
          └ @WrapOperation 包住 renderRightHand / renderLeftHand
            ├ 没有 proxyState / 功能关闭       → 原样调用（原版手）
            ├ proxyState 是 AvatarRenderState → 玩家伪装：伪装的 AvatarRenderer + 伪装皮肤
            └ 其他 LivingEntityRenderState    → FirstPersonArmRenderer.submit(...)
                                                  返回 false（没有手臂）→ 按配置：不画 / 原版手
```

- 用 `@WrapOperation`（MixinExtras，Fabric Loader 自带）而不是 `@Redirect` / HEAD 取消：别的 Mod 也包同一处时可以叠加
- 只拦 `renderPlayerHand` 内部，空手和地图两条路径一次覆盖；`AvatarRenderer.renderHand` 本身不动，不影响别的 Mod 调它
- 提取在提取阶段做，提交阶段只读 render state，符合 26.x 的分阶段约定

### 4.2 玩家伪装

```kotlin
val renderer = dispatcher.getRenderer(proxyState)       // 按皮肤模型自动选粗 / 细手臂的 AvatarRenderer
renderer.renderRightHand(pose, collector, light, proxyState.skin.body().texturePath(), proxyState.showRightSleeve)
```

和原版逻辑完全一致，只是换成代理的皮肤、模型和袖子开关。

### 4.3 生物伪装：「捕获式提交」

核心思路：**让代理实体的 renderer 正常走一遍 `submit()`，但交给一个只记录、不渲染的 `SubmitNodeCollector`**。这样拿到的是 renderer 真正会用的东西：

- 实际选中的模型实例（变种、幼体都对）
- 对应的 `RenderType`（贴图、透明、发光都由原版决定）
- tint（羊毛颜色、皮革色等）
- 每个图层各自的 `submitModel` 调用（羊毛、职业服、外层……）

```kotlin
class CapturingCollector : SubmitNodeCollector {
    val models = ArrayList<Captured>()   // (model, state, renderType, tint)
    override fun <S> submitModel(model, state, pose, renderType, light, overlay, tint, uv, outline) { models += … }
    override fun order(order: Int) = this
    // 名字牌、阴影、火焰、物品、方块…… 全部空实现
}
```

装备（盔甲、鞍、马铠、鞘翅）都经过 `EquipmentLayerRenderer.renderLayers`，交给 `CapturingCollector` 时直接跳过（`EquipmentLayerRendererMixin`），和原版第一人称手不显示盔甲袖子一致。

然后：

1. **选手臂 part**（按模型实例缓存，资源重载时清空，见 4.4）
2. 对每条捕获记录：如果它的模型里有同一路径的 part（主体 + 结构相同的图层模型）就提交这个 part；图层模型没有这个 part 就跳过
3. 提交前把 part 重置到初始姿势（`resetPose`，和原版玩家手一样不跟随 `setupAnim` 的动作，比如僵尸平举）；图层模型的 part 再**复制主体 part 的姿势**（流浪者衣服图层的初始姿势和主体不一致）
4. 光照用代理 state 的 `lightCoords`：悦灵 / 恼鬼的全亮由它们自己的 renderer 决定，不用像 FMC 那样写死
5. overlay 用 `NO_OVERLAY`（原版第一人称手不闪红）

开销：只在「第一人称 + 伪装中 + 正在画手」时每帧执行一次，两只手共用一次捕获结果。

### 4.4 选哪个 part

按顺序找，命中即停：

1. **手调表**（按实体类型）：`EntityType → ArmProfile`，可以指定 part 名、是否不画手、额外变换
2. **名字启发**（参考 FMC）：用 `root().createPartLookup()` 按名字找
   `right_arm` → `right_front_leg` → `right_leg` → `right_hind_leg`，左手把 `right` 换成 `left`。
   人形模型（`HumanoidModel`）的手臂就叫 `right_arm` / `left_arm`，不需要特殊处理
3. 都没有 → 没有手臂（史莱姆、鱼、蝙蝠、幻翼……）

图层模型按**同名** part 查找，结构和主体一致的图层（羊毛、外层、衣服）自然能对上。

某一侧找不到、另一侧有的（例如只有一条触手），两只手用同一个 part。

### 4.5 摆位置：自动拟合 + 微调

各模型 part 的枢轴、大小、朝向差别很大，单靠手调表维护不了，也覆盖不到新版本的生物。做法：

1. 用 `ModelPart.getExtentsForGui(identity, …)` 算出 part（含自身枢轴和子 part）在初始姿势下的包围盒 S，按 (模型实例, part) 缓存
2. 计算一个**拟合变换**，把 S 映射到玩家右臂盒 T（相对肩部枢轴 `x∈[-3,1] y∈[-2,10] z∈[-2,2]`）：
   - 等比缩放 `s = 12 / S.高`，再把宽度 `max(S.宽, S.深) * s` 夹在 `[2.5, 6]` 像素之间，最后 `s` 最多 2 倍（猪腿 4×6 → 1.5 倍变成 6×9；悦灵手臂、幼体的腿保持小巧）
   - 锚点：S 和 T 的「**底面**中心」（末端 = 手）对齐。第一人称只看得到手臂下半截，按肩膀对齐时短部件（幼体猪 2 像素的腿）会完全在画面外
3. 最终变换：`肩部枢轴 × zRot(±0.1) × 手调表 offset × 拟合 × 手调表 rotation`，然后提交 part（part 自己的 `translateAndRotate` 由 `ModelPart.render` 照常执行）。包围盒是在手调表 rotation 之后算的
4. 手调表只处理拟合搞不定的（当前内容见 `ArmProfiles.kt`）：
   - 初始姿势是横着的（蜘蛛腿、海龟鳍、雪傀儡手臂）：`straighten` 去掉自带旋转，再用 rotation 转成向下
   - 朝前伸的（青蛙前肢）：rotation 绕 X 转 90°
   - 换部件：鸡 / 鹦鹉用翅膀，烈焰人 / 旋风人用棒，恶魂 / 快乐恶魂 / 鱿鱼用触手，末影龙用小腿
   - 不画：蜜蜂、美西螈（腿是零厚度面片）
   - 铁傀儡：再放大 1.5 倍

左手：枢轴 x 取反、`zRot` 取反、目标盒 x 镜像。

### 4.6 不变的部分

- **物品**：位置、挥动、吃喝、拉弓全部沿用原版。物品不跟手臂走（原版本来就没画拿物品的手），所以不会出现「物品悬空在猪蹄旁边」的错位
- 隐身：原版已经在 `isInvisible` 时不画手
- 第三人称 / F5：不受影响
- 挥手进度：沿用本地玩家的（代理实体不会收到挥手包，用它的反而不动）

### 4.7 设置

全局设置文件 `config/yuimorph/settings.json`（`ClientSettings`，不分服务器；`ClientStorage` 是按服务器分开的，不适合放这类偏好）。启动时读取并写回默认值，暂不做设置界面，改完重启生效：

| 项 | 默认 | 说明 |
|---|---|---|
| `firstPersonArm` | 开 | 关掉后永远显示原版手 |
| `noArmFallback` | `hide` | 伪装没有可用手臂时：`hide` 不画（参考三个 Mod 的做法）/ `vanilla` 显示原版手 |

## 5. 文件

| 文件 | 内容 |
|---|---|
| `client/java/.../mixin/LevelExtractorMixin.java` | `extractPlayerState` TAIL：提取代理实体 render state |
| `client/java/.../mixin/PlayerRenderStateMixin.java` | 实现 `ProxyStateHolder`：代理 render state 字段，`reset()` 时清空 |
| `client/java/.../mixin/FirstPersonHandsMixin.java` | `@WrapOperation` 两处 `renderRight/LeftHand` |
| `client/java/.../mixin/EquipmentLayerRendererMixin.java` | 捕获时跳过装备 |
| `client/java/.../arm/ProxyStateHolder.java` | duck 接口 |
| `client/java/.../arm/CapturingCollector.java` | 只记录 `submitModel` 的 collector（Java 写，签名和接口一一对应） |
| `client/kotlin/.../arm/FirstPersonArm.kt` | 提取 + 分派：玩家伪装 / 生物伪装 / 回退；出错的生物记入黑名单 |
| `client/kotlin/.../arm/MobArm.kt` | 捕获、选 part、提交（主体 + 图层） |
| `client/kotlin/.../arm/ArmFit.kt` | 包围盒和拟合变换，按 part 缓存（WeakHashMap，资源重载后自动回收） |
| `client/kotlin/.../arm/ArmProfiles.kt` | 手调表 |
| `client/kotlin/.../storage/ClientSettings.kt` | 全局设置 |
| `gametest/.../FirstPersonArmGameTest.kt` | 手臂画廊截图 |

## 6. 验证

- **手臂画廊 gametest**：在单人世界里用 `PreviewEntities` 逐个创建实体，通过 `FirstPersonArm.sourceOverride` 代替代理实体，第一人称、空手，每种生物截一张图；另外是地图、左撇子、寒带猪、幼体、红羊、玩家皮肤。截图在 `mod/build/run/clientGameTest/screenshots/`
  ```bash
  ./gradlew :mod:runClientGameTest                              # 全部（约 100 张）
  ./gradlew :mod:runClientGameTest -ParmGallery=spider,pig-cold  # 只截部分
  ```
- 手动：测试服上伪装 → 空手 / 拿剑 / 拿地图 / 挥手 / 吃东西 / 左撇子 / 隐身药水 / 切 F5 / 重载资源包（F3+T）
- 兼容：和 EntityCulling、Sodium、Iris 一起开一次

## 7. 风险

- `SubmitNodeCollector` 是接口，MC 升级加方法时 `CapturingCollector` 会**编译失败**——这是好事，改一下就行，不会静默出错
- renderer 的 `submit` 里有副作用（改 `this.model`、调用 `setupAnim`），但第一人称手在所有世界实体渲染完之后才画，下一帧世界渲染会重新设置，不影响别的实体
- 其他 Mod 的生物 renderer 如果在 `submit` 里做了奇怪的事（直接画、不走 collector），捕获不到 → 当作「没有手臂」处理，并用 try/catch 兜底，出错后对该实体类型停用并记日志一次（参考 FMC 的做法）
- 别的第一人称 Mod（First-person Model、Better Combat 等）会自己画手 / 身体，可能和我们叠在一起；先不做专门适配，遇到再说

## 8. 分期

| 期 | 内容 |
|---|---|
| P1 ✅ | 钩子 + 提取；玩家伪装；生物伪装（捕获 + 名字启发 + 自动拟合）；两项设置 |
| P2 ✅ | 图层（羊毛、职业服、外层等）；手调表；手臂画廊 gametest，按截图调参 |
| P3（可选） | 没有手臂的生物画一个代表部件（史莱姆块、鱼鳍）；变身时新旧手臂过渡动画（参考 iChun）；设置界面 |

## 9. 实现时相对设计稿的调整

| 设计稿 | 实际 | 原因 |
|---|---|---|
| 包围盒顶面（肩膀）对齐 | 底面（末端）对齐 | 短部件对齐肩膀时完全在画面外（幼体猪） |
| 只限制宽度 | 另加最多放大 2 倍 | 悦灵 / 恼鬼 / 犰狳的小部件被放得糊满屏幕 |
| 手调表只有 translate/rotate/scale | 加 `straighten` | 蜘蛛腿等自带旋转，先去掉再转比在斜姿势上叠加好调 |
| 图层 part 各自重置姿势 | 复制主体 part 的姿势 | 和原版一致 |
| 人形模型单独处理 | 并入名字启发 | `HumanoidModel` 的手臂就叫 `right_arm` |
| 设置放 `ClientStorage` | 新建全局 `ClientSettings` | `ClientStorage` 按服务器分文件 |
| 未提装备 | 捕获时跳过装备 | 僵尸穿盔甲时手臂会带上盔甲袖子，和原版不一致 |
