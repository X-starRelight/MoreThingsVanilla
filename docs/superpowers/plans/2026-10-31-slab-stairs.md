# 半砖与楼梯（计划 2）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 注册 39 个半砖 + 40 个楼梯（含方块注册框架），补齐模型/语言/战利品/标签/配方，游戏内可用。

**Architecture:** `MtvBlocks` 数据驱动批量注册（方块+BlockItem+创造标签页+可燃登记）；资源文件由 PowerShell 脚本从本地 26.2/26.4 Jar 模板生成并自校验；gametest 断言注册、掉落、可燃、标签、配方加载五个契约。计划 1 遗留的铁块占位物品在 Task 1 随首个真实方块落地时移除。

**Tech Stack:** Minecraft 26.2 / Java 25 / fabric-loom 1.18 / Fabric API 0.161.0+26.2 / PowerShell 5.1 生成脚本

**分支:** 继续在 `feature/infra-refactor` 上提交（基线含计划 1 全部 13 条提交）。

---

## 关键事实（已逐一 javap/解包核实，实现者勿重新推导）

| 事实 | 值 |
|---|---|
| `SlabBlock` 构造器 | `public SlabBlock(BlockBehaviour.Properties)` |
| `StairBlock` 构造器 | **`protected StairBlock(BlockState, Properties)` → 必须子类包装** |
| 属性复制 | `BlockBehaviour.Properties.ofFullCopy(BlockBehaviour)`（硬度/音效等与基块一致） |
| 注册 | `Registry.register(BuiltInRegistries.BLOCK, Identifier, T)` / `BuiltInRegistries.ITEM` 同 |
| 26.2 颜色字段重构 | **`Blocks.WHITE_WOOL` 等已不存在**；用 `Blocks.WOOL.white()`、`Blocks.CONCRETE.black()` 等（`ColorCollection<Block>`，accessor：white/orange/magenta/lightBlue/yellow/lime/pink/gray/lightGray/cyan/purple/blue/brown/green/red/black） |
| 矿物/平滑石字段 | `Blocks.IRON_BLOCK` 等 7 个、`Blocks.SMOOTH_STONE` 均存在 |
| id 推导 | `BuiltInRegistries.BLOCK.getKey(base).getPath()` + 后缀 `_slab`/`_stairs` |
| 羊毛可燃值 | **(igniteOdds=30, burnOdds=60)**（FireBlock.bootStrap 字节码：BootstrapMethods #1 → `lambda$bootStrap$0` → `setFlammable(block, 30, 60)`） |
| Fabric 可燃 API | `FlammableBlockRegistry.getDefaultInstance().add(Block, int, int)`；读取 `.get(Block)` → `Entry.getIgniteOdds()/getBurnOdds()`（`get` 来自 `Block2ObjectMap`，可空） |
| 配方 key | `ResourceKey.create(Registries.RECIPE, Identifier)`（注意是 **`RECIPE`** 单数）；`MinecraftServer.getRecipeManager().byKey(key)` → `Optional<RecipeHolder<?>>` |
| 战利品掉落测试 | `GameTestHelper.destroyBlock(pos)` + `succeedWhen(() -> assertItemEntityPresent(item, pos, radius))` + `setBlock(pos, Block)` |
| 标签测试 | `GameTestHelper.assertBlockTag(TagKey<Block>, BlockPos)`；`TagKey.create(Registries.BLOCK, Identifier)` |
| **战利品表格式** | **26.2 与 26.4 不兼容**（26.2 用 `functions/block` 键，26.4 用 `modifier/state` 键）→ 战利品**只能**用 26.2 jar 模板 |
| 配方格式 | crafting_shaped / stonecutting / crafting_shapeless(dye) 在 26.2 与 26.4 **完全一致**（`result:{count,id}`、`ingredient` 字符串、嵌套数组 ingredients）→ 可从 26.4 抽取 |
| 26.4 有的内容 | 羊毛系（16 色×半砖/楼梯）+ 混凝土系（16 色×半砖/楼梯）的**配方**；矿物系、平滑石头楼梯 26.4 **没有**（原创） |
| 26.4 无的内容 | `white_wool_slab_from_*_stonecutting`（羊毛切石机配方不存在）→ 抽取按「result id 命中」自动处理 |
| 资源路径（26.2） | blocktags `data/<ns>/tags/block/`、loot `data/<ns>/loot_table/blocks/`、recipe `data/<ns>/recipe/`、物品模型定义 `assets/<ns>/items/<id>.json` |
| 石头切石机 1 石板→**2** 半砖 | 26.2 `smooth_stone_slab_from_smooth_stone_stonecutting` result count=2（照抄 26.4 混凝土的 count=2，勿改） |
| 标签策略（用户拍板 A） | 羊毛→新建 `minecraft:wool_slabs`/`wool_stairs`；混凝土→新建 `concrete_slabs`/`concrete_stairs` + 继承 `mineable/pickaxe`；**不进** `wool`/`concrete` 原标签（26.2 `#minecraft:wool` 被画作配方引用）；矿物→继承 `mineable/pickaxe` + `needs_*_tool`（**不进** `beacon_base_blocks`） |
| 语言条目 | key `block.more-things-vanilla.<id>`；zh 名以 设计.md「材质」章节为准；en 按原版惯例 |
| 参考 Jar 路径 | 26.4: `D:\minecraft\.minecraft\versions\26.4-snapshot-6-Fabric 0.20.0\26.4-snapshot-6-Fabric 0.20.0.jar`；26.2: `$env:USERPROFILE\.gradle\caches\fabric-loom\26.2\minecraft-merged.jar` |
| gametest 判定 | 跑前 `Remove-Item build\gametest\report.xml`；`.\gradlew.bat runGametest`（timeout 900000）；**只看报告 XML 有无 `<failure>` 元素**，gradle 退出码不作唯一依据 |
| 明确非目标 | 不生成配方解锁 advancement（配方仍可手工合成/在切石机使用，仅不进配方书自动解锁） |

---

## File Structure

- Create: `src/main/java/com/tt23xrstudio/block/MtvBlocks.java` — 方块注册框架 + 79 方块
- Modify: `src/main/java/com/tt23xrstudio/MoreThingsVanilla.java` — 移除铁块占位、调用 `MtvBlocks.registerAll()`
- Modify: `src/main/java/com/tt23xrstudio/test/MtvGameTests.java` — 新增 5 个 gametest
- Create: `scripts/gen-block-assets.ps1` — blockstate/模型/物品模型定义/语言条目生成（模板取自 26.2 jar）
- Create: `scripts/gen-loot-tags.ps1` — 战利品表（26.2 模板）+ 标签（基块成员关系自动继承）
- Create: `scripts/gen-recipes.ps1` — 矿物系/平滑石手写 + 26.4 羊毛/混凝土系抽取
- Create（脚本产出）: `src/main/resources/assets/more-things-vanilla/blockstates/*.json`（79）、`models/block/*.json`（198 = 39×2 + 40×3）、`items/*.json`（79）、`lang/{zh_cn,en_us}.json`（各 79 条块条目）
- Create（脚本产出）: `src/main/resources/data/more-things-vanilla/loot_table/blocks/*.json`（79）
- Create（脚本产出）: `src/main/resources/data/minecraft/tags/block/wool_slabs.json`、`wool_stairs.json`、`concrete_slabs.json`、`concrete_stairs.json`、`mineable/pickaxe.json`、命中的 `needs_*_tool.json`

**79 方块 id 清单**（= 设计.md 材质章节；同时是 gametest 的 `ALL_NEW_BLOCK_IDS`）：

