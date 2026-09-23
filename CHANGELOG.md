# Survival Toolbox 生存工具箱 — Changelog / 更新日志

## 1.2.1（仅 1.20.1 / 1.20.1 only）

### 修复 / Fixes

- **修复在部分 1.20.1 整合包上启动即崩溃的问题。** 1.20.1 版本此前使用了 `ResourceLocation.parse(...)` 与 `ResourceLocation.fromNamespaceAndPath(...)`；这两个方法属于 1.21 的 API，Forge 直到 **47.3.19** 才将其向后移植到 1.20.1（Forge 更新日志："47.3.19 Backport some Vanilla 1.21 ResourceLocation methods (#10241)"）。在 Forge 版本低于 47.3.19 的整合包中，模组会在构造阶段抛出 `NoSuchMethodError: ResourceLocation.parse(String)`，导致游戏在加载界面崩溃。现已全部改为 1.20.1 原版的 `new ResourceLocation(...)` 构造器，可在全部 Forge 47.x 版本上运行。
  Fixed a startup crash on 1.20.1 modpacks running a Forge version below 47.3.19. The 1.20.1 build used `ResourceLocation.parse(...)` / `ResourceLocation.fromNamespaceAndPath(...)`, which are 1.21 APIs that Forge only backported to 1.20.1 in 47.3.19. On older Forge releases the mod threw `NoSuchMethodError` while being constructed, crashing the game during loading. All call sites now use the vanilla `new ResourceLocation(...)` constructor, which exists in every Forge 47.x release.

> 1.21.1 版本不受此问题影响（这两个方法本身就是 1.21 原版 API），无需更新。
> The 1.21.1 build is unaffected by this issue and does not require an update.

- **声明的 Forge 依赖下限修正为 47.1.12。** 此前 `mods.toml` 声明 `forge=[47,)`（即 47.0 起均可），但镇魂灯使用的 `PlayerSpawnPhantomsEvent` 自 Forge **47.1.12** 才提供（Forge 更新日志："47.1.12 Add PlayerSpawnPhantomsEvent (#9644)"）。声明范围比实际要求宽，会让过旧的 Forge 照常加载模组并在加载阶段出错；现在下限与实际要求一致，过旧的 Forge 会直接给出「依赖版本不满足」的提示而不是崩溃。
  The declared Forge dependency floor is now 47.1.12, matching what the code actually requires (`PlayerSpawnPhantomsEvent` was added in Forge 47.1.12). Previously the range claimed support from 47.0, which let too-old loaders load the mod and fail during startup; now they report an unsatisfied dependency instead.

## 1.2.0

### 一、新增功能 / New Features

#### 1. 次元袋功能页（熔炼 / 铁砧 / 锻造台 / 工作台 / 拆解 / 切石机 / 磁铁 / 补货）
界面右侧按钮条展开的功能面板，全部在主界面内完成，不需要离开次元袋。All-in-one panels opened from the right-hand button strip, without leaving the bag screen.

- **熔炼页**：输入格 + 燃料格 + 产物格，规则沿用原版熔炉；可切换产物去处（放格子 / 直接进储物空间）；关闭界面后继续烧。Furnace panel with vanilla rules and an output destination toggle; keeps smelting while closed.
- **铁砧页 / 锻造台页**：输入、材料与产物按原版排布，费用与产物全部由原版逻辑计算；铁砧页支持改名框（放上物品自动填入物品名）。Anvil / smithing panels driven by the vanilla implementations, including the rename box.
- **工作台页 / 拆解页**：与拆解台共用同一套配方匹配与收费逻辑；拆解页支持「全部拆解」与变体翻页。Crafting / disassembly panels sharing the disassembly table's matching and cost rules.
- **切石机页**：输入格 + 产物格 + 可滚动的可用配方列表，每次消耗一个输入。Stonecutter panel with a scrollable recipe list.
- **磁铁页**：自动吸收附近的掉落物（开关、范围、名单、只吸已存有的模式）。Magnet panel that automatically collects nearby drops.
- **补货页**：把玩家身上已有的堆叠从次元袋自动补齐（可限定只补快捷栏）。Restock panel that tops up the stacks you already carry.
- **合成页「自动补充」开关**：取走产物后自动把消耗掉的材料从储物空间补回九宫格，只补袋子里真有的。Crafting panel's auto-refill toggle.

