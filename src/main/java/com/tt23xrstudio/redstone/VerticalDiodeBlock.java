package com.tt23xrstudio.redstone;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.SignalGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.ticks.TickPriority;
import org.jspecify.annotations.Nullable;

/**
 * 竖直二极管基类（计划 4）：中继器/比较器共用，红石逻辑复刻原版 DiodeBlock，
 * 仅放置方式改为 6 向（FACING = 输出方向 = 点击面朝向，原版 FACING = 输入方向，语义翻转）。
 *
 * 与原版的差异（均因 FACING 语义翻转）：
 * - 输入读 pos.relative(FACING.getOpposite())，原版读 relative(FACING)
 * - getSignal 判定 direction == FACING.getOpposite()，原版 == FACING
 * - updateNeighborsInFront 更新 pos.relative(FACING)（输出侧），原版为 getOpposite()
 * - 侧面输入：FACING 为水平时按原版读左右 2 向；FACING 为上/下时读 4 个水平向
 *   （原版 getClockWise/getCounterClockWise 对 UP/DOWN 抛 IllegalStateException）
 * - 完整立方体、不依赖支撑方块（canSurvive 恒 true、无掉落分支）
 */
public abstract class VerticalDiodeBlock extends Block {
	public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
	/** 输出方向（原版 DiodeBlock 的 FACING 是输入方向，此处相反）。 */
	public static final net.minecraft.world.level.block.state.properties.EnumProperty<Direction> FACING =
			BlockStateProperties.FACING;

	protected VerticalDiodeBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected abstract MapCodec<? extends Block> codec();

	// ==================== 信号输出（仅输出面可见） ====================

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	protected int ownSignal(BlockState state, BlockGetter level, BlockPos pos) {
		return state.getValue(POWERED) ? this.getOutputSignal(level, pos, state) : 0;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		// 观察者在输出侧时 direction（观察者→源）= FACING.getOpposite()
		return state.getValue(FACING).getOpposite() == direction ? this.ownSignal(state, level, pos) : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		return state.getSignal(level, pos, direction);
	}

	// ==================== 输入 ====================

	protected int getInputSignal(Level level, BlockPos pos, BlockState state) {
		Direction inputDirection = state.getValue(FACING).getOpposite();
		BlockPos targetPos = pos.relative(inputDirection);
		int input = level.getSignal(targetPos, inputDirection);
		if (input >= 15) {
			return input;
		}
		// 原版粉在输入面不朝二极管时不返回信号，需直接读 POWER（竖直方向的粉尤其如此）；
		// 竖直红石无需特判——其 getSignal 六面统一返回 POWER
		BlockState targetState = level.getBlockState(targetPos);
		if (targetState.is(Blocks.REDSTONE_WIRE)) {
			return Math.max(input, targetState.getValue(RedStoneWireBlock.POWER));
		}
		return input;
	}

