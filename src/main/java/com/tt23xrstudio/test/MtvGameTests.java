package com.tt23xrstudio.test;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

public final class MtvGameTests {

	@GameTest
	public void creativeTabRegistered(GameTestHelper helper) {
		ResourceKey<CreativeModeTab> key = ResourceKey.create(
				Registries.CREATIVE_MODE_TAB,
				MoreThingsVanilla.id("more_things_vanilla")
		);
		helper.assertTrue(
				BuiltInRegistries.CREATIVE_MODE_TAB.containsKey(key),
				"creative tab not registered: " + key
		);
		helper.succeed();
	}
}
