package com.tt23xrstudio.redstone;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 竖直中继器（计划 4）：红石逻辑复刻原版 RepeaterBlock（延迟档位/锁存/优先调度），
 * 放置方式改为 6 向（FACING = 输出方向 = 点击面朝向）。
 */
public class VerticalRepeaterBlock extends VerticalDiodeBlock {
	public static final MapCodec<VerticalRepeaterBlock> CODEC = simpleCodec(VerticalRepeaterBlock::new);
	public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
	public static final IntegerProperty DELAY = BlockStateProperties.DELAY;

	public VerticalRepeaterBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
				this.stateDefinition.any()
						.setValue(FACING, Direction.NORTH)
						.setValue(DELAY, 1)
						.setValue(LOCKED, false)
						.setValue(POWERED, false));
	}

	@Override
	public MapCodec<VerticalRepeaterBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(
			BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!player.getAbilities().mayBuild) {
			return InteractionResult.PASS;
		}
		level.setBlock(pos, state.cycle(DELAY), 3);
		return InteractionResult.SUCCESS;
	}

	@Override
	protected int getDelay(BlockState state) {
		return state.getValue(DELAY) * 2;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		BlockState state = super.getStateForPlacement(context);
		return state.setValue(LOCKED, this.isLocked(context.getLevel(), context.getClickedPos(), state));
	}

	@Override
	protected BlockState updateShape(
			BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
		// 完整立方体无支撑校验（去掉原版 Direction.DOWN 掉落分支），其余复刻锁存重算
		return !level.isClientSide() && directionToNeighbour.getAxis() != state.getValue(FACING).getAxis()
				? state.setValue(LOCKED, this.isLocked(level, pos, state))
				: state;
	}

	@Override
	public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
		return this.getAlternateSignal(level, pos, state) > 0;
	}

	@Override
	protected boolean sideInputDiodesOnly() {
		return true;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (state.getValue(POWERED)) {
			Direction direction = state.getValue(FACING);
			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
			double y = pos.getY() + 0.4 + (random.nextDouble() - 0.5) * 0.2;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.2;
			float offset = -5.0F;
			if (random.nextBoolean()) {
				offset = state.getValue(DELAY) * 2 - 1;
			}
			offset /= 16.0F;
			double xo = offset * direction.getStepX();
			double yo = offset * direction.getStepY();
			double zo = offset * direction.getStepZ();
			level.addParticle(DustParticleOptions.REDSTONE, x + xo, y + yo, z + zo, 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, DELAY, LOCKED, POWERED);
	}
}