#### 2. 次元袋存储与操作 / Storage & Interaction
- **流体存储**：流体与物品在同一页的同一套网格里混排，数量以 mB 计（无上限），左键拿起/换位、右键用桶或任意流体容器存取；数字格式与机械动力统一（1000 mB = 1 B）。Fluids share the item grid, counted in mB with vanilla-bucket interaction; B/mB formatting matches Create.
- **共享存储模式**：末影箱式——数据保存在服务器存档并按玩家存储，袋子只是入口；原有本地存储可一键并入共享空间（合并式，不覆盖）。Shared (ender-chest style) storage per player, with a one-way merge from local storage.
- **托盘（出货工作台）**：把物品/流体交给容器，或从容器取回；支持朝世界放置一格流体。Tray for sending to / taking from containers, including placing one fluid cell into the world.
- **快捷收纳扩展**：右键收纳后回读确认（写入未生效时不会清空物品栏）；创造模式背包界面同样可用。Quick deposit verifies the write before clearing the source, and works in the creative inventory screen.
- **Shift 语义**：Shift+左键一律优先放进当前展开的功能页（含托盘），放不进才回背包；Shift+左键点产物格按材料批量取出。Shift-click prioritises the open panel and bulk-takes crafted products.
- **长按提起整格**：界面内长按一格高亮后，点击其他位置完成整体交换（大堆叠换位）。Hold a slot to pick it up, then click another slot to swap whole stacks.
- **整理四档**：按注册名 / 同模组归类 / 按数量 / 按标签排序，范围可选当前页或全部页。Four sort modes with per-page or all-pages scope.
- **数量文字**：≤9999 精确显示，更大数值缩写为 千/万/亿/万亿/亿亿。Count text abbreviates large values.
- **创造口袋**：新物品，右键打开创造模式物品栏，生存下也可取用（作弊级）。Creative Pocket item.

#### 3. 交易机（Trade Machine）
新物品：把村民的交易报价记录下来，随时查看并随身成交。A new item that records villager offers for later use.

- Shift+右键村民/流浪商人记录报价（同一交易项只保留更实惠的那条），右键打开界面。Shift + right-click a villager to record offers; right-click to open.
- 界面沿用原版村民交易界面，交易次数无上限，报价可按产物名称搜索。Vanilla trade UI with unlimited uses and a product search bar.

#### 4. JEI 配方转移集成 / JEI Recipe Transfer
配方界面右上角的「+」号可以直接把材料摆进次元袋。The "+" button can now fill the bag's panels.

- **按当前打开的功能页匹配**：打开工作台页就填工作台九宫格，打开拆解页就填拆解九宫格；两个都没开时给一句提示、不摆放。Fills the panel you have open; asks you to open one otherwise.
- **造型配方按位置摆放**：按配方外框对位（不会把 2×3 的门摆成 3×2 的活板门）。Shaped recipes keep their exact layout.
- **材料不足时禁用「+」**：服务端用真实库存只读回答可转移性，未知一律不放行。The "+" is disabled when materials are truly insufficient.
- **材料必须真的从袋子/背包取**：先扣除（并回读确认）再摆放，摆不进去则回滚，不会凭空生成物品。Materials are taken and verified before placement, with rollback.

### 二、已有功能更新与修复 / Updates & Fixes to Existing Features

#### 数据安全 / Data Safety
- **写次元袋 NBT 不再就地修改标签**：此前与服务端每 tick 的网络编码线程争用同一份标签，会导致编码异常并使玩家掉线。Bag NBT writes now always copy before writing, fixing a disconnect caused by concurrent encoding.
- **界面开着时不绕过容器缓存写存储**：修正托盘「纳入」、磁铁等路径绕过界面缓存、导致写入被旧缓存覆盖的问题。Storage writes always go through the open screen's cache.
- **入库一律回读确认**：写入未真正生效时，物品留在原处（不消耗、不丢弃）。Every deposit is verified by reading the storage back.
- **本地模式写入修复（1.20.1）**：页数据曾以错误的标签类型写入袋子，导致本地模式下存入的物品在重开界面后消失；已修复，并兼容读取历史数据以恢复既有内容。Fixes lost local-mode data on 1.20.1, with a compatible reader that recovers previously affected bags.

#### 次元袋 / Pocket Dimension
- **铁砧改名**：修正「放上物品不显示名称」与「输入名字后产物拿不出来」——后者由界面在点击产物时清空改名框、进而向服务端发送空名字引起。Anvil rename box now mirrors the input item, and taking the result no longer clears the name first.
- **右键收纳**：改为回读确认后再清空来源格子，避免写入未生效时物品凭空消失。Quick deposit no longer clears the source before verifying the write.
- **界面控件生命周期**：修正面板按钮在界面重新初始化（例如从 JEI 配方界面返回）后消失的问题。Panel buttons are rebuilt whenever the screen is re-initialised.
- **合成材料「全有或全无」**：修正材料校验与消耗口径不一致导致的干消耗与无限产出。Crafting now consumes exactly what it validated.
- **死亡处理**：开启「死亡不掉落」（keepInventory）时不再干扰物品栏。Respects keepInventory.
- **搜索/重命名输入框**：聚焦时按键不再冒泡（按 E 不会关闭界面）。Typing in the search box no longer closes the screen.