- 半砖 39：`iron/gold/redstone/emerald/lapis/diamond/netherite_block_slab`（7）+ 16 色 `*_wool_slab` + 16 色 `*_concrete_slab`
- 楼梯 40：`smooth_stone_stairs`（1）+ `iron/gold/redstone/emerald/lapis/diamond/netherite_block_stairs`（7）+ 16 色 `*_wool_stairs` + 16 色 `*_concrete_stairs`
- 颜色顺序（设计.md）：black, white, gray, light_gray, red, orange, yellow, lime, green, cyan, light_blue, blue, purple, magenta, pink, brown

> **铺垫（extshape 式转换，用户拍板 A）**：64 个羊毛/混凝土系 id 与 26.4 原版完全平行（仅命名空间不同），未来升级 26.4 构建时移除其注册并加 DataFixer 重命名（`more-things-vanilla:* → minecraft:*`，参考 extshape `SchemasMixin`）。**转换代码不进本计划**，路线详见 `设计.md`「未来计划：升级 26.4 时的方块转换」；本计划仅在 `MtvBlocks.registerAll()` 加分组注释。

---

### Task 1: 方块注册框架（MtvBlocks）+ 79 方块注册 + 移除占位

**Files:**
- Create: `src/main/java/com/tt23xrstudio/block/MtvBlocks.java`
- Modify: `src/main/java/com/tt23xrstudio/MoreThingsVanilla.java`
- Test: `src/main/java/com/tt23xrstudio/test/MtvGameTests.java`

- [x] **Step 1: 写失败测试（注册断言）**

在 `MtvGameTests` 中新增常量与测试（完整代码，直接落盘）：

```java
	private static final String[] ALL_NEW_BLOCK_IDS = {
			// 半砖 39
			"iron_block_slab", "gold_block_slab", "redstone_block_slab",
			"emerald_block_slab", "lapis_block_slab", "diamond_block_slab", "netherite_block_slab",
			"black_wool_slab", "white_wool_slab", "gray_wool_slab", "light_gray_wool_slab",
			"red_wool_slab", "orange_wool_slab", "yellow_wool_slab", "lime_wool_slab",
			"green_wool_slab", "cyan_wool_slab", "light_blue_wool_slab", "blue_wool_slab",
			"purple_wool_slab", "magenta_wool_slab", "pink_wool_slab", "brown_wool_slab",
			"black_concrete_slab", "white_concrete_slab", "gray_concrete_slab", "light_gray_concrete_slab",
			"red_concrete_slab", "orange_concrete_slab", "yellow_concrete_slab", "lime_concrete_slab",
			"green_concrete_slab", "cyan_concrete_slab", "light_blue_concrete_slab", "blue_concrete_slab",
			"purple_concrete_slab", "magenta_concrete_slab", "pink_concrete_slab", "brown_concrete_slab",
			// 楼梯 40
			"smooth_stone_stairs",
			"iron_block_stairs", "gold_block_stairs", "redstone_block_stairs",
			"emerald_block_stairs", "lapis_block_stairs", "diamond_block_stairs", "netherite_block_stairs",
			"black_wool_stairs", "white_wool_stairs", "gray_wool_stairs", "light_gray_wool_stairs",
			"red_wool_stairs", "orange_wool_stairs", "yellow_wool_stairs", "lime_wool_stairs",
			"green_wool_stairs", "cyan_wool_stairs", "light_blue_wool_stairs", "blue_wool_stairs",
			"purple_wool_stairs", "magenta_wool_stairs", "pink_wool_stairs", "brown_wool_stairs",
			"black_concrete_stairs", "white_concrete_stairs", "gray_concrete_stairs", "light_gray_concrete_stairs",
			"red_concrete_stairs", "orange_concrete_stairs", "yellow_concrete_stairs", "lime_concrete_stairs",
			"green_concrete_stairs", "cyan_concrete_stairs", "light_blue_concrete_stairs", "blue_concrete_stairs",
			"purple_concrete_stairs", "magenta_concrete_stairs", "pink_concrete_stairs", "brown_concrete_stairs"
	};

	@GameTest
	public void allBlocksRegistered(GameTestHelper helper) {
		for (String path : ALL_NEW_BLOCK_IDS) {
			Identifier id = Identifier.fromNamespaceAndPath(MoreThingsVanilla.MOD_ID, path);
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "block not registered: " + id);
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "item not registered: " + id);
		}
		helper.succeed();
	}
```

新增 import：`net.minecraft.resources.Identifier`。

- [x] **Step 2: 跑 gametest 确认红**

```powershell
Remove-Item build\gametest\report.xml -ErrorAction SilentlyContinue
.\gradlew.bat runGametest *> build\gametest-run.log
```

Expected: 报告 XML 存在且含 `<failure`，消息含 `block not registered: more-things-vanilla:iron_block_slab`。

- [x] **Step 3: 实现 MtvBlocks（完整代码）**

`src/main/java/com/tt23xrstudio/block/MtvBlocks.java`：

```java
package com.tt23xrstudio.block;

import java.util.LinkedHashMap;
import java.util.Map;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 方块注册框架（计划 2 起使用）：按基块批量注册半砖/楼梯。
 * 每个条目 = 方块 + BlockItem（自动加入创造标签页）+ 羊毛系可燃登记。
 */
public final class MtvBlocks {
	private MtvBlocks() {}

	/** 本计划注册的全部方块，key 为 id 路径（如 iron_block_slab）。 */
	public static final Map<String, Block> ALL = new LinkedHashMap<>();

	/** 羊毛基块可燃值，与原版一致（FireBlock.bootStrap: setFlammable(block, 30, 60)）。 */
	private static final int WOOL_IGNITE_ODDS = 30;
	private static final int WOOL_BURN_ODDS = 60;

	private static final Block[] MINERAL_BASES = {
			Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.REDSTONE_BLOCK,
			Blocks.EMERALD_BLOCK, Blocks.LAPIS_BLOCK, Blocks.DIAMOND_BLOCK,
			Blocks.NETHERITE_BLOCK
	};

	public static void registerAll() {
		// 铺垫（extshape 式转换）：羊毛/混凝土系共 64 个方块的 id 与 26.4 原版完全平行（仅命名空间不同）。
		// 将来升级 26.4 构建时移除这 64 个注册，并按 extshape SchemasMixin 模式加 DataFixer 把
		// more-things-vanilla:* 重命名为 minecraft:*（详见 设计.md「未来计划：升级 26.4 时的方块转换」）。
		// 转换代码绝不进 26.2 构建。矿物系 14 个 + 平滑石头楼梯为永久原创，始终保留。
		// 半砖（设计.md 材质章节顺序：矿物 → 羊毛 → 混凝土）
		for (Block base : MINERAL_BASES) registerSlab(base, false);
		for (Block base : woolBases()) registerSlab(base, true);
		for (Block base : concreteBases()) registerSlab(base, false);
		// 楼梯（平滑石头在前）
		registerStairs(Blocks.SMOOTH_STONE, false);
		for (Block base : MINERAL_BASES) registerStairs(base, false);
		for (Block base : woolBases()) registerStairs(base, true);
		for (Block base : concreteBases()) registerStairs(base, false);
	}

	private static void registerSlab(Block base, boolean flammable) {
		String path = basePath(base) + "_slab";
		finish(path, new SlabBlock(BlockBehaviour.Properties.ofFullCopy(base)), flammable);
	}

	private static void registerStairs(Block base, boolean flammable) {
		String path = basePath(base) + "_stairs";
		finish(path, new ModStairBlock(base.defaultBlockState(), BlockBehaviour.Properties.ofFullCopy(base)), flammable);
	}

	private static void finish(String path, Block block, boolean flammable) {
		Registry.register(BuiltInRegistries.BLOCK, MoreThingsVanilla.id(path), block);
		BlockItem item = Registry.register(BuiltInRegistries.ITEM, MoreThingsVanilla.id(path),
				new BlockItem(block, new Item.Properties()));
		MoreThingsVanilla.TAB_ITEMS.add(item);
		ALL.put(path, block);
		if (flammable) {
			FlammableBlockRegistry.getDefaultInstance().add(block, WOOL_IGNITE_ODDS, WOOL_BURN_ODDS);
		}
	}

	private static String basePath(Block base) {
		return BuiltInRegistries.BLOCK.getKey(base).getPath();
	}

	/** 26.2 起颜色方块改为 ColorCollection（Blocks.WHITE_WOOL 已移除）。顺序=设计.md 材质章节。 */
	private static Block[] woolBases() {
		return new Block[] {
				Blocks.WOOL.black(), Blocks.WOOL.white(), Blocks.WOOL.gray(), Blocks.WOOL.lightGray(),
				Blocks.WOOL.red(), Blocks.WOOL.orange(), Blocks.WOOL.yellow(), Blocks.WOOL.lime(),
				Blocks.WOOL.green(), Blocks.WOOL.cyan(), Blocks.WOOL.lightBlue(), Blocks.WOOL.blue(),
				Blocks.WOOL.purple(), Blocks.WOOL.magenta(), Blocks.WOOL.pink(), Blocks.WOOL.brown()
		};
	}

	private static Block[] concreteBases() {
		return new Block[] {
				Blocks.CONCRETE.black(), Blocks.CONCRETE.white(), Blocks.CONCRETE.gray(), Blocks.CONCRETE.lightGray(),
				Blocks.CONCRETE.red(), Blocks.CONCRETE.orange(), Blocks.CONCRETE.yellow(), Blocks.CONCRETE.lime(),
				Blocks.CONCRETE.green(), Blocks.CONCRETE.cyan(), Blocks.CONCRETE.lightBlue(), Blocks.CONCRETE.blue(),
				Blocks.CONCRETE.purple(), Blocks.CONCRETE.magenta(), Blocks.CONCRETE.pink(), Blocks.CONCRETE.brown()
		};
	}

	/** StairBlock 构造器为 protected，子类转接（26.2）。 */
	private static final class ModStairBlock extends StairBlock {
		ModStairBlock(BlockState baseState, BlockBehaviour.Properties properties) {
			super(baseState, properties);
		}
	}
}
```

