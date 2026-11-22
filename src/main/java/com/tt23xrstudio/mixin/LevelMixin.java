package com.tt23xrstudio.mixin;

import com.tt23xrstudio.redstone.VerticalComparatorBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 的 Level.updateNeighbourForOutputSignal 只硬编码通知 Blocks.COMPARATOR（原版比较器），
 * 而且只扫水平 4 向（第二个分支也只隔一格导体看水平方向）。
 * 竖直比较器的输入面可能是上/下（容器、物品展示框检测），因此：
 * - 容器内容变化、物品展示框转动时，原版流程永远不会通知到竖直比较器；
 * - 结果就是"检测模式不会自动变更输出的红石信号"。
 *
 * 这里在 TAIL 追加扫描全部 6 向（以及隔一格导体的模拟输出），
 * 只对 VerticalComparatorBlock 调 neighborChanged，不改变原版比较器的行为。
 */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "updateNeighbourForOutputSignal", at = @At("TAIL"))
	private void mtv$notifyVerticalComparators(BlockPos pos, Block sourceBlock, CallbackInfo ci) {
		Level level = (Level) (Object) this;
		for (Direction direction : Direction.values()) {
			BlockPos neighborPos = pos.relative(direction);
			if (!level.hasChunk(
					neighborPos.getX() >> 4,
					neighborPos.getZ() >> 4
			)) {
				continue;
			}
			BlockState neighborState = level.getBlockState(neighborPos);
			if (neighborState.getBlock() instanceof VerticalComparatorBlock) {
				level.neighborChanged(neighborState, neighborPos, sourceBlock, null, false);
			} else if (neighborState.isRedstoneConductor(level, neighborPos)) {
				// 与原版一致：模拟输出可穿过一格导体被比较器读取
				BlockPos farPos = neighborPos.relative(direction);
				if (!level.hasChunk(
						farPos.getX() >> 4,
						farPos.getZ() >> 4
				)) {
					continue;
				}
				BlockState farState = level.getBlockState(farPos);
				if (farState.getBlock() instanceof VerticalComparatorBlock) {
					level.neighborChanged(farState, farPos, sourceBlock, null, false);
				}
			}
		}
	}
}
