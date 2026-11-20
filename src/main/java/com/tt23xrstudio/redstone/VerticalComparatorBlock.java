package com.tt23xrstudio.redstone;

import com.mojang.serialization.MapCodec;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.ticks.TickPriority;

/**
 * 竖直比较器（计划 4）：红石逻辑复刻原版 ComparatorBlock（比较/减法、侧面输入、
 * 模拟信号读取、物品展示框），放置方式改为 6 向（FACING = 输出方向 = 点击面朝向）。
 * 输出值存 VerticalComparatorBlockEntity（同原版 ComparatorBlockEntity）。
 */
public class VerticalComparatorBlock extends VerticalDiodeBlock implements net.minecraft.world.level.block.EntityBlock {
	public static final MapCodec<VerticalComparatorBlock> CODEC = simpleCodec(VerticalComparatorBlock::new);
	public static final EnumProperty<ComparatorMode> MODE = BlockStateProperties.MODE_COMPARATOR;

	public VerticalComparatorBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(
				this.stateDefinition.any()
						.setValue(FACING, Direction.NORTH)
						.setValue(POWERED, false)
						.setValue(MODE, ComparatorMode.COMPARE));
	}

	@Override
	public MapCodec<VerticalComparatorBlock> codec() {
		return CODEC;
	}

	@Override
	protected int getDelay(BlockState state) {
		return 2;
	}

	@Override
	protected int getOutputSignal(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockState state) {
		return level.getBlockEntity(pos) instanceof VerticalComparatorBlockEntity entity ? entity.getOutputSignal() : 0;
	}

	private int calculateOutputSignal(Level level, BlockPos pos, BlockState state) {
		int inputSignal = this.getInputSignal(level, pos, state);
		if (inputSignal == 0) {
			return 0;
		}
		int alternateSignal = this.getAlternateSignal(level, pos, state);
		if (alternateSignal > inputSignal) {
			return 0;
		}
		return state.getValue(MODE) == ComparatorMode.SUBTRACT ? inputSignal - alternateSignal : inputSignal;
	}

	@Override
	protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
		int input = this.getInputSignal(level, pos, state);
		if (input == 0) {
			return false;
		}
		int sideInput = this.getAlternateSignal(level, pos, state);
		return input > sideInput || (input == sideInput && state.getValue(MODE) == ComparatorMode.COMPARE);
	}

	/** 模拟信号读取（容器/红石块等），FACING 语义翻转：输入方向 = FACING.getOpposite()。 */
	@Override
	protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
		int resultSignal = super.getInputSignal(level, pos, state);
		Direction inputDirection = state.getValue(FACING).getOpposite();
		BlockPos targetPos = pos.relative(inputDirection);
		BlockState targetState = level.getBlockState(targetPos);
		if (targetState.hasAnalogOutputSignal()) {
			resultSignal = targetState.getAnalogOutputSignal(level, targetPos, inputDirection.getOpposite());
		} else if (resultSignal < 15 && targetState.isRedstoneConductor(level, targetPos)) {
			targetPos = targetPos.relative(inputDirection);
			targetState = level.getBlockState(targetPos);
			ItemFrame itemFrame = this.getItemFrame(level, inputDirection, targetPos);
			int itemFrameOrBlockSignal = Math.max(
					itemFrame == null ? Integer.MIN_VALUE : itemFrame.getAnalogOutput(),
					targetState.hasAnalogOutputSignal()
							? targetState.getAnalogOutputSignal(level, targetPos, inputDirection.getOpposite())
							: Integer.MIN_VALUE);
			if (itemFrameOrBlockSignal != Integer.MIN_VALUE) {
				resultSignal = itemFrameOrBlockSignal;
			}
		}
		return resultSignal;
	}

	private ItemFrame getItemFrame(Level level, Direction direction, BlockPos tPos) {
		List<ItemFrame> itemFrames = level.getEntitiesOfClass(
				ItemFrame.class,
				new AABB(tPos.getX(), tPos.getY(), tPos.getZ(), tPos.getX() + 1, tPos.getY() + 1, tPos.getZ() + 1),
				entity -> entity.getDirection() == direction);
		return itemFrames.size() == 1 ? itemFrames.get(0) : null;
	}

	@Override
	protected InteractionResult useWithoutItem(
			BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (!player.getAbilities().mayBuild) {
			return InteractionResult.PASS;
		}
		state = state.cycle(MODE);
		float pitch = state.getValue(MODE) == ComparatorMode.SUBTRACT ? 0.55F : 0.5F;
		level.playSound(player, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3F, pitch);
		level.setBlock(pos, state, 2);
		if (level.getBlockState(pos).is(this)) {
			this.refreshOutputState(level, pos, state);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
		if (!level.getBlockTicks().willTickThisTick(pos, this)) {
			int outputValue = this.calculateOutputSignal(level, pos, state);
			int oldValue = level.getBlockEntity(pos) instanceof VerticalComparatorBlockEntity entity
					? entity.getOutputSignal() : 0;
			if (outputValue != oldValue || state.getValue(POWERED) != this.shouldTurnOn(level, pos, state)) {
				TickPriority priority = this.shouldPrioritize(level, pos, state) ? TickPriority.HIGH : TickPriority.NORMAL;
				level.scheduleTick(pos, this, 2, priority);
			}
		}
	}

	private void refreshOutputState(Level level, BlockPos pos, BlockState state) {
		int outputValue = this.calculateOutputSignal(level, pos, state);
		BlockEntity blockEntity = level.getBlockEntity(pos);
		int oldValue = 0;
		if (blockEntity instanceof VerticalComparatorBlockEntity entity) {
			oldValue = entity.getOutputSignal();
			entity.setOutputSignal(outputValue);
		}
		if (oldValue != outputValue || state.getValue(MODE) == ComparatorMode.COMPARE) {
			boolean sourceOn = this.shouldTurnOn(level, pos, state);
			boolean isOn = state.getValue(POWERED);
			if (isOn && !sourceOn) {
				level.setBlock(pos, state.setValue(POWERED, false), 2);
			} else if (!isOn && sourceOn) {
				level.setBlock(pos, state.setValue(POWERED, true), 2);
			}
			this.updateNeighborsInFront(level, pos, state);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		this.refreshOutputState(level, pos, state);
	}

	@Override
	protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
		super.triggerEvent(state, level, pos, b0, b1);
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity != null && blockEntity.triggerEvent(b0, b1);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
		return new VerticalComparatorBlockEntity(worldPosition, blockState);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, MODE, POWERED);
	}
}