#### 拆解台 / Disassembly Table
- **配方污染修复**：产物一律复制后再交出，避免修改配方自身的产物栈（曾导致其它模组的配方内容被改坏）。Recipe outputs are always copied before being handed out.
- **缺中心/激活物品不再产出**：预览与消耗统一使用包含中心物品的完整材料清单。Preview and cost now use the same complete ingredient list.
- **中心物品口径**：拆解时照常返还，并可在九宫格中逐个取用。Centre/activation items are returned and individually collectable.
- **自定义配方放行**：锻造台、枪匠台（tacz）、无尽贪婪与诡厄巫法一类自定义配方可正常拆解。Custom recipe types are supported.
- **切石机槽位错位**：修正槽位注册顺序与 id 常量不一致导致的物品错位。Fixed misaligned stonecutter slots.
- **页码保持**：重算后停留在原来的配方页，不再跳回第一页。Keeps the current recipe page after recomputation.
- **稳定与容错**：配方解析与界面处理全程捕获异常，单个配方异常不再导致崩溃。Per-recipe failures are isolated instead of crashing.

#### 自适应附魔 / Adaptation
- **线性叠层**：以未减伤的原始伤害为基准固定获得「伤害 × 比例」层数，本次减伤使用叠层前的层数。Linear stacking based on pre-mitigation damage.
- **层数防护**：按配置的最大层数截断，NaN / 无穷 / 负数自动归零。Layer values are clamped and sanitised.
- **屏幕效果与模糊**：新增配置项屏蔽失明/火焰/水雾/冰冻/传送门等屏幕效果；对低水分引起的屏幕模糊改为随适应进度逐步减弱，而不是直接抵消。Screen-effect suppression config, and thirst blur fades gradually as adaptation progresses.
- **性能优化**：护盾值内存缓存、同步节流、护甲判定负缓存。Shield caching, sync throttling and negative caching for armour checks.

#### 其它 / Others
- **嗜血附魔**：可附于弓弩与模组枪械，伤害来源兜底解析弹射物归属。Applies to bows, crossbows and modded firearms.
- **透视眼镜**：白名单改为每副眼镜独立（存于物品 NBT）；界面不再有额外的背景模糊。Per-item whitelist, no extra background blur.
- **缴械法杖**：同时卸下饰品（Curios 兼容）。Also strips curios.
- **镇魂灯 / 铁砧球**：战利品表缺失不再抛出异常导致服务器崩溃。Missing loot tables no longer crash the server.
- **命令**：`/adapt` 与 `/bloodthirsty` 新增 `max` 子命令。New `max` subcommands.
- **专用服务器**：主类与公共类不再引用客户端类型，专用服务器可正常启动。Dedicated servers start correctly.
- **1.21.1 兼容**：修正数量文字注入点（1.21 已将格子绘制拆分为独立方法）导致的启动崩溃。Fixes a 1.21.1 startup crash.

## 1.1.0

### 一、新增功能 / New Features

#### 1. 随身次元袋（Pocket Dimension）
全新物品，占背包一格，右键打开界面，用于存放大量物品。A new item that takes one inventory slot; right-click to open.

- **多页无限储物**：每页 54 格（9×6），页数不限；同类物品自动合并为一条，数量可超过堆叠上限（long 计数），存再多也不占额外格子。Multipage unlimited storage: 54 slots per page, unlimited pages; identical items merge with no stack limit.
- **页管理**：界面左侧页列表，可新建页、删除空页、重命名页、切换页。Page management: create / delete empty / rename / switch.
- **跨页搜索**：按物品名称、物品 ID 或拼音（联动 JEC/PinIn）检索，结果平铺显示，超过一页时提示细化关键词。Cross-page search by name, ID or pinyin; refine hint when results exceed one page.
- **快捷收纳（三种方式）**：手持次元袋右键物品；手持物品时右键次元袋；手持次元袋右键物品栏空白处一键收纳背包（不含快捷栏）。Quick deposit in three ways: bag + right-click item; right-click bag while hovering; right-click inventory background to store the backpack (hotbar excluded).
- **安全存放**：不会因死亡、爆炸或火焰而丢失（死亡不掉落、受环境伤害不销毁）。Never lost to death, explosions or fire.
- **存档兼容**：旧版单页数据自动迁移为第 1 页。Legacy single-page data migrates to page 1 automatically.

#### 2. 锤炼箱（Tempering Box）
全新方块，用于离线锤炼【自适应】附魔装备。A new block for offline training of Adaptation-enchanted equipment.

