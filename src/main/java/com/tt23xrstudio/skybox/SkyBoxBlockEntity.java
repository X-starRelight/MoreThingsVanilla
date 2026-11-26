package com.tt23xrstudio.skybox;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.tt23xrstudio.MoreThingsVanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 天空盒方块实体。
 *
 * <p>不保存任何世界数据，只承担两件事：
 * <ul>
 *     <li>维护客户端已加载的天空盒集合，渲染器据此收集可见方块；</li>
 *     <li>缓存六面是否需要绘制，相邻的同类方块之间剔除内部面（2×2×2 只画外壳）。</li>
 * </ul>
 */
public class SkyBoxBlockEntity extends BlockEntity {
	/** 客户端已加载的天空盒；渲染线程遍历，主线程增删。 */
	private static final Set<SkyBoxBlockEntity> CLIENT_ACTIVE =
			Collections.newSetFromMap(new ConcurrentHashMap<>());

	/** 方块注册流程中由 MtvBlocks 赋值，运行期只读。 */
	private static BlockEntityType<SkyBoxBlockEntity> type;

	/** null = 尚未计算，下次 shouldRenderFace 时再查邻居。 */
	private final Boolean[] shouldRender = new Boolean[6];

	public SkyBoxBlockEntity(BlockPos worldPosition, BlockState blockState) {
		super(type, worldPosition, blockState);
	}

	/** MtvBlocks 注册完 sky_box 方块后调用。 */
	public static void registerType(Block block) {
		ResourceKey<BlockEntityType<?>> key = ResourceKey.create(
				Registries.BLOCK_ENTITY_TYPE, MoreThingsVanilla.id("sky_box"));
		type = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, key,
				new BlockEntityType<>(SkyBoxBlockEntity::new, Set.of(block)));
	}

	public static Set<SkyBoxBlockEntity> getClientActive() {
		return CLIENT_ACTIVE;
	}

	/** 相邻方块也是天空盒时隐藏该面，避免内部面互相重叠。 */
	public boolean shouldRenderFace(Direction direction) {
		int index = direction.ordinal();
		Boolean cached = this.shouldRender[index];
		if (cached == null) {
			cached = this.computeShouldRenderFace(direction);
			this.shouldRender[index] = cached;
		}
		return cached;
	}

	private boolean computeShouldRenderFace(Direction direction) {
		if (this.level == null) {
			return true;
		}

		BlockPos neighbourPos = this.getBlockPos().relative(direction);
		BlockState neighbourState = this.level.getBlockState(neighbourPos);
		return neighbourState.getBlock() != this.getBlockState().getBlock();
	}

	/** 邻居或自身状态变化时清空六面缓存。 */
	public void invalidateFaces() {
		Arrays.fill(this.shouldRender, null);
	}

	@Override
	public void setLevel(Level level) {
		super.setLevel(level);
		this.invalidateFaces();
		if (level.isClientSide()) {
			CLIENT_ACTIVE.add(this);
		}
	}

	@Override
	public void setRemoved() {
		super.setRemoved();
		CLIENT_ACTIVE.remove(this);
	}

	@Override
	public void clearRemoved() {
		super.clearRemoved();
		if (this.level != null && this.level.isClientSide()) {
			CLIENT_ACTIVE.add(this);
		}
	}
}
