package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

public final class MtvGameTests {

	@GameTest
	public void creativeTabRegistered(GameTestHelper helper) {
		helper.assertTrue(
				BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(MoreThingsVanilla.CREATIVE_TAB_KEY),
				"creative tab not registered: " + MoreThingsVanilla.CREATIVE_TAB_KEY
		);
		helper.succeed();
	}
}
