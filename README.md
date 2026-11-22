# MoreThingsVanilla

![MoreThingsVanilla 图标](./icon.png)

MoreThingsVanilla 是面向 Minecraft 26.2 的 Fabric Mod：用彩色台阶和楼梯搭建筑，用坐垫做能坐的座位，也可以让红石信号沿墙面或地板向上、向下传递。

## 基本信息

| 项 | 值 |
|---|---|
| Mod ID / 版本 | `more-things-vanilla` / `0.1.0` |
| 开发者 / 包名 | tt23xrstudio / `com.tt23xrstudio` |
| Minecraft / Java | 26.2 / Java 25 |
| Fabric Loader | 运行要求 ≥ 0.19.0；项目使用 0.19.5 构建 |
| Fabric API | 构建版本 `0.161.0+26.2` |
| 构建工具 | Gradle + Fabric Loom |

## 特性

- **建筑材质扩展**：给七种矿物块、十六色羊毛和十六色混凝土补齐台阶（游戏内称「半砖」）和楼梯，另加平滑石头楼梯。
- **原创红石组件**：竖直红石能从六面收发信号；竖直红石中继器和比较器可以把输出端朝上或朝下。红石单向传递器则从最先通电的一面取信号，送往其余五面。
- **十六色坐垫**：完整复刻原版坐垫。放到建筑表面后可以入座；坐垫是实体，因此也能放在半砖上，不必占据一整格方块。

## 新增内容一览

| 类别 | 数量 | 在游戏中可以做什么 |
|---|---:|---|
| 台阶 | 39 | 用矿物块铺半高地板，或给屋檐配上羊毛、混凝土的颜色 |
| 楼梯 | 40 | 同一套材质可做阶梯和转角；比台阶多一种平滑石头 |
| 竖直红石组件 | 3 | 跨楼层布线、延时、比较或作差 |
| 红石单向传递器 | 1 | 从一个输入面向多个方向分发信号 |
| 坐垫物品 | 16 | 制作、改色并摆放可骑乘的座位 |

共 **83 种方块、99 种物品**（83 种方块物品 + 16 种坐垫）。[建筑方块全表](docs_mt_v/blocks.md)逐项列出了所有材质和 ID。

### 配方与用法

| 成品 | 工作台配方 |
|---|---|
| 6 个台阶 / 4 个楼梯 | 3 个相同材料横排 / 6 个相同材料摆成阶梯形 |
| 1 个坐垫 | 3 个同色羊毛半砖横排；已有坐垫也可以用染料改色 |
| 1 个竖直红石 | 单独放入 1 份红石粉 |
| 1 个竖直红石中继器 | 左列放 3 块石头；中列从上到下放红石火把、红石粉、红石火把 |
| 1 个竖直红石比较器 | 左列放 3 块石头；中列从上到下放红石火把、下界石英、红石火把；右列中格再放 1 个红石火把 |
| 1 个红石单向传递器 | 原版红石中继器 ×1、红石火把 ×1、红石粉 ×2，无序合成 |

### 红石行为示例

- **把信号送上楼**：在红石块正上方叠三块竖直红石，从下到上电平依次为 `15 → 14 → 13`。要恢复到 15，可在柱顶朝上放一个竖直红石中继器。
- **比较两个强度**：竖直比较器后端输入 10、侧面输入 6 时，比较模式输出 10；切换到减法模式后输出 4。后端始终是输出面的对面，即使输出朝上也是如此。
- **分发弱信号**：只给单向传递器的一面输入强度 7，中继模式从其余五面输出 15；切到延续模式则输出 7。接线和多面同时来电的处理见[传递器指南](docs_mt_v/one-way-transmitter.md)。

## 快速上手

1. 使用 Java 25 启动 Minecraft 26.2，安装 Fabric Loader 及对应版本的 Fabric API。
2. 把 Fabric API 和本 Mod 的运行用 JAR 放入该实例的 `mods/`，选择 Fabric 配置启动。
3. 在创造模式打开「更多内容-原版」标签页，或在生存模式先用 3 块羊毛合成半砖，再做一个同色坐垫。[安装与首次验证](docs_mt_v/getting-started.md)

## 版本说明与 26.4 计划

当前 `0.1.0` 的目标是 **26.2**。在未来的 26.4 构建中，计划将 64 个十六色羊毛、混凝土台阶与楼梯交给原版注册，并用 DataFixer 将存档里的对应 ID 从 `more-things-vanilla:` 迁移到 `minecraft:`。例如，`more-things-vanilla:red_wool_slab` 将对应 `minecraft:red_wool_slab`。**26.2 版不执行这项转换。**

七种矿物的 14 个建筑方块、平滑石头楼梯和四个红石方块计划继续保留在本 Mod 的命名空间内。

## 推荐搭配 Mod

`vanilla-refit-fabric`（Vanilla Refit Fabric，原版精修）同样扩展建筑形态与细节，适合想增加建筑选材的玩家。它是可选搭配。

## 项目结构

```text
docs_mt_v/                          玩家文档与方块清单
src/main/java/com/tt23xrstudio/     公共入口、方块、红石、坐垫和 GameTest
src/client/                         客户端入口、坐垫渲染等
src/main/resources/assets/          名称、模型、纹理与音效定义
src/main/resources/data/            合成与切石配方、掉落表、标签
gradle.properties                   游戏、加载器与依赖版本
```

从源码构建：Windows 在项目根目录运行 `.\gradlew.bat build`，成品在 `build/libs/`。开发客户端与测试任务见[快速上手](docs_mt_v/getting-started.md)。

## 文档导航

[文档首页与阅读路径](docs_mt_v/README.md) · [快速上手](docs_mt_v/getting-started.md) · [台阶与楼梯全表](docs_mt_v/blocks.md) · [竖直红石](docs_mt_v/redstone.md) · [红石单向传递器](docs_mt_v/one-way-transmitter.md) · [坐垫](docs_mt_v/cushion.md) · [常见问题](docs_mt_v/faq.md)

## 许可证

[GNU General Public License v3.0](LICENSE)。
