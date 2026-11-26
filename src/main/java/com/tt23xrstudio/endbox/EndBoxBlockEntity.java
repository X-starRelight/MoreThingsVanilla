package com.tt23xrstudio.endbox;

import java.util.Set;

import com.tt23xrstudio.MoreThingsVanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EndBoxBlockEntity extends TheEndPortalBlockEntity {
	private static BlockEntityType<EndBoxBlockEntity> type;

	public EndBoxBlockEntity(BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public static void registerType(Block block) {
		type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, MoreThingsVanilla.id("end_box"),
				new BlockEntityType<>(EndBoxBlockEntity::new, Set.of(block)));
	}

	public static BlockEntityType<EndBoxBlockEntity> type() {
		return type;
	}

	@Override
	public boolean shouldRenderFace(Direction direction) {
		return level == null || !level.getBlockState(getBlockPos().relative(direction)).is(getBlockState().getBlock());
	}
}
