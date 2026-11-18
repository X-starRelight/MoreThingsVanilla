package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;
import com.tt23xrstudio.block.MtvBlocks;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
		int expectedCount = ALL_NEW_BLOCK_IDS.length;
		helper.assertValueEqual(expectedCount, MtvBlocks.ALL.size(),
				"MtvBlocks.ALL size != " + expectedCount);
		long namespaceCount = BuiltInRegistries.BLOCK.keySet().stream()
				.filter(key -> MoreThingsVanilla.MOD_ID.equals(key.getNamespace()))
				.count();
		helper.assertValueEqual(expectedCount, (int) namespaceCount,
				"registered block count in mod namespace != " + expectedCount);
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
		String slabActual = slab == null ? "null" : slab.getIgniteOdds() + "/" + slab.getBurnOdds();
		String stairsActual = stairs == null ? "null" : stairs.getIgniteOdds() + "/" + stairs.getBurnOdds();
		helper.assertTrue(slab != null && slab.getIgniteOdds() == 30 && slab.getBurnOdds() == 60,
				"white_wool_slab flammability " + slabActual + " != vanilla wool (30, 60)");
		helper.assertTrue(stairs != null && stairs.getIgniteOdds() == 30 && stairs.getBurnOdds() == 60,
				"brown_wool_stairs flammability " + stairsActual + " != vanilla wool (30, 60)");
		helper.succeed();
	}

	@GameTest
	public void blocksInTags(GameTestHelper helper) {
		TagKey<Block> woolSlabs = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "wool_slabs"));
		TagKey<Block> woolStairs = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "wool_stairs"));
		TagKey<Block> concreteSlabs = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "concrete_slabs"));
		TagKey<Block> concreteStairs = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "concrete_stairs"));
		TagKey<Block> pickaxe = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "mineable/pickaxe"));
		TagKey<Block> needsStoneTool = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "needs_stone_tool"));
		TagKey<Block> needsIronTool = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "needs_iron_tool"));
		TagKey<Block> needsDiamondTool = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("minecraft", "needs_diamond_tool"));
		BlockPos woolPos = new BlockPos(1, 1, 1);
		BlockPos concreteSlabPos = new BlockPos(2, 1, 1);
		BlockPos concreteStairsPos = new BlockPos(4, 1, 1);
		BlockPos ironPos = new BlockPos(3, 1, 1);
		BlockPos woolStairsPos = new BlockPos(5, 1, 1);
		BlockPos netheritePos = new BlockPos(6, 1, 1);
		BlockPos diamondPos = new BlockPos(7, 1, 1);
		helper.setBlock(woolPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("white_wool_slab")));
		helper.setBlock(concreteSlabPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("white_concrete_slab")));
		helper.setBlock(concreteStairsPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("white_concrete_stairs")));
		helper.setBlock(ironPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("iron_block_slab")));
		helper.setBlock(woolStairsPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("brown_wool_stairs")));
		helper.setBlock(netheritePos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("netherite_block_slab")));
		helper.setBlock(diamondPos, BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id("diamond_block_slab")));
		helper.assertBlockTag(woolSlabs, woolPos);
		helper.assertBlockTag(woolStairs, woolStairsPos);
		helper.assertBlockTag(concreteSlabs, concreteSlabPos);
		helper.assertBlockTag(concreteStairs, concreteStairsPos);
		helper.assertBlockTag(pickaxe, ironPos);
		helper.assertBlockTag(needsStoneTool, ironPos);
		helper.assertBlockTag(needsIronTool, diamondPos);
		helper.assertBlockTag(needsDiamondTool, netheritePos);
		// 负断言：羊毛族方块不进 mineable/pickaxe（羊毛用剪刀，不属于镐可挖掘）
		helper.assertFalse(helper.getBlockState(woolPos).is(pickaxe),
				"white_wool_slab unexpectedly in minecraft:mineable/pickaxe");
		helper.succeed();
	}

	/**
	 * 物品显示名必须用 block. 翻译键（与原版一致：Items 注册方块物品时调 useBlockDescriptionPrefix()）。
	 * 26.2 的 Item.Properties 默认 item. 前缀，漏调该方法会导致创造面板显示原始翻译键。
	 */
	@GameTest
	public void itemNamesUseBlockTranslationKey(GameTestHelper helper) {
		String prefix = "block." + MoreThingsVanilla.MOD_ID + ".";
		for (String path : ALL_NEW_BLOCK_IDS) {
			Item item = BuiltInRegistries.ITEM.getValue(MoreThingsVanilla.id(path));
			helper.assertValueEqual(prefix + path, item.getDescriptionId(),
					"wrong description id for item: " + path);
		}
		helper.succeed();
	}

	/**
	 * 羊毛系 32 个方块必须能当熔炉燃料，每个 300 tick（同木板，用户拍板）。
	 * 原版羊毛本身不是熔炉燃料，此为本 mod 新增功能；矿物/混凝土系不得成为燃料（负断言）。
	 */
	@GameTest
	public void woolBlocksAreFuels(GameTestHelper helper) {
		FuelValues fuels = helper.getLevel().getServer().fuelValues();
		int checked = 0;
		for (String path : ALL_NEW_BLOCK_IDS) {
			if (!path.endsWith("_wool_slab") && !path.endsWith("_wool_stairs")) continue;
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(MoreThingsVanilla.id(path)));
			helper.assertTrue(fuels.isFuel(stack), "wool block not a fuel: " + path);
			helper.assertValueEqual(300, fuels.burnDuration(stack), "wrong burn duration: " + path);
			checked++;
		}
		helper.assertValueEqual(32, checked, "wool fuel count");
		// 负断言：矿物/混凝土系不是燃料
		ItemStack iron = new ItemStack(BuiltInRegistries.ITEM.getValue(MoreThingsVanilla.id("iron_block_slab")));
		helper.assertFalse(fuels.isFuel(iron), "iron_block_slab unexpectedly a fuel");
		helper.succeed();
	}

	@GameTest
	public void recipesLoaded(GameTestHelper helper) {
		// 每个方块至少一条合成配方（手写或 26.4 抽取，配方 id = 方块 id）
		List<String> recipeIds = new ArrayList<>(Arrays.asList(ALL_NEW_BLOCK_IDS));
		// 额外抽取/手写的代表性配方（id = 文件名）
		recipeIds.add("dye_white_wool_slab");
		recipeIds.add("white_concrete_slab_from_white_concrete_stonecutting");
		recipeIds.add("smooth_stone_stairs_from_smooth_stone_stonecutting");
		// 矿物系切石机 14 条（验收反馈拍板：半砖 1→2、楼梯 1:1，推翻计划原「矿物不加切石机」）
		String[] mineralBases = {"iron_block", "gold_block", "redstone_block", "emerald_block",
				"lapis_block", "diamond_block", "netherite_block"};
		for (String base : mineralBases) {
			recipeIds.add(base + "_slab_from_" + base + "_stonecutting");
			recipeIds.add(base + "_stairs_from_" + base + "_stonecutting");
		}
		for (String path : recipeIds) {
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, MoreThingsVanilla.id(path));
			helper.assertTrue(
					helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
					"recipe not loaded: " + MoreThingsVanilla.MOD_ID + ":" + path);
		}
		helper.succeed();
	}

	// ==================== 计划 3：坐垫（复刻 26.4） ====================

	/** 16 色坐垫 id 路径（与 26.4 原版平行，仅命名空间不同）。 */
	private static final String[] CUSHION_COLORS = {
			"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
			"light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
	};

	/** 26.4 原版 4 个坐垫音效事件（sounds.json 键，提取到本 mod 命名空间重注册）。 */
	private static final String[] CUSHION_SOUNDS = {
			"entity.cushion.break", "entity.cushion.get_up", "entity.cushion.place", "entity.cushion.sit"
	};

	@GameTest
	public void cushionsRegistered(GameTestHelper helper) {
		TagKey<Item> cushionsTag = TagKey.create(Registries.ITEM,
				Identifier.fromNamespaceAndPath("minecraft", "cushions"));
		for (String color : CUSHION_COLORS) {
			Identifier id = MoreThingsVanilla.id(color + "_cushion");
			helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "cushion item not registered: " + id);
			Item item = BuiltInRegistries.ITEM.getValue(id);
			helper.assertValueEqual("item." + MoreThingsVanilla.MOD_ID + "." + color + "_cushion",
					item.getDescriptionId(), "wrong description id: " + color);
			helper.assertTrue(new ItemStack(item).is(cushionsTag),
					"cushion not in minecraft:cushions: " + color);
			helper.assertTrue(MoreThingsVanilla.TAB_ITEMS.contains(item),
					"cushion missing from creative tab: " + color);
		}
		Identifier entityId = MoreThingsVanilla.id("cushion");
		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(entityId),
				"cushion entity type not registered: " + entityId);
		for (String sound : CUSHION_SOUNDS) {
			helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(MoreThingsVanilla.id(sound)),
					"cushion sound not registered: " + sound);
		}
		helper.succeed();
	}

	/** 32 条配方：16 条合成（3 羊毛半砖）+ 16 条染料重染（26.4 原版抽取，命名空间替换）。 */
	@GameTest
	public void cushionRecipesLoaded(GameTestHelper helper) {
		for (String color : CUSHION_COLORS) {
			String[] paths = {color + "_cushion", "dye_" + color + "_cushion"};
			for (String path : paths) {
				ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, MoreThingsVanilla.id(path));
				helper.assertTrue(
						helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
						"recipe not loaded: " + MoreThingsVanilla.MOD_ID + ":" + path);
			}
		}
		helper.succeed();
	}

	/** 26.4 原版两个标签：物品 minecraft:cushions、方块 minecraft:cushion_uses_collision_shape。 */
	@GameTest
	public void cushionTags(GameTestHelper helper) {
		TagKey<Block> collisionTag = TagKey.create(Registries.BLOCK,
				Identifier.fromNamespaceAndPath("minecraft", "cushion_uses_collision_shape"));
		// 26.4 内容：[#minecraft:cauldrons, hopper, composter]（碰撞箱≠可视形状，放置需射线校正）
		helper.assertTrue(Blocks.HOPPER.defaultBlockState().is(collisionTag),
				"hopper not in minecraft:cushion_uses_collision_shape");
		helper.assertTrue(Blocks.COMPOSTER.defaultBlockState().is(collisionTag),
				"composter not in minecraft:cushion_uses_collision_shape");
		helper.assertFalse(Blocks.STONE.defaultBlockState().is(collisionTag),
				"stone unexpectedly in minecraft:cushion_uses_collision_shape");
		helper.succeed();
	}

	/** 16 色坐垫均为燃料，50 tick（26.4：cookingFuel(COOKING_TIME_WOOL_SLABS)，time_wool_slabs.json = 50）。 */
	@GameTest
	public void cushionsAreFuels(GameTestHelper helper) {
		FuelValues fuels = helper.getLevel().getServer().fuelValues();
		for (String color : CUSHION_COLORS) {
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(MoreThingsVanilla.id(color + "_cushion")));
			helper.assertTrue(fuels.isFuel(stack), "cushion not a fuel: " + color);
			helper.assertValueEqual(50, fuels.burnDuration(stack), "wrong burn duration: " + color);
		}
		helper.succeed();
	}

	/**
	 * 放置 → 实体生成（中键取物验证颜色随物品传递）→ 玩家攻击破坏 → 掉落对应颜色物品。
	 * 复刻 26.4 CushionItem.useOn + Cushion.dropItem 主链路。
	 */
	@GameTest
	public void cushionPlaceAndBreak(GameTestHelper helper) {
		BlockPos groundPos = new BlockPos(3, 1, 1);
		helper.setBlock(groundPos, Blocks.STONE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		Item whiteCushion = BuiltInRegistries.ITEM.getValue(MoreThingsVanilla.id("white_cushion"));
		ItemStack stack = new ItemStack(whiteCushion);
		BlockPos absGround = helper.absolutePos(groundPos);
		// 点击顶面中心（y = 方块顶面）
		Vec3 clickLocation = new Vec3(absGround.getX() + 0.5, absGround.getY() + 1.0, absGround.getZ() + 0.5);
		BlockHitResult hit = new BlockHitResult(clickLocation, Direction.UP, absGround, false);
		InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		helper.assertTrue(result.consumesAction(), "cushion placement did not consume: " + result);

		EntityType<?> cushionType = BuiltInRegistries.ENTITY_TYPE.getValue(MoreThingsVanilla.id("cushion"));
		AABB searchArea = new AABB(absGround.getX() - 1, absGround.getY(), absGround.getZ() - 1,
				absGround.getX() + 2, absGround.getY() + 3, absGround.getZ() + 2);
		List<Entity> placed = helper.getLevel().getEntitiesOfClass(Entity.class, searchArea).stream()
				.filter(entity -> entity.getType() == cushionType)
				.toList();
		helper.assertValueEqual(1, placed.size(), "placed cushion entity count");
		// 中键取物 = 对应颜色物品（验证颜色随物品传递）
		helper.assertValueEqual(whiteCushion, placed.get(0).getPickResult().getItem(), "wrong pick result color");
		// 玩家攻击破坏 → dropItem 掉落对应颜色物品
		placed.get(0).skipAttackInteraction(player);
		helper.succeedWhen(() -> helper.assertItemEntityPresent(whiteCushion, groundPos.above(), 2.0));
	}
}