	/**
	 * 侧面（交替）输入：FACING 水平 → 原版左右 2 向；FACING 垂直 → 4 个水平向。
	 * 走 getControlInputSignal 等价逻辑（SignalGetter.getControlInputSignal 的 diodesOnly 分支
	 * 只认原版 DiodeBlock，此处补认本类）。
	 */
	protected int getAlternateSignal(SignalGetter level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(FACING);
		boolean sideInputDiodesOnly = this.sideInputDiodesOnly();
		int result = 0;
		if (facing.getAxis() == Direction.Axis.Y) {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				result = Math.max(result, controlInputSignal(level, pos.relative(direction), direction, sideInputDiodesOnly));
			}
		} else {
			Direction clockWise = facing.getClockWise();
			Direction counterClockWise = facing.getCounterClockWise();
			result = Math.max(result, controlInputSignal(level, pos.relative(clockWise), clockWise, sideInputDiodesOnly));
			result = Math.max(result, controlInputSignal(level, pos.relative(counterClockWise), counterClockWise, sideInputDiodesOnly));
		}
		return result;
	}

	/** SignalGetter.getControlInputSignal 的复刻（diodesOnly 分支补认竖直二极管）。 */
	private static int controlInputSignal(SignalGetter level, BlockPos pos, Direction direction, boolean diodesOnly) {
		BlockState state = level.getBlockState(pos);
		if (diodesOnly) {
			return DiodeBlock.isDiode(state) || state.getBlock() instanceof VerticalDiodeBlock
					? level.getDirectSignal(pos, direction) : 0;
		}
		if (state.is(Blocks.REDSTONE_BLOCK)) {
			return 15;
		}
		if (state.is(Blocks.REDSTONE_WIRE)) {
			return state.getValue(RedStoneWireBlock.POWER);
		}
		return state.isSignalSource() ? level.getDirectSignal(pos, direction) : 0;
	}

	protected boolean sideInputDiodesOnly() {
		return false;
	}

	protected int getOutputSignal(BlockGetter level, BlockPos pos, BlockState state) {
		return 15;
	}

	// ==================== 调度（复刻 DiodeBlock） ====================

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!this.isLocked(level, pos, state)) {
			boolean on = state.getValue(POWERED);
			boolean shouldTurnOn = this.shouldTurnOn(level, pos, state);
			if (on && !shouldTurnOn) {
				level.setBlock(pos, state.setValue(POWERED, false), 2);
			} else if (!on) {
				level.setBlock(pos, state.setValue(POWERED, true), 2);
				if (!shouldTurnOn) {
					level.scheduleTick(pos, this, this.getDelay(state), TickPriority.VERY_HIGH);
				}
			}
		}
	}

	@Override
	protected void neighborChanged(
			BlockState state, Level level, BlockPos pos, Block block,
			@Nullable Orientation orientation, boolean movedByPiston) {
		// 完整立方体无支撑校验，去掉原版的掉落分支
		if (level.getBlockState(pos).is(this)) {
			this.checkTickOnNeighbor(level, pos, state);
		}
	}

	protected void checkTickOnNeighbor(Level level, BlockPos pos, BlockState state) {
		if (!this.isLocked(level, pos, state)) {
			boolean on = state.getValue(POWERED);
			boolean shouldTurnOn = this.shouldTurnOn(level, pos, state);
			if (on != shouldTurnOn && !level.getBlockTicks().willTickThisTick(pos, this)) {
				TickPriority priority = TickPriority.HIGH;
				if (this.shouldPrioritize(level, pos, state)) {
					priority = TickPriority.EXTREMELY_HIGH;
				} else if (on) {
					priority = TickPriority.VERY_HIGH;
				}
				level.scheduleTick(pos, this, this.getDelay(state), priority);
			}
		}
	}

	public boolean isLocked(LevelReader level, BlockPos pos, BlockState state) {
		return false;
	}

	protected boolean shouldTurnOn(Level level, BlockPos pos, BlockState state) {
		return this.getInputSignal(level, pos, state) > 0;
	}

	public boolean shouldPrioritize(BlockGetter level, BlockPos pos, BlockState state) {
		Direction front = state.getValue(FACING);
		BlockState frontState = level.getBlockState(pos.relative(front));
		// 原版比较 neighbor 的输入方向；本类 neighbor 的输入 = neighbor FACING.getOpposite()
		return frontState.getBlock() instanceof VerticalDiodeBlock
				&& frontState.getValue(FACING).getOpposite() != front;
	}

	protected abstract int getDelay(BlockState state);

	// ==================== 放置与邻居通知 ====================

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// 输出方向 = 点击面朝向（用户拍板）
		return this.defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
		if (this.shouldTurnOn(level, pos, state)) {
			level.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!oldState.is(state.getBlock())) {
			this.updateNeighborsInFront(level, pos, state);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!movedByPiston) {
			this.updateNeighborsInFront(level, pos, state);
		}
	}

	protected void updateNeighborsInFront(Level level, BlockPos pos, BlockState state) {
		Direction front = state.getValue(FACING);
		BlockPos frontPos = pos.relative(front);
		Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, front, Direction.UP);
		level.neighborChanged(frontPos, this, orientation);
		// 除自身（front 的 front.getOpposite() 方向是本方块）外更新 front 的邻居
		level.updateNeighborsAtExceptFromFacing(frontPos, this, front.getOpposite(), orientation);
	}

	public static boolean isVerticalDiode(BlockState state) {
		return state.getBlock() instanceof VerticalDiodeBlock;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, POWERED);
	}
}
