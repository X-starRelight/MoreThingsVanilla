package com.tt23xrstudio;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MoreThingsVanilla implements ModInitializer {
	public static final String MOD_ID = "more-things-vanilla";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 创造标签页物品清单。计划 2 起，各方块/物品注册时向此列表添加条目。 */
	public static final List<Item> TAB_ITEMS = new ArrayList<>();

	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("more_things_vanilla"));

	@Override
	public void onInitialize() {
		// 占位：原版会隐藏空的 CATEGORY 创造标签页（CreativeModeTab.shouldDisplay()），
		// 计划 2 注册首个方块后移除此行，TAB_ITEMS 将由真实内容接管
		TAB_ITEMS.add(Items.IRON_BLOCK);
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
		LOGGER.info("MoreThingsVanilla loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