- [x] **Step 4: 接线 MoreThingsVanilla + 移除铁块占位**

`onInitialize()` 中：删除占位两行注释 + `TAB_ITEMS.add(Items.IRON_BLOCK);`，在标签页注册之后加 `MtvBlocks.registerAll();`；删除 `import net.minecraft.world.item.Items;`（已无引用），新增 `import com.tt23xrstudio.block.MtvBlocks;`。改后方法体：

```java
	@Override
	public void onInitialize() {
		Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				CREATIVE_TAB_KEY,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.more_things_vanilla"))
						// 图标暂用铁块；计划 4 竖直红石完成后可换为竖直红石物品
						.icon(() -> new ItemStack(Blocks.IRON_BLOCK))
						.displayItems((context, output) -> TAB_ITEMS.forEach(output::accept))
						.build()
		);
		// 计划 2 的半砖/楼梯（占位铁块已随首个真实方块移除，见计划 1 Task 6 约定）
		MtvBlocks.registerAll();
		LOGGER.info("MoreThingsVanilla loaded");
	}
```

- [x] **Step 5: 跑 gametest 确认绿**

同 Step 2 命令。Expected: 报告存在、**无** `<failure>` 元素、gradle exit 0。既有 `creativeTabRegistered` 断言（hasAnyItems）此时由 79 个真实物品守护。

- [x] **Step 6: 编译全量 + 提交**

```powershell
.\gradlew.bat build
git add -A
git commit -m "feat: register 39 slabs and 40 stairs via MtvBlocks framework"
```

Expected: BUILD SUCCESSFUL；提交后 `git status --short` 干净。

---

### Task 2: 资源生成脚本（blockstate / 模型 / 物品模型定义 / 语言）

**Files:**
- Create: `scripts/gen-block-assets.ps1`
- Create（脚本产出）: `assets/more-things-vanilla/blockstates/` 79 个、`models/block/` 198 个、`items/` 79 个、`lang/{zh_cn,en_us}.json` 全量重写

- [x] **Step 1: 写脚本**

`scripts/gen-block-assets.ps1`（完整内容）。要点：模板从 26.2 jar 抽取（格式与运行版本一致）；替换按「最长优先 + 带引号精确匹配」；每个产出文件 `ConvertFrom-Json` 自校验；数量断言（blockstates=79、models=198、items=79、lang 每文件 79 条块条目 + 1 条 itemGroup）。

