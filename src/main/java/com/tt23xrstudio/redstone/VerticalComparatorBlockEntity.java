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
 * 竖直比较器输出值 BlockEntity（复刻 26.2 ComparatorBlockEntity：OutputSignal 持久化）。
 * 类型注册必须在方块注册之后调用（类型的有效方块集合依赖方块实例）。
 */
public class VerticalComparatorBlockEntity extends BlockEntity {
	private static final String OUTPUT_KEY = "OutputSignal";
	private static final int DEFAULT_OUTPUT = 0;

	/** 方块注册流程中由 MtvBlocks 赋值，运行期只读。 */
	private static BlockEntityType<VerticalComparatorBlockEntity> type;

	private int output = DEFAULT_OUTPUT;

	public VerticalComparatorBlockEntity(BlockPos worldPosition, BlockState blockState) {
		super(type, worldPosition, blockState);
	}

	/** MtvBlocks 注册完 vertical_comparator 方块后调用（26.2：直接 new BlockEntityType + 注册）。 */
	public static void registerType(Block comparatorBlock) {
		ResourceKey<BlockEntityType<?>> key = ResourceKey.create(
				Registries.BLOCK_ENTITY_TYPE, MoreThingsVanilla.id("vertical_comparator"));
		type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key,
				new BlockEntityType<>(VerticalComparatorBlockEntity::new, Set.of(comparatorBlock)));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt(OUTPUT_KEY, this.output);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.output = input.getIntOr(OUTPUT_KEY, DEFAULT_OUTPUT);
	}

	public int getOutputSignal() {
		return this.output;
	}

	public void setOutputSignal(int value) {
		this.output = value;
	}
}
