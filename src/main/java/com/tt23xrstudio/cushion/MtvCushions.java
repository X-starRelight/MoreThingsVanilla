package com.tt23xrstudio.cushion;

import java.util.EnumMap;
import java.util.Map;

import com.tt23xrstudio.MoreThingsVanilla;

import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

/**
 * 坐垫注册中心（计划 3，复刻 26.4）：实体 + 16 色物品 + 4 音效事件 + 熔炉燃料。
 * id 与 26.4 原版完全平行（仅命名空间不同），升级 26.4 时进 DataFixer 转换表（设计.md）。
 */
public final class MtvCushions {
	private MtvCushions() {}

	/** 熔炉燃料燃烧时间（tick）。26.4：cookingFuel(COOKING_TIME_WOOL_SLABS)，time_wool_slabs.json = 50（用户拍板）。 */
	private static final int CUSHION_FUEL_BURN_TIME = 50;

	/** 16 色坐垫物品，按色索引（中键取物、破坏掉落）。 */
	private static final Map<DyeColor, Item> ITEMS_BY_COLOR = new EnumMap<>(DyeColor.class);

	/** 坐垫实体（26.4：MISC / noLootTable / sized(1.0, 0.25) / tracking 10 / updateInterval MAX_VALUE）。 */
	public static final EntityType<MtvCushion> CUSHION = registerEntityType();

	/** 26.4 原版 4 个坐垫音效事件，提取到本 mod 命名空间（ogg 15 个 + sounds.json 4 键）。 */
	public static final SoundEvent CUSHION_BREAK = registerSound("entity.cushion.break");
	public static final SoundEvent CUSHION_GET_UP = registerSound("entity.cushion.get_up");
	public static final SoundEvent CUSHION_PLACE = registerSound("entity.cushion.place");
	public static final SoundEvent CUSHION_SIT = registerSound("entity.cushion.sit");

	/** 注册 16 色物品与燃料值（实体/音效在类加载时已注册，onInitialize 调用本方法触发）。 */
	public static void registerAll() {
		// 16 色坐垫（26.4：stacksTo(16)；id 路径 <color>_cushion 与原版平行）
		for (DyeColor color : DyeColor.values()) {
			String path = color.getName() + "_cushion";
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MoreThingsVanilla.id(path));
			Item item = Registry.register(BuiltInRegistries.ITEM, key,
					new MtvCushionItem(color, new Item.Properties().setId(key).stacksTo(16)));
			ITEMS_BY_COLOR.put(color, item);
			MoreThingsVanilla.TAB_ITEMS.add(item);
		}
		// 熔炉燃料：每色 50 tick（26.4 羊毛半砖燃料值；本 mod 羊毛半砖 300 系计划 2 拍板，坐垫单独按 50）
		FuelValueEvents.BUILD.register((builder, context) -> {
			for (Item item : ITEMS_BY_COLOR.values()) {
				builder.add(item, CUSHION_FUEL_BURN_TIME);
			}
		});
	}

	/** 按色取坐垫物品（中键取物、破坏掉落）。 */
	public static Item item(DyeColor color) {
		return ITEMS_BY_COLOR.get(color);
	}

	private static EntityType<MtvCushion> registerEntityType() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MoreThingsVanilla.id("cushion"));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				EntityType.Builder.of(MtvCushion::new, MobCategory.MISC)
						.sized(1.0F, 0.25F)
						.clientTrackingRange(10)
						.updateInterval(Integer.MAX_VALUE)
						.noLootTable()
						.build(key));
	}

	private static SoundEvent registerSound(String path) {
		Identifier id = MoreThingsVanilla.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
