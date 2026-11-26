package com.tt23xrstudio.endbox;

import com.mojang.serialization.MapCodec;
import com.tt23xrstudio.block.ProjectedBoxBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class EndBoxBlock extends ProjectedBoxBlock {
	private static final MapCodec<EndBoxBlock> CODEC = simpleCodec(EndBoxBlock::new);

	public EndBoxBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<EndBoxBlock> codec() {
		return CODEC;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EndBoxBlockEntity(pos, state);
	}
}
