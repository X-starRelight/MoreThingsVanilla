package com.tt23xrstudio.redstone;

import java.util.Set;

import com.tt23xrstudio.MoreThingsVanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 单向传递器状态 BlockEntity：只持久化「等待全灭」标志。
 *
 * 等待全灭期间不输出，也不接受任何新输入，直到 6 面读到的信号全部为 0。
 * 这样可以避免输出侧红石粉需要多 tick 才衰减完的残余信号被误判成新输入，
 * 导致传递器反向自锁。1 tick 仲裁窗口本身由原版方块 tick 队列保存，无需重复持久化。
 */
public class OneWayTransmitterBlockEntity extends BlockEntity {
	private static final String BLOCKED_KEY = "Blocked";

	/** 方块注册流程中由 MtvBlocks 赋值，运行期只读。 */
	private static BlockEntityType<OneWayTransmitterBlockEntity> type;

	private boolean blocked;

	public OneWayTransmitterBlockEntity(BlockPos worldPosition, BlockState blockState) {
		super(type, worldPosition, blockState);
	}

	/** MtvBlocks 注册完 one_way_transmitter 方块后调用。 */
	public static void registerType(Block transmitterBlock) {
		ResourceKey<BlockEntityType<?>> key = ResourceKey.create(
				Registries.BLOCK_ENTITY_TYPE, MoreThingsVanilla.id("one_way_transmitter"));
		type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key,
				new BlockEntityType<>(OneWayTransmitterBlockEntity::new, Set.of(transmitterBlock)));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putBoolean(BLOCKED_KEY, this.blocked);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.blocked = input.getBooleanOr(BLOCKED_KEY, false);
	}

	public boolean isBlocked() {
		return this.blocked;
	}

	public void setBlocked(boolean blocked) {
		if (this.blocked != blocked) {
			this.blocked = blocked;
			this.setChanged();
		}
	}
}