```powershell
# 生成 79 个新方块的 blockstate / block model / items 模型定义 / 语言条目。
# 结构模板全部取自本地 26.2 官方 jar（与运行版本格式一致），基块纹理沿用原版。
param(
	[string]$MergedJar = "$env:USERPROFILE\.gradle\caches\fabric-loom\26.2\minecraft-merged.jar",
	[string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$ns = 'more-things-vanilla'
$colors = [ordered]@{
	black = @('黑色', 'Black'); white = @('白色', 'White'); gray = @('灰色', 'Gray')
	light_gray = @('淡灰色', 'Light Gray'); red = @('红色', 'Red'); orange = @('橙色', 'Orange')
	yellow = @('黄色', 'Yellow'); lime = @('黄绿色', 'Lime'); green = @('绿色', 'Green')
	cyan = @('青色', 'Cyan'); light_blue = @('浅蓝色', 'Light Blue'); blue = @('蓝色', 'Blue')
	purple = @('紫色', 'Purple'); magenta = @('品红色', 'Magenta'); pink = @('粉红色', 'Pink')
	brown = @('棕色', 'Brown')
}
$minerals = [ordered]@{
	iron_block = @('铁块', 'Iron Block'); gold_block = @('金块', 'Gold Block')
	redstone_block = @('红石块', 'Redstone Block'); emerald_block = @('绿宝石块', 'Emerald Block')
	lapis_block = @('青金石块', 'Lapis Block'); diamond_block = @('钻石块', 'Diamond Block')
	netherite_block = @('下界合金块', 'Netherite Block')
}

# ---- 构建 79 条目表：id / 基块纹理路径 / kind / zh / en ----
$entries = @()
foreach ($k in $minerals.Keys) {
	$entries += @{ id = "${k}_slab";     base = $k; kind = 'slab';   zh = "$($minerals[$k][0])半砖"; en = "$($minerals[$k][1]) Slab" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_wool_slab";     base = "${c}_wool";     kind = 'slab';   zh = "$($colors[$c][0])羊毛半砖"; en = "$($colors[$c][1]) Wool Slab" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_concrete_slab"; base = "${c}_concrete"; kind = 'slab';   zh = "$($colors[$c][0])混凝土半砖"; en = "$($colors[$c][1]) Concrete Slab" }
}
$entries += @{ id = 'smooth_stone_stairs'; base = 'smooth_stone'; kind = 'stairs'; zh = '平滑石头楼梯'; en = 'Smooth Stone Stairs' }
foreach ($k in $minerals.Keys) {
	$entries += @{ id = "${k}_stairs";     base = $k; kind = 'stairs'; zh = "$($minerals[$k][0])楼梯"; en = "$($minerals[$k][1]) Stairs" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_wool_stairs";     base = "${c}_wool";     kind = 'stairs'; zh = "$($colors[$c][0])羊毛楼梯"; en = "$($colors[$c][1]) Wool Stairs" }
}
foreach ($c in $colors.Keys) {
	$entries += @{ id = "${c}_concrete_stairs"; base = "${c}_concrete"; kind = 'stairs'; zh = "$($colors[$c][0])混凝土楼梯"; en = "$($colors[$c][1]) Concrete Stairs" }
}
if ($entries.Count -ne 79) { throw "entry count $($entries.Count) != 79" }

# ---- 从 26.2 jar 抽模板 ----
$zip = [System.IO.Compression.ZipFile]::OpenRead($MergedJar)
function Read-JarEntry([string]$name) {
	$e = $zip.Entries | Where-Object FullName -eq $name
	if (-not $e) { throw "template missing in jar: $name" }
	$sr = New-Object System.IO.StreamReader($e.Open())
	try { $sr.ReadToEnd() } finally { $sr.Close() }
}
$tplSlabBlockstate = Read-JarEntry 'assets/minecraft/blockstates/smooth_stone_slab.json'
$tplSlabModel      = Read-JarEntry 'assets/minecraft/models/block/smooth_stone_slab.json'
$tplSlabTopModel   = Read-JarEntry 'assets/minecraft/models/block/smooth_stone_slab_top.json'
$tplSlabItem       = Read-JarEntry 'assets/minecraft/items/smooth_stone_slab.json'
$tplStairsBlockstate = Read-JarEntry 'assets/minecraft/blockstates/stone_stairs.json'
$tplStairsModel      = Read-JarEntry 'assets/minecraft/models/block/stone_stairs.json'
$tplStairsInner      = Read-JarEntry 'assets/minecraft/models/block/stone_stairs_inner.json'
$tplStairsOuter      = Read-JarEntry 'assets/minecraft/models/block/stone_stairs_outer.json'
$tplStairsItem       = Read-JarEntry 'assets/minecraft/items/stone_stairs.json'
$zip.Dispose()

# 替换：字面 .Replace（无正则元字符风险），带引号精确匹配，最长优先。
# 模板中模型引用形如 "minecraft:block/smooth_stone_slab_top"（单冒号命名空间），输出 "more-things-vanilla:block/<id>_top"。
function Convert-Template([string]$text, [string]$tplId, [string]$tplBase, [string]$id, [string]$base) {
	$text = $text.Replace('"minecraft:block/' + $tplId + '_top"',   '"more-things-vanilla:block/' + $id + '_top"')
	$text = $text.Replace('"minecraft:block/' + $tplId + '_inner"', '"more-things-vanilla:block/' + $id + '_inner"')
	$text = $text.Replace('"minecraft:block/' + $tplId + '_outer"', '"more-things-vanilla:block/' + $id + '_outer"')
	$text = $text.Replace('"minecraft:block/' + $tplId + '"',       '"more-things-vanilla:block/' + $id + '"')
	$text = $text.Replace('"minecraft:' + $tplId + '"',             '"more-things-vanilla:' + $id + '"')
	$text = $text.Replace('"minecraft:block/' + $tplBase + '"',     '"minecraft:block/' + $base + '"')
	return $text
}
```

> 正确性依据：① 先替 `_top/_inner/_outer` 再替裸 id，避免前缀截断；② `"minecraft:block/stone"` 不会误伤 `"minecraft:block/stone_stairs"`（带闭引号精确匹配）；③ 模板 blockstate 的 double 变体与模型纹理同为 `"minecraft:block/<tplBase>"`，第 6 条一并转成基块（double→原版完整方块模型，纹理→基块纹理），一次替换两处生效；④ `items/<id>.json` 的 `"minecraft:block/<tplId>"` 由第 5 条转到 `more-things-vanilla:block/<id>`；⑤ 每文件写盘前 `[void](ConvertFrom-Json $text)` 校验。

