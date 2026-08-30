# Survival Toolbox 生存工具箱 — Changelog / 更新日志

## 1.1.0

### 新增功能 / New Features

**随身次元袋（Pocket Dimension）** — 全新物品 / new item
- 多页无限储物：每页 54 格（9×6），页数不限；同类物品自动合并，数量无堆叠上限（long）
  Multipage unlimited storage: 54 slots per page, unlimited pages; identical items merge with no stack limit.
- 页管理：新建 / 删除（仅空页）/ 重命名 / 切换；跨页搜索（物品名、物品 ID、拼音）
  Page management (create / delete / rename / switch) and cross-page search by name, ID or pinyin.
- 快捷收纳三种方式：手持次元袋右键物品、物品栏内右键次元袋、手持次元袋右键空白处收纳背包（不含快捷栏）
  Three quick-deposit interactions: bag + right-click item, right-click bag on hovered item, right-click inventory background (hotbar excluded).
- 安全存放：不会因死亡、爆炸或火焰而丢失（免疫环境伤害与销毁）
  The bag is never lost to death, explosions or fire.
- 存档兼容：旧版单页数据自动迁移为第 1 页
  Legacy single-page data migrates automatically to page 1.

**锤炼箱（Tempering Box）** — 全新方块 / new block
- 12 槽位：8 件【自适应】附魔装备 + 4 格被捕捉实体（每格 ≤64）
  12 slots: 8 Adaptation-enchanted equipment + 4 captured-entity slots (≤64 each).
- 每秒按被捕捉实体数量为装备累积自适应层数（模拟持续受击，支持离线养成）
  Gains Adaptation stacks each second based on captured entities (simulates combat training).

**透视眼镜（X-Ray Goggles）** — 全新物品 / new item
- 白名单透视：非白名单方块不渲染，矿石及白名单方块正常显示；丢出或切换物品后自动恢复
  Whitelist x-ray: non-whitelisted blocks hidden, ores shown; restores automatically when dropped or switched.
- Shift+右键打开方块选择界面，可手动开关任意方块
  Shift + right-click opens the block selector to toggle any block.
- 兼容 Create/Flywheel：隐藏机械动力引擎渲染；兼容 Embeddium/Sodium：透视满亮度渲染
  Hides Create/Flywheel engine rendering; full-bright rendering with Embeddium/Sodium.

**拆解台（Disassemble Table）** — 功能增强 / enhancements
- 锻造（Smithing）配方：支持锻造产物拆解（如下界合金装备 → 模板 + 基础装备 + 材料），保留附魔与 NBT
  Smithing recipes supported: disassemble smithing outputs preserving enchantments and NBT.
- 枪匠台（tacz）配方、药水拆解（酿造链/喷溅/滞留/龙息）、附魔拆解（拆回附魔书）、全部拆解
  tacz gunsmith recipes, potion chains, enchantment splitting, bulk disassemble.
- 性能：配方索引异步构建，不阻塞服务器线程；兼容精妙背包升级配方内容保留
  Async recipe index (no server-thread stalls); Sophisticated Backpacks upgrade preservation.

**无限之源（Infinite Source）**
- 兼容桶堆叠与玻璃瓶取水 / Compatible with stacked buckets and glass bottles.

### 修复 / Fixes
1. 修复随身次元袋在 1.21.1 存取后物品丢失（新数据组件 API 语义变更）。
   Fixed items disappearing from the Pocket Dimension on 1.21.1 (data-component API semantics change).
2. 修复拆解台锻造配方将最终产物误列为材料（生产环境字段混淆）。
   Fixed smithing recipes listing the product as a material (obfuscated field names).
3. 修复 1.21.1 快捷收纳「全部收纳」被服务端槽位校验拒绝。
   Fixed deposit-all being rejected by server slot validation on 1.21.1.
4. 修复无限之源水桶共享 NBT 导致的状态丢失。
   Fixed Infinite Source buckets sharing NBT and losing state.
5. 修复透视在 Embeddium/Sodium 下不生效（光照数据注入适配）。
   Fixed x-ray with Embeddium/Sodium (light-data injection).

### 兼容性 / Compatibility
Forge 1.20.1（Java 17）/ NeoForge 1.21.1（Java 21）。Create/Flywheel、Embeddium/Sodium、tacz、Sophisticated Backpacks 均为可选兼容（缺失时对应功能自动禁用）。
