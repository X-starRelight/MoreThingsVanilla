package com.tt23xrstudio.skybox;

import com.mojang.serialization.MapCodec;
import com.tt23xrstudio.block.ProjectedBoxBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.Orientation;

/**
 * 天空盒方块（基础款 Block of Sky）。
 *
 * <p>方块本身不可见、不挡光、不遮挡邻居，实际画面由 {@code SkyBoxRenderer}
 * 六面采样天空立方体贴图绘制。没有红石开关，也不存在 ACTIVE / 天空类型状态。
 */
public class SkyBoxBlock extends ProjectedBoxBlock {
	public static final MapCodec<SkyBoxBlock> CODEC = simpleCodec(SkyBoxBlock::new);

	public SkyBoxBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
		return new SkyBoxBlockEntity(blockPos, blockState);
	}

	@Override
	protected BlockState updateShape(
			BlockState state,
			LevelReader level,
			ScheduledTickAccess ticks,
			BlockPos pos,
			Direction directionToNeighbour,
			BlockPos neighbourPos,
			BlockState neighbourState,
			RandomSource random
	) {
		invalidateFaces(level, pos);
		return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
	}

	@Override
	protected void neighborChanged(
			BlockState state,
			Level level,
			BlockPos pos,
			Block block,
			Orientation orientation,
			boolean movedByPiston
	) {
		super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
		invalidateFaces(level, pos);
	}

	private static void invalidateFaces(LevelReader level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof SkyBoxBlockEntity skyBox) {
			skyBox.invalidateFaces();
		}
	}
}
