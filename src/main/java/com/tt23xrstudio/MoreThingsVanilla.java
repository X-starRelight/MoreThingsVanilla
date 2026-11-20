package com.tt23xrstudio;

import java.util.ArrayList;
import java.util.List;

import com.tt23xrstudio.block.MtvBlocks;
import com.tt23xrstudio.cushion.MtvCushions;

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
		Registry.register(
				BuiltInRegistries.CREATIVE_MODE_TAB,
				CREATIVE_TAB_KEY,
				FabricCreativeModeTab.builder()
						.title(Component.translatable("itemGroup.more_things_vanilla"))
						// 计划 4 拍板：图标换为竖直红石物品（supplier 惰性求值，晚于方块注册）
						.icon(() -> new ItemStack(MtvBlocks.ALL.get("vertical_redstone")))
						.displayItems((context, output) -> TAB_ITEMS.forEach(output::accept))
						.build()
		);
		// 计划 2 的半砖/楼梯（占位铁块已随首个真实方块移除，见计划 1 Task 6 约定）
		MtvBlocks.registerAll();
		// 计划 3 的坐垫（复刻 26.4：实体 + 16 色物品 + 4 音效 + 燃料）
		MtvCushions.registerAll();
		LOGGER.info("MoreThingsVanilla loaded");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