```powershell
# ---- 写出 + 自校验 ----
$outRoot = Join-Path (Join-Path $ProjectRoot 'src\main\resources\assets') $ns
$counts = @{ blockstate = 0; model = 0; item = 0 }
function Write-JsonFile([string]$relPath, [string]$text) {
	[void](ConvertFrom-Json $text)   # 结构自校验
	$p = Join-Path $outRoot $relPath
	New-Item -ItemType Directory -Force -Path (Split-Path $p) | Out-Null
	[System.IO.File]::WriteAllText($p, $text + "`n", (New-Object System.Text.UTF8Encoding $false))
}
foreach ($e in $entries) {
	$id = $e.id; $base = $e.base
	if ($e.kind -eq 'slab') {
		Write-JsonFile "blockstates\$id.json" (Convert-Template $tplSlabBlockstate 'smooth_stone_slab' 'smooth_stone' $id $base)
		Write-JsonFile "models\block\$id.json" (Convert-Template $tplSlabModel 'smooth_stone_slab' 'smooth_stone' $id $base)
		Write-JsonFile "models\block\${id}_top.json" (Convert-Template $tplSlabTopModel 'smooth_stone_slab' 'smooth_stone' $id $base)
		Write-JsonFile "items\$id.json" (Convert-Template $tplSlabItem 'smooth_stone_slab' 'smooth_stone' $id $base)
		$counts.blockstate++; $counts.model += 2; $counts.item++
	} else {
		Write-JsonFile "blockstates\$id.json" (Convert-Template $tplStairsBlockstate 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\$id.json" (Convert-Template $tplStairsModel 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\${id}_inner.json" (Convert-Template $tplStairsInner 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "models\block\${id}_outer.json" (Convert-Template $tplStairsOuter 'stone_stairs' 'stone' $id $base)
		Write-JsonFile "items\$id.json" (Convert-Template $tplStairsItem 'stone_stairs' 'stone' $id $base)
		$counts.blockstate++; $counts.model += 3; $counts.item++
	}
}
if ($counts.blockstate -ne 79 -or $counts.model -ne 198 -or $counts.item -ne 79) {
	throw "count mismatch: $($counts | ConvertTo-Json -Compress)"
}

# ---- 语言条目（全量确定性重写，含计划 1 的标签页名称）----
$tabZh = '更多内容-原版'; $tabEn = 'More Things Vanilla'
foreach ($lang in @('zh_cn', 'en_us')) {
	$idx = if ($lang -eq 'zh_cn') { 0 } else { 1 }
	$sb = [System.Text.StringBuilder]::new()
	[void]$sb.Append("{`n`t`"itemGroup.more_things_vanilla`": `"" + $(if ($lang -eq 'zh_cn') { $tabZh } else { $tabEn }) + "`"")
	foreach ($e in $entries) {
		$name = if ($idx -eq 0) { $e.zh } else { $e.en }
		[void]$sb.Append(",`n`t`"block.${ns}.$($e.id)`": `"$name`"")
	}
	[void]$sb.Append("`n}`n")
	$text = $sb.ToString()
	[void](ConvertFrom-Json $text)
	$p = Join-Path $outRoot "lang\$lang.json"
	[System.IO.File]::WriteAllText($p, $text, (New-Object System.Text.UTF8Encoding $false))
	$parsed = ConvertFrom-Json $text
	$blockKeys = $parsed.PSObject.Properties.Name | Where-Object { $_ -like "block.${ns}.*" }
	if ($blockKeys.Count -ne 79) { throw "$lang block entries $($blockKeys.Count) != 79" }
}
Write-Output "OK: blockstates=$($counts.blockstate) models=$($counts.model) items=$($counts.item) lang=79x2"
```

> 上面两段 `powershell` 代码块拼起来是**同一个脚本文件**的连续内容（`Convert-Template` 只定义一次）。写盘前逐文件 `ConvertFrom-Json` 校验；`items/<id>.json` 的 `"minecraft:block/<tplId>"` 由第 5 条规则转到 `more-things-vanilla:block/<id>`，模型引用正确。
> **实现时已修订（Task 2 执行记录）**：① 26.2 实际模板的 slab blockstate double 变体为 `"minecraft:block/smooth_stone_slab_double"`、模型另有 `"side": "..._slab_side"` 纹理 → 增加 `_double`/`_side` 两条字面替换规则（否则 39 半砖 double 形态与侧面纹理错误）；② 脚本文件本身存为 UTF-8 **带 BOM**（PS 5.1 对无 BOM 文件按 ANSI 解析，中文字面量会乱码），产出 JSON/lang 仍为无 BOM UTF-8。

- [x] **Step 2: 运行脚本并核对输出**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gen-block-assets.ps1
```

Expected: 末行 `OK: blockstates=79 models=198 items=79 lang=79x2`，无异常。

抽查（人工确认转换正确）：
```powershell
Get-Content src\main\resources\assets\more-things-vanilla\blockstates\white_wool_slab.json
Get-Content src\main\resources\assets\more-things-vanilla\models\block\iron_block_stairs.json
Get-Content src\main\resources\assets\more-things-vanilla\items\smooth_stone_stairs.json
Select-String -Path src\main\resources\assets\more-things-vanilla\lang\zh_cn.json -Pattern 'black_wool_slab|smooth_stone_stairs'
```

Expected 要点：
- `white_wool_slab` blockstate：type=bottom/top → `more-things-vanilla:block/white_wool_slab{,_top}`，type=double → `minecraft:block/white_wool`
- `iron_block_stairs` model：parent `minecraft:block/stairs`，纹理 `minecraft:block/iron_block`
- `smooth_stone_stairs` items：model 指向 `more-things-vanilla:block/smooth_stone_stairs`
- zh：`"block.more-things-vanilla.black_wool_slab": "黑色羊毛半砖"`、`"block.more-things-vanilla.smooth_stone_stairs": "平滑石头楼梯"`
- 两个 lang 文件均含 `itemGroup` 条目且 JSON 合法

- [x] **Step 3: 构建 + 提交**

```powershell
.\gradlew.bat build
git add -A
git commit -m "feat: generate blockstates, models, item defs and lang entries for 79 blocks"
```

Expected: BUILD SUCCESSFUL（processResources 打包新资源）。

---

### Task 3: 战利品表 + 标签（红→绿）

**Files:**
- Test: `src/main/java/com/tt23xrstudio/test/MtvGameTests.java`（新增 3 个测试）
- Create: `scripts/gen-loot-tags.ps1`
- Create（脚本产出）: `data/more-things-vanilla/loot_table/blocks/*.json`（79）、`data/minecraft/tags/block/`（wool_slabs、wool_stairs、concrete_slabs、concrete_stairs、mineable/pickaxe、命中的 needs_*_tool）

- [x] **Step 1: 写失败测试（掉落 / 可燃 / 标签）**

`MtvGameTests` 新增（import：`net.minecraft.tags.TagKey`、`net.fabricmc.fabric.api.registry.FlammableBlockRegistry`、`net.minecraft.world.item.crafting` 不需要、`net.minecraft.world.level.block.Block`、`net.minecraft.world.item.Item`、`net.minecraft.core.BlockPos`）：

```java
	@GameTest
	public void blocksDropThemselves(GameTestHelper helper) {
		Identifier slabId = MoreThingsVanilla.id("iron_block_slab");
		Identifier stairsId = MoreThingsVanilla.id("smooth_stone_stairs");
		Item slabItem = BuiltInRegistries.ITEM.getValue(slabId);
		Item stairsItem = BuiltInRegistries.ITEM.getValue(stairsId);
		BlockPos slabPos = new BlockPos(1, 1, 1);
		BlockPos stairsPos = new BlockPos(3, 1, 1);
		helper.setBlock(slabPos, BuiltInRegistries.BLOCK.getValue(slabId));
		helper.setBlock(stairsPos, BuiltInRegistries.BLOCK.getValue(stairsId));
		helper.destroyBlock(slabPos);
		helper.destroyBlock(stairsPos);
		helper.succeedWhen(() -> {
			helper.assertItemEntityPresent(slabItem, slabPos, 1.0);
			helper.assertItemEntityPresent(stairsItem, stairsPos, 1.0);
		});
	}

	@GameTest
	public void woolBlocksAreFlammable(GameTestHelper helper) {
		FlammableBlockRegistry registry = FlammableBlockRegistry.getDefaultInstance();
		FlammableBlockRegistry.Entry slab = registry.get(BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("white_wool_slab")));
		FlammableBlockRegistry.Entry stairs = registry.get(BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("brown_wool_stairs")));
		helper.assertTrue(slab != null && slab.getIgniteOdds() == 30 && slab.getBurnOdds() == 60,
				"white_wool_slab flammability != vanilla wool (30, 60)");
		helper.assertTrue(stairs != null && stairs.getIgniteOdds() == 30 && stairs.getBurnOdds() == 60,
				"brown_wool_stairs flammability != vanilla wool (30, 60)");
		helper.succeed();
	}

	@GameTest
	public void blocksInTags(GameTestHelper helper) {
		TagKey<Block> woolSlabs = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "wool_slabs"));
		TagKey<Block> concreteSlabs = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "concrete_slabs"));
		TagKey<Block> concreteStairs = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "concrete_stairs"));
		TagKey<Block> pickaxe = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "mineable/pickaxe"));
		BlockPos woolPos = new BlockPos(1, 1, 1);
		BlockPos concreteSlabPos = new BlockPos(2, 1, 1);
		BlockPos concreteStairsPos = new BlockPos(4, 1, 1);
		BlockPos ironPos = new BlockPos(3, 1, 1);
		helper.setBlock(woolPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("white_wool_slab")));
		helper.setBlock(concreteSlabPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("white_concrete_slab")));
		helper.setBlock(concreteStairsPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("white_concrete_stairs")));
		helper.setBlock(ironPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("iron_block_slab")));
		helper.assertBlockTag(woolSlabs, woolPos);
		helper.assertBlockTag(concreteSlabs, concreteSlabPos);
		helper.assertBlockTag(concreteStairs, concreteStairsPos);
		helper.assertBlockTag(pickaxe, ironPos);
		helper.succeed();
	}
```

> `MoreThingsVanilla.id(...)` 返回 `Identifier`（计划 1 已有该工具方法）✓。若 `getValue` 对缺失 id 抛异常，`allBlocksRegistered` 已先行守护。

- [x] **Step 2: 跑 gametest 确认红**

同 Task 1 Step 2 命令。Expected: 报告含 2 条新 `<failure>`：
- `blocksDropThemselves` → 掉落物缺失（无战利品表）
- `blocksInTags` → 标签断言失败

> 注：`woolBlocksAreFlammable` 此时**已是绿的**——可燃登记在 Task 1 的 `finish()` 里就写好了，本任务只为它补上回归守护（不作为红的判定条件）。

- [x] **Step 3: 写 gen-loot-tags.ps1**

`scripts/gen-loot-tags.ps1`（完整代码，全部用字面 `.Replace`、逐文件 `ConvertFrom-Json` 校验）：

```powershell
param(
	[string]$MergedJar = "$env:USERPROFILE\.gradle\caches\fabric-loom\26.2\minecraft-merged.jar",
	[string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ns = 'more-things-vanilla'
$root = Join-Path $ProjectRoot 'src\main\resources'

# id 表与 gen-block-assets.ps1 共享：直接枚举已生成的 blockstate 文件名
$ids = @(Get-ChildItem (Join-Path $root "assets\$ns\blockstates") -Filter *.json | ForEach-Object BaseName)
if ($ids.Count -ne 79) { throw "blockstate ids $($ids.Count) != 79" }
$slabs  = @($ids | Where-Object { $_ -like '*_slab' })
$stairs = @($ids | Where-Object { $_ -like '*_stairs' })
if ($slabs.Count -ne 39 -or $stairs.Count -ne 40) { throw "slab=$($slabs.Count) stair=$($stairs.Count) != 39/40" }

$zip = [System.IO.Compression.ZipFile]::OpenRead($MergedJar)
function Read-JarEntry([string]$name) {
	$e = $zip.Entries | Where-Object FullName -eq $name
	if (-not $e) { throw "template missing in jar: $name" }
	$sr = New-Object System.IO.StreamReader($e.Open())
	try { return $sr.ReadToEnd() } finally { $sr.Close() }
}
function Write-Utf8Json([string]$path, [string]$text) {
	[void](ConvertFrom-Json $text)   # 结构自校验
	New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
	[System.IO.File]::WriteAllText($path, $text + "`n", (New-Object System.Text.UTF8Encoding $false))
}

# ---- 战利品表：只能用 26.2 模板（26.4 是 modifier/state 新格式，26.2 不识别）----
# 26.2 slab 模板内引用：'"minecraft:smooth_stone_slab"'（block 与 name 两处）+ '"minecraft:blocks/smooth_stone_slab"'（random_sequence）
# 26.2 stairs 模板内引用：'"minecraft:stone_stairs"' + '"minecraft:blocks/stone_stairs"'
$slabTpl   = Read-JarEntry 'data/minecraft/loot_table/blocks/smooth_stone_slab.json'
$stairsTpl = Read-JarEntry 'data/minecraft/loot_table/blocks/stone_stairs.json'
$lootDir = Join-Path $root "data\$ns\loot_table\blocks"
foreach ($id in $slabs) {
	$t = $slabTpl.Replace('"minecraft:blocks/smooth_stone_slab"', '"' + $ns + ':blocks/' + $id + '"')
	$t = $t.Replace('"minecraft:smooth_stone_slab"', '"' + $ns + ':' + $id + '"')
	Write-Utf8Json (Join-Path $lootDir "$id.json") $t
}
foreach ($id in $stairs) {
	$t = $stairsTpl.Replace('"minecraft:blocks/stone_stairs"', '"' + $ns + ':blocks/' + $id + '"')
	$t = $t.Replace('"minecraft:stone_stairs"', '"' + $ns + ':' + $id + '"')
	Write-Utf8Json (Join-Path $lootDir "$id.json") $t
}
if ((Get-ChildItem $lootDir -Filter *.json).Count -ne 79) { throw 'loot count != 79' }

# ---- 标签（$zip 保持打开，下面要读 jar 内原版标签）----
function Write-Tag([string]$path, [string[]]$values) {
	$body = ($values | ForEach-Object { "`t`t`"" + $ns + ':' + $_ + '"' }) -join ",`n"
	$text = "{`n`t`"replace`": false,`n`t`"values`": [`n" + $body + "`n`t]`n}"
	Write-Utf8Json $path $text
}
function Get-BaseId([string]$id) {
	if ($id.EndsWith('_slab')) { return $id.Substring(0, $id.Length - 5) }
	if ($id.EndsWith('_stairs')) { return $id.Substring(0, $id.Length - 7) }
	return $id
}
$tagDir = Join-Path $root 'data\minecraft\tags\block'
New-Item -ItemType Directory -Force -Path $tagDir | Out-Null

# 1) 族标签（用户拍板 A：忠于 26.4——26.4 实测有 wool_slabs/wool_stairs/concrete_slabs/concrete_stairs 四个家族标签，
#    成员只含对应 *_slab/*_stairs，不进 wool/concrete 原标签）
$familyTags = [ordered]@{
	'wool_slabs'     = @($ids | Where-Object { $_ -like '*_wool_slab' })
	'wool_stairs'    = @($ids | Where-Object { $_ -like '*_wool_stairs' })
	'concrete_slabs' = @($ids | Where-Object { $_ -like '*_concrete_slab' })
	'concrete_stairs'= @($ids | Where-Object { $_ -like '*_concrete_stairs' })
}
foreach ($name in $familyTags.Keys) {
	$members = $familyTags[$name]
	if ($members.Count -ne 16) { throw "family tag $name has $($members.Count) != 16" }
	Write-Tag (Join-Path $tagDir "$name.json") $members
}

# 2) 挖掘/工具标签继承：枚举 jar 内 mineable/*.json 与 needs_*_tool.json，
#    若基块 id（ours 去掉 _slab/_stairs 后缀）在原版标签值列表中 → 我方同名标签文件加入我方 id。
#    我方文件只含本 mod id（replace=false 与原版文件自动并集）。
$vanillaTagFiles = @($zip.Entries | Where-Object {
	$_.FullName -like 'data/minecraft/tags/block/mineable/*.json' -or
	$_.FullName -like 'data/minecraft/tags/block/needs_*_tool.json'
})
foreach ($vf in $vanillaTagFiles) {
	$sr = New-Object System.IO.StreamReader($vf.Open())
	try { $vanilla = ConvertFrom-Json $sr.ReadToEnd() } finally { $sr.Close() }
	$baseSet = @{}
	foreach ($v in $vanilla.values) { if ($v -is [string]) { $baseSet[$v] = $true } }
	$rel = $vf.FullName.Replace('data/minecraft/tags/block/', '')   # mineable/pickaxe.json 或 needs_stone_tool.json
	$ours = @($ids | Where-Object { $baseSet.ContainsKey('minecraft:' + (Get-BaseId $_)) })
	if ($ours.Count -eq 0) { continue }
	Write-Tag (Join-Path $tagDir $rel) $ours
	Write-Output ("tag {0}: +{1}" -f $rel, $ours.Count)
}
$zip.Dispose()

# 3) 打印每个标签文件条数（人工核对）
foreach ($f in Get-ChildItem $tagDir -Recurse -Filter *.json) {
	$n = (ConvertFrom-Json (Get-Content $f.FullName -Raw)).values.Count
	Write-Output ("tag {0}: {1}" -f $f.Name, $n)
}
Write-Output 'OK: loot=79 tags written'
```

> **预期标签产出**：`wool_slabs/wool_stairs/concrete_slabs/concrete_stairs` 各 16；`mineable/pickaxe.json` 含矿物 14 + 混凝土 32 + `smooth_stone_stairs`（若 `smooth_stone` 在原版 pickaxe 标签，26.2 实测在）≈47；`needs_*_tool.json` 按基块继承（iron/gold → needs_stone_tool，diamond/emerald/netherite → needs_iron_tool 等，以 jar 实际为准）；羊毛系不进任何 mineable（26.4 实测白羊毛半砖只在 wool_slabs）。

- [x] **Step 4: 运行脚本**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gen-loot-tags.ps1
```

Expected: 打印 loot=79、各标签文件条数；抽查：
```powershell
Get-Content src\main\resources\data\minecraft\tags\block\mineable\pickaxe.json
Get-Content src\main\resources\data\more-things-vanilla\loot_table\blocks\white_wool_slab.json
```
Expected: pickaxe 含 `more-things-vanilla:iron_block_slab`（无羊毛条目）；loot 含 `type=double → count 2` 结构（26.2 旧格式 `functions/block` 键）。

- [x] **Step 5: 跑 gametest 确认绿**

同 Task 1 Step 2 命令。Expected: 无 `<failure>`（此时 5 个测试全绿：`creativeTabRegistered`、`allBlocksRegistered`、`blocksDropThemselves`、`woolBlocksAreFlammable`、`blocksInTags`）。

- [x] **Step 6: 构建 + 提交**

```powershell
.\gradlew.bat build
git add -A
git commit -m "feat: add loot tables and block tags for slabs and stairs"
```

---

### Task 4: 配方（红→绿）

**Files:**
- Test: `src/main/java/com/tt23xrstudio/test/MtvGameTests.java`（新增 1 个测试）
- Create: `scripts/gen-recipes.ps1`
- Create（脚本产出）: `data/more-things-vanilla/recipe/*.json`

- [x] **Step 1: 写失败测试（配方加载）**

```java
	@GameTest
	public void recipesLoaded(GameTestHelper helper) {
		// 每个方块至少一条合成配方（手写或 26.4 抽取，配方 id = 方块 id）
		List<String> recipeIds = new ArrayList<>(Arrays.asList(ALL_NEW_BLOCK_IDS));
		// 额外抽取/手写的代表性配方（id = 文件名）
		recipeIds.add("dye_white_wool_slab");
		recipeIds.add("white_concrete_slab_from_white_concrete_stonecutting");
		recipeIds.add("smooth_stone_stairs_from_smooth_stone_stonecutting");
		for (String path : recipeIds) {
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, MoreThingsVanilla.id(path));
			helper.assertTrue(
					helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
					"recipe not loaded: " + MoreThingsVanilla.MOD_ID + ":" + path);
		}
		helper.succeed();
	}
```

新增 import：`java.util.List`、`java.util.ArrayList`、`java.util.Arrays`、`net.minecraft.world.item.crafting.Recipe`、`net.minecraft.resources.ResourceKey`、`net.minecraft.core.registries.Registries`（若尚无）。

- [x] **Step 2: 跑 gametest 确认红**

同 Task 1 Step 2 命令。Expected: `<failure ...> recipe not loaded: more-things-vanilla:iron_block_slab`。

- [x] **Step 3: 写 gen-recipes.ps1**

`scripts/gen-recipes.ps1`，三个职责（全部字面 `.Replace` + `ConvertFrom-Json` 校验）：

```powershell
param(
	[string]$RefJar = "D:\minecraft\.minecraft\versions\26.4-snapshot-6-Fabric 0.20.0\26.4-snapshot-6-Fabric 0.20.0.jar",
	[string]$ProjectRoot = (Split-Path $PSScriptRoot -Parent)
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ns = 'more-things-vanilla'
$root = Join-Path $ProjectRoot 'src\main\resources'

# id 表与前两个脚本共享：枚举已生成的 blockstate 文件名（79 条）
$ids = @(Get-ChildItem (Join-Path $root "assets\$ns\blockstates") -Filter *.json | ForEach-Object BaseName)
if ($ids.Count -ne 79) { throw "blockstate ids $($ids.Count) != 79" }
$idSet = @{}
foreach ($i in $ids) { $idSet[$i] = $true }

$recipeDir = Join-Path $root "data\$ns\recipe"
New-Item -ItemType Directory -Force -Path $recipeDir | Out-Null

function Write-Utf8Json([string]$path, [string]$text) {
	[void](ConvertFrom-Json $text)   # 结构自校验
	[System.IO.File]::WriteAllText($path, $text + "`n", (New-Object System.Text.UTF8Encoding $false))
}

# ---- 1) 从 26.4 抽取（羊毛/混凝土系的合成+染色+切石机；矿物系/平滑石楼梯 26.4 没有，天然不撞）----
$zip = [System.IO.Compression.ZipFile]::OpenRead($RefJar)
$extracted = 0
foreach ($e in $zip.Entries) {
	if ($e.FullName -notlike 'data/minecraft/recipe/*.json') { continue }
	if ($e.FullName.Split('/').Count -ne 4) { continue }   # 只取直接子文件
	$sr = New-Object System.IO.StreamReader($e.Open())
	try { $t = $sr.ReadToEnd() } finally { $sr.Close() }
	try { $j = ConvertFrom-Json $t } catch { continue }
	$resultId = $null
	if ($j.result -is [string]) { $resultId = $j.result }
	elseif ($j.result -and $j.result.id) { $resultId = $j.result.id }
	if (-not $resultId -or $resultId -notlike 'minecraft:*') { continue }
	$pathPart = ($resultId -split ':', 2)[1]
	if (-not $idSet.ContainsKey($pathPart)) { continue }   # result id 不在 79 表内 → 不是我们的方块
	# 命中：79 个 id 逐个带引号精确替换（基块如 white_wool 不在表内，天然不动）
	foreach ($id in $ids) {
		$t = $t.Replace('"minecraft:' + $id + '"', '"' + $ns + ':' + $id + '"')
	}
	[void](ConvertFrom-Json $t)
	Write-Utf8Json (Join-Path $recipeDir (Split-Path $e.FullName -Leaf)) $t
	$extracted++
}
$zip.Dispose()

# ---- 2) 手写矿物系 + 平滑石楼梯（26.4 没有；格式 = 26.2 实测模板）----
$slabTpl = @'
{
	"type": "minecraft:crafting_shaped",
	"category": "building",
	"key": {
		"#": "minecraft:@BASE@"
	},
	"pattern": [
		"###"
	],
	"result": {
		"count": 6,
		"id": "@NS@:@BASE@_slab"
	}
}
'@
$stairsTpl = @'
{
	"type": "minecraft:crafting_shaped",
	"category": "building",
	"key": {
		"#": "minecraft:@BASE@"
	},
	"pattern": [
		"#  ",
		"## ",
		"###"
	],
	"result": {
		"count": 4,
		"id": "@NS@:@BASE@_stairs"
	}
}
'@
$minerals = @('iron_block', 'gold_block', 'redstone_block', 'emerald_block', 'lapis_block', 'diamond_block', 'netherite_block')
foreach ($base in $minerals) {
	Write-Utf8Json (Join-Path $recipeDir "$base`_slab.json")   ($slabTpl.Replace('@BASE@', $base).Replace('@NS@', $ns))
	Write-Utf8Json (Join-Path $recipeDir "$base`_stairs.json") ($stairsTpl.Replace('@BASE@', $base).Replace('@NS@', $ns))
}
# 平滑石头楼梯：合成（同原版 stone_stairs）+ 切石机 1:1（设计.md）
$smoothTpl = $stairsTpl.Replace('@BASE@', 'smooth_stone').Replace('@NS@', $ns)
Write-Utf8Json (Join-Path $recipeDir 'smooth_stone_stairs.json') $smoothTpl
$smoothSc = @'
{
	"type": "minecraft:stonecutting",
	"ingredient": "minecraft:smooth_stone",
	"result": {
		"count": 1,
		"id": "@NS@:smooth_stone_stairs"
	}
}
'@
Write-Utf8Json (Join-Path $recipeDir 'smooth_stone_stairs_from_smooth_stone_stonecutting.json') ($smoothSc.Replace('@NS@', $ns))

# ---- 3) 自校验：79 个方块每个都存在 result=我方 id 的配方（可合成性契约）----
$covered = @{}
foreach ($f in Get-ChildItem $recipeDir -Filter *.json) {
	$j = ConvertFrom-Json (Get-Content $f.FullName -Raw)
	$rid = $null
	if ($j.result -is [string]) { $rid = $j.result }
	elseif ($j.result -and $j.result.id) { $rid = $j.result.id }
	if ($rid -and $rid -like "${ns}:*") { $covered[($rid -split ':', 2)[1]] = $true }
}
$missing = @($ids | Where-Object { -not $covered.ContainsKey($_) })
if ($missing.Count -gt 0) { throw "no recipe producing: $($missing -join ', ')" }
$total = (Get-ChildItem $recipeDir -Filter *.json).Count
Write-Output ("OK: manual=16 extracted={0} total={1}" -f $extracted, $total)
```

> 26.4 抽取预期（仅供核对，勿硬编码为失败条件）：羊毛系 64（半砖 16 合成 + 16 染色，楼梯同）+ 混凝土系 64（16 合成 + 16 切石机 ×半砖/楼梯）；若 26.4 另有羊毛切石机配方也会被自动抽到（result id 命中即抽）。
> `dye_*` 配方 ingredients 含 15 个他色方块 id（嵌套数组），同样被 79-id 替换覆盖 ✓。
> 混凝土切石机 count=2——抽取自动保持，勿手改。

- [x] **Step 4: 运行脚本**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\gen-recipes.ps1
```

Expected: 自校验全过。抽查：
```powershell
Get-Content src\main\resources\data\more-things-vanilla\recipe\dye_white_wool_slab.json
Get-Content src\main\resources\data\more-things-vanilla\recipe\iron_block_slab.json
```
Expected: dye 内 15 个数组元素全部 `more-things-vanilla:*_wool_slab`；result `more-things-vanilla:white_wool_slab`；矿物合成 pattern `###` count 6。

- [x] **Step 5: 跑 gametest 确认绿**

同 Task 1 Step 2 命令。Expected: 无 `<failure>`（6 个测试全绿：creativeTab、allBlocksRegistered、blocksDropThemselves、woolBlocksAreFlammable、blocksInTags、recipesLoaded）。

- [x] **Step 6: 构建 + 提交**

```powershell
.\gradlew.bat build
git add -A
git commit -m "feat: add crafting and stonecutting recipes for all 79 blocks"
```

---

### Task 5: 全量验证 + 客户端人眼验收 + 文档

**Files:**
- Modify: `README.md`（功能清单补一行：39 半砖 + 40 楼梯）
- Modify: `docs/superpowers/plans/2026-10-31-slab-stairs.md`（勾选全部任务、记录结论）

- [x] **Step 1: 改名回归 grep（沿用计划 1 纪律）**

```powershell
Select-String -Path (Get-ChildItem -Recurse -Include *.java,*.json,*.gradle,*.properties -File |
	Where-Object { $_.FullName -notmatch '\\build\\' } | ForEach-Object FullName) `
	-Pattern 'modid|com\.example|Example Mod' | Select-Object -First 10
```

Expected: 无输出（模板残留为零）。

- [x] **Step 2: 全量 gametest + 全量构建**

```powershell
Remove-Item build\gametest\report.xml -ErrorAction SilentlyContinue
.\gradlew.bat runGametest
# 判定：report.xml 存在、7 个 testcase（6 个本 mod 测试 + always_pass）、无 <failure> 元素
.\gradlew.bat build
```

Expected: BUILD SUCCESSFUL。

- [x] **Step 3: README 补功能行 + 计划文档收尾**

README 功能列表追加：`- 39 种半砖 + 40 种楼梯（矿物块/羊毛/混凝土/平滑石头，含配方、标签、战利品）`。计划文档勾选本任务与验收清单，记录 gametest 测试数与提交号。

- [x] **Step 4: 提交文档**

```powershell
git add -A
git commit -m "docs: record slab and stairs plan completion"
```

- [ ] **Step 5: 启动客户端人眼验收（控制器后台跑，用户关窗）**

`shell background=true` 运行 `.\gradlew.bat runClient`，请用户检查：
1. 创造标签页出现 79 个新物品，图标非紫黑缺失，中文名正确（悬停「黑色羊毛半砖」「平滑石头楼梯」）
2. 放置半砖/楼梯形状正常、可挖掉落（羊毛半砖点燃可烧）
3. 切石机放入白色混凝土 → 出现 2 个白色混凝土半砖等选项；工作台 3 铁块 → 6 铁块半砖
4. 创造标签页标题仍为「更多内容-原版」
5. 熔炉燃料槽放入羊毛系半砖/楼梯 → 可烧 1 个物品（300 tick，同木板）
6. 切石机放入铁块等 7 种矿物块 → 出现半砖（1→2）与楼梯（1→1）选项

---

## 执行记录（2026-11-16 收尾）

- Task 1：`684e2f3`（规格 ✅ / 质量 Yes；实现者修复计划 API 缺口：`ofFullCopy` 不复制 id，需 `setId` 后再构造）
- Task 2：`64cbc75`（规格 ✅ / 质量 Yes；脚本 3 处修订：UTF-8 BOM、`_double`/`_side` 替换规则）
- Task 3：`ce5ff37` + 审查修复 `bff40d4`（规格 ✅ / 质量 With fixes→已修：8 标签全断言 + wool∉pickaxe 负断言；计划 bug 修订：`GameTestHelper.destroyBlock` 26.2 为 dropBlocks=false，改用 `level.destroyBlock(..., true, ...)`）
- Task 4：`360faf3` + 审查修复 `d5838d6`（规格 ✅ / 质量 Yes；修复：两脚本 zip try/finally、gen-recipes 头部注释）
- Task 5：回归 grep 零命中（`bin/` 为 gitignore 的 IDE 残留，不计）；gametest **7 testcase / 0 failure**；`build` BUILD SUCCESSFUL

### 验收反馈修订（2026-11-16 人眼验收）

- 翻译键 bug `a888a6c`：26.2 `Item.Properties` 默认 `item.` 前缀，方块物品须显式 `useBlockDescriptionPrefix()`（原版 `Items` 同款），否则游戏内显示完整翻译键；新增测试 `itemNamesUseBlockTranslationKey` 锁死 79 物品
- 验收反馈两项 `3773d25`（gametest **9 testcase / 0 failure**）：
  1. **羊毛系熔炉燃料**（新功能，原版羊毛不是燃料）：`FuelValueEvents.BUILD` 注册本 mod 32 个羊毛半砖/楼梯，每个 300 tick（同木板，用户拍板）；测试 `woolBlocksAreFuels` 含矿物系负断言
  2. **矿物切石机 14 条**（推翻计划原「矿物不加切石机」，用户拍板）：7 矿物块 × 半砖 1→2 / 楼梯 1:1；`gen-recipes.ps1` 手写段 16→30，配方总数 144→158

---

## 验收清单（对照 设计.md 半砖/楼梯章节）

- [x] 39 半砖 + 40 楼梯全部注册（方块+物品），gametest `allBlocksRegistered` 绿
- [x] 占位铁块已移除，标签页内容 = 真实方块（`hasAnyItems`/`shouldDisplay` 继续守护）
- [x] 硬度/音效与基块一致（`ofFullCopy`）、掉落与基块同（战利品表，26.2 格式）且掉落测试绿
- [x] 羊毛系可燃 (30, 60) 同原版，测试绿
- [x] 标签：羊毛→wool_slabs/wool_stairs、混凝土→concrete_slabs/concrete_stairs+mineable/pickaxe、矿物→mineable/pickaxe+needs_*_tool 继承；不进 wool/concrete/beacon_base_blocks（用户拍板 A）
- [x] 配方：3→6 半砖 / 6→4 楼梯；羊毛/混凝土切石机按 26.4 实际（羊毛无、混凝土 1→2）；平滑石楼梯切石机 1:1；矿物切石机半砖 1→2、楼梯 1:1（验收拍板）；每方块可合成（`recipesLoaded` 绿）
- [x] 羊毛系 32 方块可当熔炉燃料 300 tick（验收拍板，`woolBlocksAreFuels` 绿）
- [x] 模型/方块状态/物品模型定义齐全（79/198/79），客户端无紫黑缺失
- [x] zh_cn/en_us 语言条目齐全（各 79 块条目 + 标签名）
- [x] 客户端人眼验收通过（Task 5 Step 5 六项 + 翻译键中文名，2026-11-16 用户确认「均通过」）
- [x] 全部改动已提交、工作区干净