- 界面共 12 槽：前 8 槽放带【自适应】附魔的装备，后 4 格放被捕捉实体（每格最多 64 个）。12 slots: 8 Adaptation equipment + 4 captured-entity slots (≤64 each).
- 每秒按被捕捉实体的数量为每件装备自动累积自适应层数（模拟持续受击训练）。Gains Adaptation stacks every second based on captured entities (simulates combat training).
- 右键打开，取出装备即可直接使用。Right-click to open.

#### 3. 透视眼镜（X-Ray Goggles）
全新物品，手持时透视地下矿物。A new item that reveals ores through walls while held.

- **白名单透视**：手持时非白名单方块不渲染（墙体透明），矿石及白名单方块正常显示；丢出或切换主手物品后自动恢复正常渲染。Whitelist x-ray; restores automatically when dropped or switched.
- **自定义透视**：Shift+右键打开方块选择界面，可手动开关任意方块的透视显示，支持"只看已开启"过滤。Shift + right-click to toggle any block; "enabled only" filter.
- **可选优化**：Create 引擎渲染自动隐藏；Embeddium/Sodium 下满亮度显示；透视期间自动移除黑暗/失明效果。Optional: hides Create/Flywheel engines, full-bright with Embeddium/Sodium, suppresses Darkness/Blindness.

### 二、已有功能更新与修复 / Updates & Fixes to Existing Features

#### 拆解台（Disassemble Table）
- **新增：锻造（Smithing）配方拆解**——如下界合金装备 → 锻造模板 + 基础装备 + 材料，拆解/合成保留附魔与 NBT。New: smithing recipe disassembly preserving enchantments and NBT.
- **新增：tacz 枪匠台配方**——材料按配方数量展开，产物按 GunId 精确匹配。New: tacz gunsmith recipes, GunId-exact output matching.
- **修复：拔刀剑"刀→刀"升级配方**——不再误删材料刀、不再剔除产物变体。Fix: Slashblade blade→blade upgrades no longer drop the material blade.
- **修复：锻造配方材料列表**——不再把最终产物当作材料。Fix: smithing material list no longer shows the product.
- **新增：模组升级配方兼容**——精妙背包等"自身升级"配方内容物/状态保留。New: Sophisticated Backpacks-style upgrade recipes preserve contents/state.
- **改进：产物预览与一键合成**——按九宫格材料实时合成预览，单击产出真实结果（保留输入 NBT）。Improvement: live preview & one-click craft preserving input NBT.
- **改进：材料展示**——同一材料多次出现合并为一个槽位（×数量），与 JEI 一致。Improvement: merged material slots like JEI.

#### 无限之源（Infinite Source）
- **修复：堆叠水桶**——取水不再把整叠桶改为装液状态（修复共享 NBT 状态错乱）。Fix: stacked buckets no longer convert the whole stack when filled.
- **改进：玻璃瓶**——任意数量堆叠均可取水。Improvement: glass bottles work in any quantity.

#### 自适应附魔（Adaptation）
- **改进：层数增长**——逐件独立判定，层数低于伤害的装备继续叠层。Improvement: per-piece layer gain.
- **新增：负面效果前置拒绝**——已适应的负面效果挂上前直接拒绝。New: adapted negative effects are blocked before applying.
- **新增：屏幕视觉效果屏蔽**——新配置项（默认开启）屏蔽失明/火焰/水雾/冰冻/传送门等屏幕效果。New: screen-effect suppression config (default on).
- **修复：层数数据异常**——NaN/无穷/负数自动归零。Fix: corrupted layer data resets to zero.

#### 嗜血附魔（Bloodthirsty）
- **改进：附魔范围**——可附在任意带攻击力的物品（镐/锹/锄/模组武器等）。Improvement: applies to any item with attack damage.

#### 铁砧球与捕捉实体（Anvil Orb / Captured Entity）
- **新增：发射器支持**——放入发射器红石触发按朝向投掷，每发消耗 1 个。New: dispenser support.

#### 镇魂灯（Guardian Lantern）
- **修复：破坏掉落**——只掉落一个携带配置的镇魂灯，不再重复掉落，搬起后保留配置。Fix: single drop carrying its configuration.

#### 微农场（Micro Farm）
- **修复：快捷移动**——目标范围限定容器槽，不再物品自合并数量翻倍；捕捉实体/食物优先入对应槽。Fix: shift-click no longer duplicates; entity/food prioritize dedicated slots.

### 兼容性 / Compatibility
Forge 1.20.1（Java 17）/ NeoForge 1.21.1（Java 21）。Create/Flywheel、Embeddium/Sodium、tacz、Sophisticated Backpacks 均为可选兼容（缺失时对应功能自动禁用）。
