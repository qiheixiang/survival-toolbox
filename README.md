# Survival Toolbox 生存工具箱

A Minecraft survival-enhancement mod featuring automated farming, item disassembly, guardian defense, enchantment upgrades, terrain editing and entity capture.

一款 Minecraft 生存增强模组，包含自动化农场、物品拆解、守卫照明、附魔强化、地形编辑与实体捕捉。

## 版本 / Versions

| 目录 | Minecraft | 加载器 | 最新版本 |
|------|-----------|--------|---------|
| [`1.20.1/`](1.20.1/) | 1.20.1 | Forge 47.x | 1.0.1 |
| [`1.21.1/`](1.21.1/) | 1.21.1 | NeoForge 21.1.x | 1.0.0 |

## 主要功能 / Features

- **拆解台 Disassembly Table**：逆向合成——把成品拆回材料，或放材料找配方
- **智慧农场 Smart Farm**：全自动耕地/播种/收获/浇水/吸收掉落物
- **镇魂灯 Guardian Lantern**：照明、阻止刷怪、自动攻击（配武器与黑白名单）
- **混沌篝火 Feast / Chaos Bonfire**：范围喂食/增益
- **自适应附魔 Adaptation**：受伤叠层→减伤/护盾/回血/飞行/复活，并适应负面效果
- **嗜血附魔 Bloodthirsty**：击杀吸血转化为攻击力
- **地形编辑器 Terrain Editor**：范围编辑地形
- **铁砧球 Anvil Orb**：发射捕捉实体
- **附魔数据交换 Enchantment Transfer**：装备间互换附魔数据

## 构建 / Build

Each folder is a standalone Gradle project. Build with Java 17 (1.20.1) / Java 21 (1.21.1):

每个目录都是独立的 Gradle 项目。用 Java 17（1.20.1）/ Java 21（1.21.1）构建：

```bash
cd 1.20.1   # 或 / or 1.21.1
./gradlew build
# jar 输出在 build/libs/
```

## 下载 / Download

Release 页面下载对应版本的 jar：**Releases** → 选版本 → 下载 `survival-toolbox-forge1.20.1-x.x.x.jar` 或 `survival-toolbox-neoforge1.21.1-x.x.x.jar`，放入 mods 文件夹。

Download the matching jar from the **Releases** page and place it in your `mods` folder.

## License

See [LICENSE](LICENSE).
