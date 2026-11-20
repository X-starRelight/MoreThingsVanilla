package com.tt23xrstudio.redstone;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * 竖直红石（计划 4，原创）：完整 1×1×1 立方体，六面均可收发红石能量、整块统一电平。
 * 红石逻辑复刻原版 RedStoneWireBlock + DefaultRedstoneWireEvaluator：
 * - 每过一格衰减 -1（块信号满 15 不衰减，红石粉邻居取 POWER-1）
 * - 输出时强充能紧邻方块（六面 getDirectSignal）
 * - 与原版红石粉互通（isSignalSource=true → 原版粉视觉连接、信号双向）
 * 26.2 适配：原版粉是地面 2D 连线（N/E/S/W 属性 + 水平求值器），竖直版无连接属性、
 * 求值改为六邻域扫描；求值期间关闭自身信号输出（同原版 shouldSignal 防经导体读回自身）。
 */
public class VerticalRedstoneBlock extends Block {
	public static final MapCodec<VerticalRedstoneBlock> CODEC = simpleCodec(VerticalRedstoneBlock::new);
	public static final IntegerProperty POWER = BlockStateProperties.POWER;

	/** 求值期间关闭本方块信号输出，防止经导体方块读回自身电平（同原版 RedStoneWireBlock.shouldSignal）。 */
	private boolean shouldSignal = true;

	public VerticalRedstoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
	}

	@Override
	public MapCodec<VerticalRedstoneBlock> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER);
	}

	// ==================== 信号输出（六面统一） ====================

	@Override
	protected boolean isSignalSource(BlockState state) {
		// 求值期间为 false（同原版粉），平时恒 true 让原版粉 shouldConnectTo 视觉连接
		return this.shouldSignal;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return this.shouldSignal ? state.getValue(POWER) : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		// 强充能紧邻方块（六面）
		return this.shouldSignal ? state.getSignal(level, pos, direction) : 0;
	}

	// ==================== 放置与邻居更新 ====================

	@Override
	public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
		return this.defaultBlockState();
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
			this.updatePowerStrength(level, pos, state, null);
			for (Direction direction : Direction.values()) {
				level.updateNeighborsAt(pos.relative(direction), this);
			}
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!movedByPiston) {
			for (Direction direction : Direction.values()) {
				level.updateNeighborsAt(pos.relative(direction), this);
			}
			this.updatePowerStrength(level, pos, state, null);
		}
	}

	@Override
	protected void neighborChanged(
			BlockState state, Level level, BlockPos pos, Block block,
			@Nullable Orientation orientation, boolean movedByPiston) {
		if (!level.isClientSide()) {
			this.updatePowerStrength(level, pos, state, orientation);
		}
	}

	// ==================== 电平求值（复刻 DefaultRedstoneWireEvaluator 语义） ====================

	private void updatePowerStrength(Level level, BlockPos pos, BlockState state, @Nullable Orientation orientation) {
		int target = this.calculateTargetStrength(level, pos);
		if (state.getValue(POWER) != target) {
			if (level.getBlockState(pos) == state) {
				level.setBlock(pos, state.setValue(POWER, target), 2);
			}
			level.updateNeighborsAt(pos, this);
			for (Direction direction : Direction.values()) {
				level.updateNeighborsAt(pos.relative(direction), this);
			}
		}
	}

	/**
	 * 目标电平 = max(块信号, 红石粉信号 - 1)；块信号满 15 直接取 15。
	 * 块信号：六邻域中非粉邻居的 getSignal（求值期间 shouldSignal=false，防止经导体读回自身）。
	 * 粉信号：六邻域中本方块/原版红石粉邻居的 POWER（竖直版六向，原版求值器只扫水平+斜上斜下）。
	 */
	private int calculateTargetStrength(Level level, BlockPos pos) {
		this.shouldSignal = false;
		int blockSignal = 0;
		int wireSignal = 0;
		try {
			for (Direction direction : Direction.values()) {
				BlockPos neighborPos = pos.relative(direction);
				BlockState neighborState = level.getBlockState(neighborPos);
				if (neighborState.is(this)) {
					wireSignal = Math.max(wireSignal, neighborState.getValue(POWER));
				} else if (neighborState.is(Blocks.REDSTONE_WIRE)) {
					wireSignal = Math.max(wireSignal, neighborState.getValue(RedStoneWireBlock.POWER));
				} else {
					blockSignal = Math.max(blockSignal, level.getSignal(neighborPos, direction));
				}
			}
		} finally {
			this.shouldSignal = true;
		}
		if (blockSignal >= 15) {
			return 15;
		}
		return Math.max(blockSignal, Math.max(0, wireSignal - 1));
	}
}
