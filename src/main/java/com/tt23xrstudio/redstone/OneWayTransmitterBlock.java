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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/**
 * 单向传递器（计划 5，原创）：完整立方体，六个面都是输入候选。
 *
 * 规则（用户拍板）：
 * - 某一面先收到信号 → 该面锁定为输入面，其余 5 面全部以相同强度输出；
 * - 同一游戏刻多个面到达按「同时」处理：多等 1 tick 再仲裁，避免先到面闪一下输出；
 *   仲裁时 ≥2 面有信号 → 本次不输出，进入等待全灭；
 * - 锁定期间只认输入面，输出面即使被外部供电也被忽略；
 * - 输入归零 → 输出立即归零并进入等待全灭；只有 6 面全部为 0 才恢复空闲并重新仲裁；
 * - 输入多少就传多少（不衰减），输出面强充能相邻方块。
 *
 * 1 tick 仲裁窗口用原版方块 tick 队列（ServerLevel.scheduleTick）实现；
 * 「等待全灭」用普通方块实体持久化，避免输出侧红石粉的衰减残余被误判成新输入而自锁。
 */
public class OneWayTransmitterBlock extends Block implements EntityBlock {
	public static final MapCodec<OneWayTransmitterBlock> CODEC = simpleCodec(OneWayTransmitterBlock::new);
	public static final IntegerProperty POWER = BlockStateProperties.POWER;
	/** 当前锁定的输入面；POWER=0 时该值仅表示上一次锁定的面，不参与输出。 */
	public static final EnumProperty<Direction> INPUT = EnumProperty.create("input", Direction.class);

	public OneWayTransmitterBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
				.setValue(POWER, 0)
				.setValue(INPUT, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(POWER, INPUT);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new OneWayTransmitterBlockEntity(pos, state);
	}

	// ==================== 信号输出（输入面 0，其余 5 面 POWER） ====================

