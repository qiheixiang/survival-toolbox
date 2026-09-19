# Survival Toolbox 生存工具箱

A Minecraft survival-enhancement mod featuring automated farming, item disassembly, guardian defense, enchantment upgrades, terrain editing and entity capture.

一款 Minecraft 生存增强模组，包含自动化农场、物品拆解、守卫照明、附魔强化、地形编辑与实体捕捉。

## 版本 / Versions

| 目录 | Minecraft | 加载器 | 最新版本 |
|------|-----------|--------|---------|
| [`1.20.1/`](1.20.1/) | 1.20.1 | Forge 47.x | 1.2.0 |
| [`1.21.1/`](1.21.1/) | 1.21.1 | NeoForge 21.1.x | 1.2.0 |

详细更新内容见 [CHANGELOG.md](CHANGELOG.md)。

## 主要功能 / Features

- **随身次元袋 Pocket Dimension**：多页无限储物（同类合并、数量无上限）、流体与物品同页混排、共享存储模式（末影箱式）、托盘（出货工作台）、跨页搜索、多种快捷收纳、长按提起整格、整理（按注册名/模组/数量/标签四档）、防丢失
- **次元袋功能页 Pocket Dimension Panels**：右侧展开的熔炼、铁砧、锻造台、工作台、拆解、切石机、磁铁、补货页——逻辑全部复用原版与拆解台自身实现
- **拆解台 Disassembly Table**：逆向合成——把成品拆回材料，或放材料找配方；支持锻造台、枪匠台（tacz）与自定义仪式类配方，含药水/附魔拆解
- **JEI 集成 JEI Integration**：配方「+」号可直接从次元袋取料（按当前打开的功能页匹配、按配方位置摆放、材料不足时禁用、工作台页可切换自动补充）
- **交易机 Trade Machine**：记录村民报价并随身成交，报价可按产物搜索
- **创造口袋 Creative Pocket**：右键打开创造模式物品栏，生存下也能取用（作弊级物品）
- **锤炼箱 Tempering Box**：离线锤炼——用被捕捉实体为自适应附魔装备自动累积层数
- **透视眼镜 X-Ray Goggles**：手持透视矿石、自定义透视方块（每副独立白名单）、兼容 Create/Flywheel 引擎隐藏与 Embeddium/Sodium 满亮
- **智慧农场 Smart Farm**：全自动耕地/播种/收获/浇水/吸收掉落物
- **镇魂灯 Guardian Lantern**：照明、阻止刷怪、自动攻击（配武器与黑白名单）
- **混沌篝火 Feast / Chaos Bonfire**：范围喂食/增益
- **自适应附魔 Adaptation**：受伤叠层→减伤/护盾/回血/飞行/复活，逐步适应负面效果与低水分屏幕模糊
- **嗜血附魔 Bloodthirsty**：击杀吸血转化为攻击力，可附于弓弩与模组枪械
- **缴械法杖 Disarm Staff**：卸下生物全部装备与饰品（Curios 兼容）
- **地形编辑器 Terrain Editor**：范围编辑地形
- **铁砧球 Anvil Orb**：发射捕捉实体
- **附魔数据交换 Enchantment Transfer**：装备间互换附魔数据
- **无限之源 Infinite Source**：取水不消耗，兼容桶堆叠与玻璃瓶

## 构建 / Build

Each folder is a standalone Gradle project. Build with Java 17 (1.20.1) / Java 21 (1.21.1):

每个目录都是独立的 Gradle 项目。用 Java 17（1.20.1）/ Java 21（1.21.1）构建：

```bash
cd 1.20.1   # 或 / or 1.21.1
./gradlew build
# jar 输出在 build/libs/
```

> 注意：`1.20.1/libs/` 目录已包含 Flywheel、Embeddium 与 Legendary Survival Overhaul 的**编译期专用** jar（仅用于 Mixin 注解处理器校验，不打包），详见 [`1.20.1/libs/README.md`](1.20.1/libs/README.md)。

## 下载 / Download

Release 页面下载对应版本的 jar：**Releases** → 选版本 → 下载 `survival-toolbox-forge1.20.1-x.x.x.jar` 或 `survival-toolbox-neoforge1.21.1-x.x.x.jar`，放入 mods 文件夹。

Download the matching jar from the **Releases** page and place it in your `mods` folder.

## License

See [LICENSE](LICENSE).
