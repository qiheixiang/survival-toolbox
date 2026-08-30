# Survival Toolbox 生存工具箱 — Changelog / 更新日志

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