	@Override
	protected boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		int power = state.getValue(POWER);
		// direction = 接收者→本方块；输入面接收者方向 = INPUT.getOpposite()
		return power > 0 && direction != state.getValue(INPUT).getOpposite() ? power : 0;
	}

	@Override
	protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
		// 输出面强充能相邻方块（六面中的 5 个输出面）
		return state.getSignal(level, pos, direction);
	}

	// ==================== 放置 / 移除 / 邻居更新 ====================

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		// 自身状态变化（POWER/INPUT）也会触发 onPlace，用 oldState 守卫避免重复排仲裁
		if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
			this.scheduleIfNeeded(level, pos, state);
		}
	}

	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		if (!movedByPiston) {
			this.notifyNeighbors(level, pos);
		}
	}

	@Override
	protected void neighborChanged(
			BlockState state, Level level, BlockPos pos, Block block,
			@Nullable Orientation orientation, boolean movedByPiston) {
		if (level.isClientSide() || !level.getBlockState(pos).is(this)) {
			return;
		}
		if (state.getValue(POWER) > 0) {
			this.refreshLocked(level, pos, state);
			return;
		}
		OneWayTransmitterBlockEntity blockEntity = this.blockEntity(level, pos);
		if (blockEntity != null && blockEntity.isBlocked()) {
			// 等待全灭期间忽略一切信号，只检查 6 面是否都已归零
			if (this.allFacesOff(level, pos)) {
				blockEntity.setBlocked(false);
			}
			return;
		}
		this.scheduleIfNeeded(level, pos, state);
	}

	// ==================== 1 tick 仲裁 ====================

	private void scheduleIfNeeded(Level level, BlockPos pos, BlockState state) {
		if (state.getValue(POWER) > 0 || !(level instanceof ServerLevel serverLevel)) {
			return;
		}
		OneWayTransmitterBlockEntity blockEntity = this.blockEntity(level, pos);
		if (blockEntity != null && blockEntity.isBlocked()) {
			return;
		}
		// 同一 tick 内多次邻居更新只排一个仲裁 tick
		if (serverLevel.getBlockTicks().hasScheduledTick(pos, this)) {
			return;
		}
		if (this.anyFaceOn(level, pos)) {
			serverLevel.scheduleTick(pos, this, 1);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		OneWayTransmitterBlockEntity blockEntity = this.blockEntity(level, pos);
		if (blockEntity != null && blockEntity.isBlocked()) {
			if (this.allFacesOff(level, pos)) {
				blockEntity.setBlocked(false);
			}
			return;
		}
		if (state.getValue(POWER) > 0) {
			this.refreshLocked(level, pos, state);
			return;
		}
		Direction lockedInput = null;
		int lockedSignal = 0;
		int inputCount = 0;
		for (Direction direction : Direction.values()) {
			int signal = this.readFace(level, pos, direction);
			if (signal > 0) {
				inputCount++;
				if (lockedInput == null || signal > lockedSignal) {
					lockedInput = direction;
					lockedSignal = signal;
				}
			}
		}
		if (inputCount == 1) {
			this.lock(level, pos, state, blockEntity, lockedInput, lockedSignal);
		} else if (inputCount >= 2 && blockEntity != null) {
			// 同刻多面到达：本次不传递，等 6 面全灭后再重新仲裁
			blockEntity.setBlocked(true);
		}
	}

	private void lock(ServerLevel level, BlockPos pos, BlockState state,
			@Nullable OneWayTransmitterBlockEntity blockEntity, Direction input, int signal) {
		if (blockEntity != null) {
			blockEntity.setBlocked(false);
		}
		BlockState newState = state.setValue(INPUT, input).setValue(POWER, signal);
		if (newState != state) {
			level.setBlock(pos, newState, 2);
		}
		this.notifyNeighbors(level, pos);
	}

	private void refreshLocked(Level level, BlockPos pos, BlockState state) {
		Direction input = state.getValue(INPUT);
		int signal = this.readFace(level, pos, input);
		int power = state.getValue(POWER);
		if (signal <= 0) {
			OneWayTransmitterBlockEntity blockEntity = this.blockEntity(level, pos);
			if (blockEntity != null) {
				blockEntity.setBlocked(true);
			}
			if (power != 0) {
				level.setBlock(pos, state.setValue(POWER, 0), 2);
			}
			this.notifyNeighbors(level, pos);
			return;
		}
		if (signal != power) {
			level.setBlock(pos, state.setValue(POWER, signal), 2);
			this.notifyNeighbors(level, pos);
		}
	}

	// ==================== 六面输入读取 ====================

	/** 读取 face 方向邻居给本方块的信号；face = 本方块→邻居。 */
	private int readFace(Level level, BlockPos pos, Direction face) {
		BlockPos neighborPos = pos.relative(face);
		int signal = level.getSignal(neighborPos, face);
		if (signal >= 15) {
			return signal;
		}
		// 原版粉在输入面不朝本方块时不返回信号，需直接读 POWER（同竖直二极管输入逻辑）
		BlockState neighborState = level.getBlockState(neighborPos);
		if (neighborState.is(Blocks.REDSTONE_WIRE)) {
			return Math.max(signal, neighborState.getValue(RedStoneWireBlock.POWER));
		}
		return signal;
	}

	private boolean anyFaceOn(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (this.readFace(level, pos, direction) > 0) {
				return true;
			}
		}
		return false;
	}

	private boolean allFacesOff(Level level, BlockPos pos) {
		for (Direction direction : Direction.values()) {
			if (this.readFace(level, pos, direction) > 0) {
				return false;
			}
		}
		return true;
	}

	// ==================== 通知 ====================

	private void notifyNeighbors(Level level, BlockPos pos) {
		level.updateNeighborsAt(pos, this);
		for (Direction direction : Direction.values()) {
			level.updateNeighborsAt(pos.relative(direction), this);
		}
	}

	@Nullable
	private OneWayTransmitterBlockEntity blockEntity(BlockGetter level, BlockPos pos) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity instanceof OneWayTransmitterBlockEntity transmitter ? transmitter : null;
	}
}
