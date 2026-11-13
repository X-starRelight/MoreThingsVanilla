package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;
import com.tt23xrstudio.block.MtvBlocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class MtvGameTests {

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
	public void creativeTabRegistered(GameTestHelper helper) {
		helper.assertValueEqual("more-things-vanilla:more_things_vanilla",
				MoreThingsVanilla.CREATIVE_TAB_KEY.identifier().toString(), "creative tab id changed");
		helper.assertTrue(
				BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(MoreThingsVanilla.CREATIVE_TAB_KEY),
				"creative tab not registered: " + MoreThingsVanilla.CREATIVE_TAB_KEY
		);
		// gametest 无创造界面，内容需手动构建后才能断言可见性
		// （原版 shouldDisplay(): 空的 CATEGORY 标签页不渲染，参见 CreativeModeInventoryScreen）
		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getOptional(MoreThingsVanilla.CREATIVE_TAB_KEY)
				.orElseThrow();
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
				FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
		helper.assertTrue(tab.hasAnyItems(),
				"creative tab has no display items (空分类标签页在游戏内会被隐藏)");
		helper.assertTrue(tab.shouldDisplay(),
				"creative tab shouldDisplay()=false, 游戏内不可见");
		helper.succeed();
	}

	@GameTest
	public void allBlocksRegistered(GameTestHelper helper) {
		for (String path : ALL_NEW_BLOCK_IDS) {
			Identifier id = Identifier.fromNamespaceAndPath(MoreThingsVanilla.MOD_ID, path);
			helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "block not registered: " + id);
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "item not registered: " + id);
		}
		// 反向断言（计划 1 质量审查允许的补充）：防止注册了多余方块
		helper.assertValueEqual(79, MtvBlocks.ALL.size(), "MtvBlocks.ALL size != 79");
		long namespaceCount = BuiltInRegistries.BLOCK.keySet().stream()
				.filter(key -> MoreThingsVanilla.MOD_ID.equals(key.getNamespace()))
				.count();
		helper.assertValueEqual(79, (int) namespaceCount, "registered block count in mod namespace != 79");
		helper.succeed();
	}

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
		// 26.2 的 GameTestHelper.destroyBlock(pos) 实际调用 ServerLevel.destroyBlock(pos, false, null)，
		// dropBlocks=false 不产生任何掉落物，战利品表断言必然失败（计划原文的 bug）。
		// 这里改用 level.destroyBlock(..., true, null) 显式掉落，才能验证方块掉自己。
		helper.getLevel().destroyBlock(helper.absolutePos(slabPos), true, null);
		helper.getLevel().destroyBlock(helper.absolutePos(stairsPos), true, null);
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
}
