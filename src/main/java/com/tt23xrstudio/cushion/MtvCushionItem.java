package com.tt23xrstudio.cushion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PostSpawnProcessor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 坐垫物品（复刻 26.4 CushionItem.useOn）：点击方块顶面放置坐垫实体。
 * 26.2 无 CUSHION_COLOR 组件 → 本类持有 DyeColor 字段，创建实体后显式 setColor。
 */
public class MtvCushionItem extends Item {
	/** 特殊碰撞形状方块的射线校正 epsilon（26.4 COLLISION_SHAPE_RAYCAST_EPSILON）。 */
	private static final double COLLISION_SHAPE_RAYCAST_EPSILON = 0.001;

	/** 26.4 BlockTags.CUSHION_USES_COLLISION_SHAPE（26.2 无此标签 → 自建同 id，data/minecraft 提供内容）。 */
	private static final TagKey<Block> CUSHION_USES_COLLISION_SHAPE = TagKey.create(Registries.BLOCK,
			Identifier.fromNamespaceAndPath("minecraft", "cushion_uses_collision_shape"));

	private final DyeColor color;

	public MtvCushionItem(DyeColor color, Properties properties) {
		super(properties);
		this.color = color;
	}

	/** 本物品对应颜色（放置实体时显式传递）。 */
	public DyeColor getColor() {
		return this.color;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		UseOnContext recalculatedContext = recalculateContextForSpecialCollisionShapes(context);
		Level level = context.getLevel();
		Direction clickedFace = recalculatedContext.getClickedFace();
		if (clickedFace != Direction.UP) {
			return InteractionResult.FAIL;
		}

		BlockPlaceContext placeContext = new BlockPlaceContext(recalculatedContext);
		BlockPos blockPos = placeContext.getClickedPos();
		// 26.4 Vec3.atCenterOfWithY(blockPos, clickY)（26.2 无此方法 → 手写）
		Vec3 clickLocation = recalculatedContext.getClickLocation();
		Vec3 entityPos = new Vec3(blockPos.getX() + 0.5, clickLocation.y, blockPos.getZ() + 0.5);
		AABB spawnAABB = MtvCushions.CUSHION.getSpawnAABB(entityPos.x, entityPos.y, entityPos.z);
		if (!MtvCushion.canBePlacedAt(level, spawnAABB)) {
			return InteractionResult.FAIL;
		}

		ItemStack itemStack = context.getItemInHand();
		if (level instanceof ServerLevel serverLevel) {
			if (!serverLevel.getEntitiesOfClass(MtvCushion.class, spawnAABB).isEmpty()) {
				return InteractionResult.FAIL;
			}

			PostSpawnProcessor<MtvCushion> entityConfig =
					EntityType.createDefaultStackConfig(serverLevel, itemStack, context.getPlayer());
			MtvCushion cushion = MtvCushions.CUSHION.create(serverLevel, entityConfig, blockPos,
					EntitySpawnReason.SPAWN_ITEM_USE, true, true);
			if (cushion == null) {
				return InteractionResult.FAIL;
			}

			cushion.setColor(this.color);
			cushion.snapTo(entityPos, Direction.fromYRot(placeContext.getRotation()).toYRot(), 0.0F);
			serverLevel.addFreshEntity(cushion);
			cushion.destroyIfInFire(serverLevel);
			level.playSound(null, cushion.getX(), cushion.getY(), cushion.getZ(),
					MtvCushions.CUSHION_PLACE, SoundSource.BLOCKS, 0.75F, 0.8F);
			cushion.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
			itemStack.consume(1, placeContext.getPlayer());
		}

		return InteractionResult.SUCCESS;
	}

	/** 碰撞形状≠可视形状的方块（漏斗/堆肥桶/炼药锅）：用碰撞箱重算点击位置（26.4 同款）。 */
	private static UseOnContext recalculateContextForSpecialCollisionShapes(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return context;
		}

		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		BlockState clickedState = level.getBlockState(clickedPos);
		if (!clickedState.is(CUSHION_USES_COLLISION_SHAPE)) {
			return context;
		}

		Vec3 rayFrom = player.getEyePosition();
		Vec3 ray = context.getClickLocation().subtract(rayFrom);
		Vec3 rayTo = context.getClickLocation().add(ray.normalize().scale(COLLISION_SHAPE_RAYCAST_EPSILON));
		BlockHitResult collisionHitResult = clickedState.getCollisionShape(level, clickedPos).clip(rayFrom, rayTo, clickedPos);
		return collisionHitResult == null ? context : new UseOnContext(player, context.getHand(), collisionHitResult);
	}
}
