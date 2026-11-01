package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;

public final class MtvGameTests {

	@GameTest
	public void creativeTabRegistered(GameTestHelper helper) {
		helper.assertValueEqual("more-things-vanilla:WRONG_ID",
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
}
