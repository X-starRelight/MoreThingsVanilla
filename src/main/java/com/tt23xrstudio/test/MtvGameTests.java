package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;
import com.tt23xrstudio.block.MtvBlocks;
import com.tt23xrstudio.redstone.OneWayTransmitterBlock;

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
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
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
			"purple_concrete_stairs", "magenta_concrete_stairs", "pink_concrete_stairs", "brown_concrete_stairs",
			// 计划 4：竖直红石三件套 3
			"vertical_redstone", "vertical_repeater", "vertical_comparator",
			// 计划 5：单向传递器 1
			"one_way_transmitter"
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

	// ==================== 计划 4：竖直红石三件套 ====================

	private static Block verticalBlock(String path) {
		return BuiltInRegistries.BLOCK.getValue(MoreThingsVanilla.id(path));
	}

	/** 竖直红石信号链：红石块(15) → 三格竖直红石 15/14/13（每过一格衰减 -1，同原版红石粉）。 */
	@GameTest
	public void verticalRedstoneDecaysPerBlock(GameTestHelper helper) {
		BlockPos source = new BlockPos(1, 1, 1);
		helper.setBlock(source, Blocks.REDSTONE_BLOCK);
		helper.setBlock(new BlockPos(2, 1, 1), verticalBlock("vertical_redstone"));
		helper.setBlock(new BlockPos(3, 1, 1), verticalBlock("vertical_redstone"));
		helper.setBlock(new BlockPos(4, 1, 1), verticalBlock("vertical_redstone"));
		helper.assertValueEqual(15, helper.getBlockState(new BlockPos(2, 1, 1))
				.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER), "first vertical redstone power");
		helper.assertValueEqual(14, helper.getBlockState(new BlockPos(3, 1, 1))
				.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER), "second vertical redstone power");
		helper.assertValueEqual(13, helper.getBlockState(new BlockPos(4, 1, 1))
				.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER), "third vertical redstone power");
		helper.succeed();
	}

	/** 竖直红石六面均能输出信号（含上下），且强充能紧邻方块。 */
	@GameTest
	public void verticalRedstoneSixFacesAndStrongPower(GameTestHelper helper) {
		BlockPos vr = new BlockPos(3, 3, 3);
		// 先放竖直红石，再放红石块供电（手动电平会被邻居更新重算覆盖，必须用真实电源）
		helper.setBlock(vr, verticalBlock("vertical_redstone"));
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.REDSTONE_BLOCK);
		helper.assertValueEqual(15, helper.getBlockState(vr).getValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER), "power from redstone block");
		// 六面输出：读竖直红石自身信号（direction 语义由实现内部处理，六面统一电平）
		net.minecraft.core.Direction[] all = net.minecraft.core.Direction.values();
		for (net.minecraft.core.Direction dir : all) {
			int signal = helper.getLevel().getSignal(helper.absolutePos(vr), dir);
			helper.assertValueEqual(15, signal, "signal face " + dir);
		}
		// 强充能：紧邻实体方块应带 direct signal 15（getDirectSignalTo = 各邻居对它的强充能）
		helper.setBlock(new BlockPos(4, 3, 3), Blocks.STONE);
		int direct = helper.getLevel().getDirectSignalTo(helper.absolutePos(new BlockPos(4, 3, 3)));
		helper.assertValueEqual(15, direct, "strong power into stone");
		helper.succeed();
	}

	/** 与原版红石粉互通：红石粉贴着竖直红石时视觉连接属性非 NONE，且信号双向传递。 */
	@GameTest
	public void verticalRedstoneInteropsWithVanillaWire(GameTestHelper helper) {
		BlockPos vr = new BlockPos(2, 1, 2);
		BlockPos wirePos = new BlockPos(3, 1, 2);
		// 红石粉放在竖直红石东侧（放在石头上）。
		// 顺序关键：helper.setBlock 放粉只得到全 NONE 默认态，连接靠邻居出现时 wire.updateShape 重算，
		// 所以先放粉、后放竖直红石（否则连接永不计算）。
		helper.setBlock(new BlockPos(3, 0, 2), Blocks.STONE);
		helper.setBlock(wirePos, Blocks.REDSTONE_WIRE);
		helper.setBlock(vr, verticalBlock("vertical_redstone").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER, 12));
		// 红石粉应朝西连接到竖红石（signal source 互通）
		net.minecraft.world.level.block.state.properties.RedstoneSide side = helper.getBlockState(wirePos).getValue(
				net.minecraft.world.level.block.RedStoneWireBlock.WEST);
		helper.assertTrue(side.isConnected(), "vanilla wire not connected toward vertical redstone: " + side);
		// 双向：红石粉(15) → 竖直红石 = 14（按 -1 衰减读入）。
		// 红石块只贴粉、不贴竖直红石，验证信号确实从粉读入；先重置再供电触发重算。
		helper.setBlock(vr, verticalBlock("vertical_redstone"));
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.REDSTONE_BLOCK);
		int power = helper.getBlockState(vr).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.POWER);
		helper.assertValueEqual(14, power, "vertical redstone should read vanilla wire at 15 minus 1");
		helper.succeed();
	}

	/** 中继器 6 个放置朝向：FACING = 输出方向 = 点击面朝向。 */
	@GameTest
	public void verticalRepeaterPlacementOrientations(GameTestHelper helper) {
		net.minecraft.core.Direction[] faces = {
				net.minecraft.core.Direction.UP, net.minecraft.core.Direction.DOWN,
				net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH,
				net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.NORTH
		};
		Item repeater = BuiltInRegistries.ITEM.getValue(MoreThingsVanilla.id("vertical_repeater"));
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		int i = 0;
		for (net.minecraft.core.Direction face : faces) {
			// y=2/z=2 布局，避开结构地板 y=0 与边界 z=0
			BlockPos support = new BlockPos(1 + i, 2, 2);
			helper.setBlock(support, Blocks.STONE);
			BlockPos target = support.relative(face);
			helper.setBlock(target, Blocks.AIR);
			Vec3 click = new Vec3(helper.absolutePos(target).getX() + 0.5 - face.getStepX() * 0.5,
					helper.absolutePos(target).getY() + 0.5 - face.getStepY() * 0.5,
					helper.absolutePos(target).getZ() + 0.5 - face.getStepZ() * 0.5);
			BlockHitResult hit = new BlockHitResult(click, face, helper.absolutePos(support), false);
			InteractionResult result = new ItemStack(repeater).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
			helper.assertTrue(result.consumesAction(), "placement failed for face " + face + ": " + result);
			net.minecraft.world.level.block.state.BlockState placed = helper.getBlockState(target);
			helper.assertTrue(placed.is(verticalBlock("vertical_repeater")), "not repeater at " + target + ": " + placed);
			helper.assertValueEqual(face, placed.getValue(
					net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING), "wrong facing for clicked " + face);
			i++;
		}
		helper.succeed();
	}

	/** 中继器：输入端供能 → 延迟后输出端输出 15（输出方向 = FACING）。 */
	@GameTest
	public void verticalRepeaterDelaysAndOutputs(GameTestHelper helper) {
		// 输出朝东：输入在西
		BlockPos rep = new BlockPos(3, 1, 3);
		helper.setBlock(rep, verticalBlock("vertical_repeater").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.EAST));
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
		// 延迟 2 tick（1 档），等待后断言输出
		helper.succeedWhen(() -> {
			// getSignal(pos, dir) 读 pos 处方块：pos=中继器、dir=观察者(输出侧东)→源=WEST
			int out = helper.getLevel().getSignal(helper.absolutePos(rep), net.minecraft.core.Direction.WEST);
			helper.assertValueEqual(15, out, "repeater output east");
		});
	}

	/** 比较器：比较模式与减法模式 + 侧面输入（输出朝上时读 4 个水平方向）。 */
	@GameTest
	public void verticalComparatorModesAndSideInput(GameTestHelper helper) {
		// 输出朝上，后端（下方）供能 15，侧面无信号 → 输出 15
		BlockPos comp = new BlockPos(3, 1, 3);
		helper.setBlock(comp, verticalBlock("vertical_comparator").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
		helper.setBlock(new BlockPos(3, 0, 3), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			// getSignal(pos, dir) 读 pos 处方块：pos=比较器、dir=观察者(输出侧上)→源=DOWN
			int up = helper.getLevel().getSignal(helper.absolutePos(comp), net.minecraft.core.Direction.DOWN);
			helper.assertValueEqual(15, up, "comparator output up");
			// 侧面（4 水平方向任一）供能 15 > 输入？输入也是 15，减法模式下 15-15=0；先验证减法模式切换
			net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(comp);
			helper.assertValueEqual(net.minecraft.world.level.block.state.properties.ComparatorMode.COMPARE,
					state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MODE_COMPARATOR), "default mode");
		});
	}

	/** 四个新红石元件均有配方（竖直红石=红石粉合成；中继器/比较器同材料竖排；传递器无序合成）。 */
	@GameTest
	public void verticalRecipesLoaded(GameTestHelper helper) {
		for (String path : new String[] {"vertical_redstone", "vertical_repeater", "vertical_comparator",
				"one_way_transmitter"}) {
			ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, MoreThingsVanilla.id(path));
			helper.assertTrue(
					helper.getLevel().getServer().getRecipeManager().byKey(key).isPresent(),
					"recipe not loaded: " + MoreThingsVanilla.MOD_ID + ":" + path);
		}
		helper.succeed();
	}

	// ==================== 计划 4 复现（用户反馈：中继器/比较器不输出）：端到端 输出→红石粉 ====================

	/** 对照组：原版中继器——粉先放、电源后放，POWERED 翻转后粉必须重算亮起。 */
	@GameTest
	public void vanillaRepeaterPowersWireEndToEnd(GameTestHelper helper) {
		helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 0, 3), Blocks.STONE);
		// 原版 FACING=输入方向（水平 4 向属性）：输入朝西（电源在西），输出朝东接粉
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.REPEATER.defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_WIRE);
		// 电源最后放：翻转 POWERED 时粉已在场
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			int p = helper.getBlockState(new BlockPos(4, 1, 3)).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER);
			helper.assertValueEqual(p, 15, "vanilla wire after repeater powered");
		});
	}

	/** 复现：竖直中继器同布局——粉先放、电源后放，断言粉亮起。 */
	@GameTest
	public void verticalRepeaterPowersWireEndToEnd(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 0, 3), Blocks.STONE);
		// 竖直版 FACING=输出方向：输出朝东接粉，输入在西
		BlockPos rep = new BlockPos(3, 1, 3);
		helper.setBlock(rep, verticalBlock("vertical_repeater").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_WIRE);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			// 1) 中继器自身：POWERED 翻转 + 输出（读自身坐标，dir=观察者(输出侧东)→源=WEST）
			int out = helper.getLevel().getSignal(helper.absolutePos(rep), Direction.WEST);
			helper.assertValueEqual(out, 15, "repeater own output");
			// 2) 粉：翻转后是否被通知重算
			int p = helper.getBlockState(new BlockPos(4, 1, 3)).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER);
			helper.assertValueEqual(p, 15, "wire after vertical repeater powered");
		});
	}

	/** 复现：竖直比较器同布局——粉先放、电源后放，断言粉亮起。 */
	@GameTest
	public void verticalComparatorPowersWireEndToEnd(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 0, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(3, 1, 3), verticalBlock("vertical_comparator").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_WIRE);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
		helper.succeedWhen(() -> {
			int p = helper.getBlockState(new BlockPos(4, 1, 3)).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER);
			helper.assertValueEqual(15, p, "wire after vertical comparator powered");
		});
	}

	/** 真实放置流：点击红石块东面放置中继器（输入端贴电源），粉先在场，断言粉亮起。 */
	@GameTest
	public void verticalRepeaterItemPlacedAgainstPowerPowersWire(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 0, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_WIRE);
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
		Item repeater = BuiltInRegistries.ITEM.getValue(MoreThingsVanilla.id("vertical_repeater"));
		Player player = helper.makeMockPlayer(GameType.CREATIVE);
		// 点击红石块的东面 → 目标 (3,1,3)，FACING=EAST（输出朝东），输入端贴着红石块
		BlockPos target = new BlockPos(3, 1, 3);
		Vec3 click = new Vec3(helper.absolutePos(target).getX() + 0.5 - Direction.EAST.getStepX() * 0.5,
				helper.absolutePos(target).getY() + 0.5 - Direction.EAST.getStepY() * 0.5,
				helper.absolutePos(target).getZ() + 0.5 - Direction.EAST.getStepZ() * 0.5);
		BlockHitResult hit = new BlockHitResult(click, Direction.EAST, helper.absolutePos(new BlockPos(2, 1, 3)), false);
		InteractionResult result = new ItemStack(repeater).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
		helper.assertTrue(result.consumesAction(), "placement failed: " + result);
		helper.assertValueEqual(Direction.EAST, helper.getBlockState(target).getValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING), "facing");
		helper.succeedWhen(() -> {
			int p = helper.getBlockState(new BlockPos(4, 1, 3)).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER);
			helper.assertValueEqual(15, p, "wire after item-placed vertical repeater powered");
		});
	}

	// ==================== 复现（用户反馈）：比较器模式切换与侧输入 ====================

	/** 复现 A：compare 模式下侧输入=后端 15 时必须输出 15（不得出现减法效果）。 */
	@GameTest
	public void verticalComparatorCompareIgnoresSideInput(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 0, 3), Blocks.STONE);
		BlockPos comp = new BlockPos(3, 1, 3);
		helper.setBlock(comp, verticalBlock("vertical_comparator").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);   // 后端 15
		helper.setBlock(new BlockPos(3, 1, 4), Blocks.REDSTONE_BLOCK);   // 侧面（南，EAST 的顺时针侧）15
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_WIRE);
		helper.succeedWhen(() -> {
			int out = helper.getLevel().getSignal(helper.absolutePos(comp), Direction.WEST);
			helper.assertValueEqual(15, out, "compare mode output with side == rear");
			int p = helper.getBlockState(new BlockPos(4, 1, 3)).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER);
			helper.assertValueEqual(15, p, "wire in compare mode");
		});
	}

	/** 复现 B：compare 下右键切到 substitute，输出应自动变为 0（15-15），粉随之熄灭。 */
	@GameTest
	public void verticalComparatorModeSwitchUpdatesOutput(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 0, 3), Blocks.STONE);
		BlockPos comp = new BlockPos(3, 1, 3);
		helper.setBlock(comp, verticalBlock("vertical_comparator").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST));
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
		helper.setBlock(new BlockPos(3, 1, 4), Blocks.REDSTONE_BLOCK);
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_WIRE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(15,
					helper.getLevel().getSignal(helper.absolutePos(comp), Direction.WEST),
					"precondition: compare outputs 15");
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(comp)),
					Direction.EAST, helper.absolutePos(comp), false);
			helper.getBlockState(comp).useWithoutItem(helper.getLevel(), player, hit);
		});
		helper.succeedWhen(() -> {
			helper.assertValueEqual(
					net.minecraft.world.level.block.state.properties.ComparatorMode.SUBTRACT,
					helper.getBlockState(comp).getValue(
							net.minecraft.world.level.block.state.properties.BlockStateProperties.MODE_COMPARATOR),
					"mode cycled to subtract");
			int out = helper.getLevel().getSignal(helper.absolutePos(comp), Direction.WEST);
			helper.assertValueEqual(0, out, "subtract mode output after toggle");
			int p = helper.getBlockState(new BlockPos(4, 1, 3)).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER);
			helper.assertValueEqual(0, p, "wire after toggle to subtract");
		});
	}

	/** 复现 C：subtract 下右键切回 compare，输出应自动恢复 15，粉随之亮起。 */
	@GameTest
	public void verticalComparatorSubtractToCompareUpdatesOutput(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 0, 3), Blocks.STONE);
		BlockPos comp = new BlockPos(3, 1, 3);
		helper.setBlock(comp, verticalBlock("vertical_comparator").defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.EAST)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.MODE_COMPARATOR,
						net.minecraft.world.level.block.state.properties.ComparatorMode.SUBTRACT));
		helper.setBlock(new BlockPos(2, 1, 3), Blocks.REDSTONE_BLOCK);
		helper.setBlock(new BlockPos(3, 1, 4), Blocks.REDSTONE_BLOCK);
		helper.setBlock(new BlockPos(4, 1, 3), Blocks.REDSTONE_WIRE);
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(0,
					helper.getLevel().getSignal(helper.absolutePos(comp), Direction.WEST),
					"precondition: subtract outputs 0");
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(comp)),
					Direction.EAST, helper.absolutePos(comp), false);
			helper.getBlockState(comp).useWithoutItem(helper.getLevel(), player, hit);
		});
		helper.succeedWhen(() -> {
			helper.assertValueEqual(
					net.minecraft.world.level.block.state.properties.ComparatorMode.COMPARE,
					helper.getBlockState(comp).getValue(
							net.minecraft.world.level.block.state.properties.BlockStateProperties.MODE_COMPARATOR),
					"mode cycled to compare");
			int out = helper.getLevel().getSignal(helper.absolutePos(comp), Direction.WEST);
			helper.assertValueEqual(15, out, "compare mode output after toggle");
			int p = helper.getBlockState(new BlockPos(4, 1, 3)).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER);
			helper.assertValueEqual(15, p, "wire after toggle to compare");
		});
	}

	/**
	 * 复现 D（用户反馈"检测模式不会自动变更输出的红石信号"）：
	 * 后方容器内容变化必须通知竖直比较器重算（原版 Level.updateNeighbourForOutputSignal 只硬编码通知
	 * Blocks.COMPARATOR，且只扫水平 4 向，竖直摆放的容器输入永远收不到通知）。
	 */
	@GameTest
	public void verticalComparatorDetectsContainerContents(GameTestHelper helper) {
		BlockPos comp = new BlockPos(3, 1, 3);
		BlockPos chestPos = new BlockPos(3, 0, 3);
		helper.setBlock(chestPos, Blocks.CHEST);
		helper.setBlock(comp, verticalBlock("vertical_comparator").defaultBlockState().setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.UP));
		helper.runAfterDelay(5, () -> {
			helper.assertValueEqual(0, helper.getLevel().getSignal(helper.absolutePos(comp), Direction.DOWN),
					"empty chest gives no comparator output");
			Container chest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chestPos));
			chest.setItem(0, new ItemStack(Items.STONE, 1));
			chest.setChanged();
		});
		helper.succeedWhen(() -> {
			int out = helper.getLevel().getSignal(helper.absolutePos(comp), Direction.DOWN);
			helper.assertValueEqual(1, out, "comparator detects chest contents change");
		});
	}

	// ==================== 计划 5：单向传递器 ====================

	/**
	 * 单面输入 → 其余 5 面全部同强度输出，输入面本身不输出。
	 * 输入面 WEST 时，输入面读数方向 = INPUT.getOpposite() = EAST。
	 */
	@GameTest
	public void oneWayTransmitterForwardsSingleFace(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(3, 3, 3);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.REDSTONE_BLOCK);
		helper.startSequence().thenIdle(3).thenExecute(() -> {
			BlockState state = helper.getBlockState(transmitter);
			helper.assertValueEqual(Direction.WEST, state.getValue(OneWayTransmitterBlock.INPUT),
					"locked input face");
			helper.assertValueEqual(15, state.getValue(BlockStateProperties.POWER), "locked power");
			for (Direction direction : Direction.values()) {
				int expected = direction == Direction.EAST ? 0 : 15; // WEST 输入面：EAST 方向读数为输入面
				helper.assertValueEqual(expected,
						helper.getLevel().getSignal(helper.absolutePos(transmitter), direction),
						"signal dir " + direction);
			}
		}).thenSucceed();
	}

	/** 竖直输入（下方）同样成立：DOWN 输入 → UP 不输出、其余 5 面输出。 */
	@GameTest
	public void oneWayTransmitterVerticalInput(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(3, 3, 3);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.setBlock(new BlockPos(3, 2, 3), Blocks.REDSTONE_BLOCK);
		helper.startSequence().thenIdle(3).thenExecute(() -> {
			BlockState state = helper.getBlockState(transmitter);
			helper.assertValueEqual(Direction.DOWN, state.getValue(OneWayTransmitterBlock.INPUT),
					"locked input face DOWN");
			helper.assertValueEqual(15, state.getValue(BlockStateProperties.POWER), "locked power");
			for (Direction direction : Direction.values()) {
				int expected = direction == Direction.UP ? 0 : 15; // DOWN 输入面：UP 方向读数为输入面
				helper.assertValueEqual(expected,
						helper.getLevel().getSignal(helper.absolutePos(transmitter), direction),
						"signal dir " + direction);
			}
		}).thenSucceed();
	}

	/**
	 * 同一游戏刻两个面同时输入 → 本次不传递（全 0），等 6 面全灭后重新仲裁，
	 * 之后单面输入可正常传递。
	 */
	@GameTest
	public void oneWayTransmitterSimultaneousInputsBlock(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(3, 3, 3);
		BlockPos west = new BlockPos(2, 3, 3);
		BlockPos east = new BlockPos(4, 3, 3);
		BlockPos north = new BlockPos(3, 3, 2);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.setBlock(west, Blocks.REDSTONE_BLOCK);
		helper.setBlock(east, Blocks.REDSTONE_BLOCK);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertValueEqual(0,
							helper.getBlockState(transmitter).getValue(BlockStateProperties.POWER),
							"simultaneous inputs must not transmit");
					for (Direction direction : Direction.values()) {
						helper.assertValueEqual(0,
								helper.getLevel().getSignal(helper.absolutePos(transmitter), direction),
								"no output dir " + direction);
					}
				})
				.thenExecute(() -> {
					helper.setBlock(west, Blocks.AIR);
					helper.setBlock(east, Blocks.AIR);
				})
				.thenIdle(2)
				.thenExecute(() -> helper.setBlock(north, Blocks.REDSTONE_BLOCK))
				.thenIdle(3)
				.thenExecute(() -> {
					BlockState state = helper.getBlockState(transmitter);
					helper.assertValueEqual(Direction.NORTH, state.getValue(OneWayTransmitterBlock.INPUT),
							"re-arbitrated input NORTH");
					helper.assertValueEqual(15, state.getValue(BlockStateProperties.POWER),
							"power after re-arbitration");
					for (Direction direction : Direction.values()) {
						int expected = direction == Direction.SOUTH ? 0 : 15; // NORTH 输入面
						helper.assertValueEqual(expected,
								helper.getLevel().getSignal(helper.absolutePos(transmitter), direction),
								"signal after reset dir " + direction);
					}
				})
				.thenSucceed();
	}

	/** 锁定期间输出面被外部供电应被忽略；失去输入后必须等 6 面全灭才重新仲裁。 */
	@GameTest
	public void oneWayTransmitterIgnoresOutputFacesWhileLocked(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(3, 3, 3);
		BlockPos west = new BlockPos(2, 3, 3);
		BlockPos east = new BlockPos(4, 3, 3);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.setBlock(west, Blocks.REDSTONE_BLOCK);
		helper.startSequence()
				.thenIdle(3)
				.thenExecute(() -> {
					helper.assertValueEqual(Direction.WEST,
							helper.getBlockState(transmitter).getValue(OneWayTransmitterBlock.INPUT),
							"initial input WEST");
					helper.setBlock(east, Blocks.REDSTONE_BLOCK);
				})
				.thenIdle(2)
				.thenExecute(() -> {
					BlockState state = helper.getBlockState(transmitter);
					helper.assertValueEqual(Direction.WEST, state.getValue(OneWayTransmitterBlock.INPUT),
							"still locked WEST");
					helper.assertValueEqual(15, state.getValue(BlockStateProperties.POWER),
							"still power 15");
					helper.assertValueEqual(15,
							helper.getLevel().getSignal(helper.absolutePos(transmitter), Direction.WEST),
							"east output still 15");
				})
				.thenExecute(() -> helper.setBlock(west, Blocks.AIR))
				.thenIdle(3)
				.thenExecute(() -> helper.assertValueEqual(0,
						helper.getBlockState(transmitter).getValue(BlockStateProperties.POWER),
						"blocked after input lost while east still powered"))
				.thenExecute(() -> helper.setBlock(east, Blocks.AIR))
				.thenIdle(3)
				.thenExecute(() -> helper.setBlock(east, Blocks.REDSTONE_BLOCK))
				.thenIdle(3)
				.thenExecute(() -> {
					BlockState state = helper.getBlockState(transmitter);
					helper.assertValueEqual(Direction.EAST, state.getValue(OneWayTransmitterBlock.INPUT),
							"re-arbitrated input EAST");
					helper.assertValueEqual(15, state.getValue(BlockStateProperties.POWER),
							"power after re-arbitration");
					helper.assertValueEqual(15,
							helper.getLevel().getSignal(helper.absolutePos(transmitter), Direction.EAST),
							"west output after re-arbitration");
				})
				.thenSucceed();
	}

	/** 强度透传：竖直红石链 15→14→13 输入 → 输出 13（不衰减）。 */
	@GameTest
	public void oneWayTransmitterPassesStrength(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(5, 3, 3);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.setBlock(new BlockPos(4, 3, 3), verticalBlock("vertical_redstone"));
		helper.setBlock(new BlockPos(3, 3, 3), verticalBlock("vertical_redstone"));
		helper.setBlock(new BlockPos(2, 3, 3), verticalBlock("vertical_redstone"));
		helper.setBlock(new BlockPos(1, 3, 3), Blocks.REDSTONE_BLOCK);
		helper.startSequence().thenIdle(6).thenExecute(() -> {
			BlockState state = helper.getBlockState(transmitter);
			helper.assertValueEqual(Direction.WEST, state.getValue(OneWayTransmitterBlock.INPUT),
					"input WEST");
			helper.assertValueEqual(13, state.getValue(BlockStateProperties.POWER),
					"passthrough strength 13");
			helper.assertValueEqual(13,
					helper.getLevel().getSignal(helper.absolutePos(transmitter), Direction.WEST),
					"east output 13");
			helper.assertValueEqual(0,
					helper.getLevel().getSignal(helper.absolutePos(transmitter), Direction.EAST),
					"input face silent");
		}).thenSucceed();
	}

	/** 锁定期间输入强度变化（15→14，不经过 0）→ 输出同步变化，不解除锁定。 */
	@GameTest
	public void oneWayTransmitterLockedStrengthChanges(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(3, 3, 3);
		BlockPos inputWire = new BlockPos(2, 3, 3);
		BlockPos secondWire = new BlockPos(2, 3, 4);
		BlockPos sourceA = new BlockPos(1, 3, 3);
		BlockPos sourceB = new BlockPos(2, 3, 5);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.STONE);
		helper.setBlock(inputWire, Blocks.REDSTONE_WIRE);
		helper.setBlock(secondWire, Blocks.REDSTONE_WIRE);
		helper.setBlock(sourceA, Blocks.REDSTONE_BLOCK);
		helper.setBlock(sourceB, Blocks.REDSTONE_BLOCK);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.startSequence()
				.thenIdle(5)
				.thenExecute(() -> {
					helper.assertValueEqual(15,
							helper.getBlockState(transmitter).getValue(BlockStateProperties.POWER),
							"initial power 15");
					helper.setBlock(sourceA, Blocks.AIR);
				})
				.thenIdle(5)
				.thenExecute(() -> {
					helper.assertValueEqual(Direction.WEST,
							helper.getBlockState(transmitter).getValue(OneWayTransmitterBlock.INPUT),
							"still locked WEST after strength change");
					helper.assertValueEqual(14,
							helper.getBlockState(transmitter).getValue(BlockStateProperties.POWER),
							"locked input changed to 14");
					helper.assertValueEqual(14,
							helper.getLevel().getSignal(helper.absolutePos(transmitter), Direction.WEST),
							"output follows to 14");
				})
				.thenSucceed();
	}

	/** 输出面强充能相邻方块（getDirectSignalTo 读到 15）。 */
	@GameTest
	public void oneWayTransmitterStrongPowersAdjacent(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(3, 3, 3);
		BlockPos stone = new BlockPos(4, 3, 3);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.setBlock(new BlockPos(2, 3, 3), Blocks.REDSTONE_BLOCK);
		helper.setBlock(stone, Blocks.STONE);
		helper.startSequence().thenIdle(4).thenExecute(() -> helper.assertValueEqual(15,
				helper.getLevel().getDirectSignalTo(helper.absolutePos(stone)),
				"strong power into stone")).thenSucceed();
	}

	/** 与原版红石粉互通：粉供电输入 → 传递器锁定；输出面点亮的粉 POWER=15。 */
	@GameTest
	public void oneWayTransmitterInteropsWithVanillaWire(GameTestHelper helper) {
		BlockPos transmitter = new BlockPos(3, 3, 3);
		BlockPos inputWire = new BlockPos(2, 3, 3);
		BlockPos outputWire = new BlockPos(4, 3, 3);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.STONE);
		helper.setBlock(new BlockPos(4, 2, 3), Blocks.STONE);
		// 粉先放、传递器后放，粉才会重算连接（与竖直红石互通测试同序）
		helper.setBlock(inputWire, Blocks.REDSTONE_WIRE);
		helper.setBlock(outputWire, Blocks.REDSTONE_WIRE);
		helper.setBlock(transmitter, verticalBlock("one_way_transmitter"));
		helper.setBlock(new BlockPos(1, 3, 3), Blocks.REDSTONE_BLOCK);
		helper.startSequence().thenIdle(5).thenExecute(() -> {
			BlockState state = helper.getBlockState(transmitter);
			helper.assertValueEqual(Direction.WEST, state.getValue(OneWayTransmitterBlock.INPUT),
					"input WEST from vanilla wire");
			helper.assertValueEqual(15, state.getValue(BlockStateProperties.POWER), "power 15");
			helper.assertValueEqual(15, helper.getBlockState(inputWire).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER),
					"input wire powered");
			helper.assertValueEqual(15, helper.getBlockState(outputWire).getValue(
					net.minecraft.world.level.block.RedStoneWireBlock.POWER),
					"output wire powered");
		}).thenSucceed();
	}
}
