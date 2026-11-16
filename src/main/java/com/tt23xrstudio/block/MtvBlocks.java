package com.tt23xrstudio.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
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

	/** 羊毛系熔炉燃料燃烧时间（tick），同木板；原版羊毛不是燃料，此为本 mod 新增（用户拍板）。 */
	private static final int WOOL_FUEL_BURN_TIME = 300;

	/** 羊毛系物品清单，供熔炉燃料事件使用。 */
	private static final List<Item> woolFuelItems = new ArrayList<>();

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
		// 熔炉燃料（验收反馈拍板）：羊毛系 32 个方块每个 300 tick（同木板）。
		// 原版羊毛本身不是熔炉燃料，仅本 mod 的羊毛系半砖/楼梯可当燃料（矿物/混凝土系不注册）。
		FuelValueEvents.BUILD.register((builder, context) -> {
			for (Item item : woolFuelItems) {
				builder.add(item, WOOL_FUEL_BURN_TIME);
			}
		});
	}

	private static void registerSlab(Block base, boolean flammable) {
		String path = basePath(base) + "_slab";
		finish(path, properties(path, base), props -> new SlabBlock(props), flammable);
	}

	private static void registerStairs(Block base, boolean flammable) {
		String path = basePath(base) + "_stairs";
		finish(path, properties(path, base),
				props -> new ModStairBlock(base.defaultBlockState(), props), flammable);
	}

	/** 26.2：Block 构造要求 properties 已带 id（原版 Blocks.register 同序：setId → 构造 → 注册）。 */
	private static BlockBehaviour.Properties properties(String path, Block base) {
		return BlockBehaviour.Properties.ofFullCopy(base)
				.setId(ResourceKey.create(Registries.BLOCK, MoreThingsVanilla.id(path)));
	}

	private static void finish(String path, BlockBehaviour.Properties properties,
			Function<BlockBehaviour.Properties, Block> factory, boolean flammable) {
		Block block = factory.apply(properties);
		Registry.register(BuiltInRegistries.BLOCK, MoreThingsVanilla.id(path), block);
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, MoreThingsVanilla.id(path));
		BlockItem item = Registry.register(BuiltInRegistries.ITEM, MoreThingsVanilla.id(path),
				// 26.2：Item.Properties 默认 item. 前缀；方块物品须显式切到 block. 前缀
				// （原版 Items 注册同款），否则游戏内显示原始翻译键、lang 的 block.* 条目全部不生效。
				new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		MoreThingsVanilla.TAB_ITEMS.add(item);
		ALL.put(path, block);
		if (flammable) {
			FlammableBlockRegistry.getDefaultInstance().add(block, WOOL_IGNITE_ODDS, WOOL_BURN_ODDS);
			woolFuelItems.add(item);
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
