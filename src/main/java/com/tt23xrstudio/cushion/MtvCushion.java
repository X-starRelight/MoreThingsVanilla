package com.tt23xrstudio.cushion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 坐垫实体（复刻 26.4 Cushion）：附着方块顶面、可骑乘、按色掉落。
 * 26.2 适配：
 * - 无 DYE_COLOR 序列化器 → BYTE + DyeColor.byId/getId
 * - 父类无 tickAtCheckInterval 钩子 → 覆写 tick 自带 100t 计数器
 * - 无 level.findBlocksIn → BlockPos.betweenClosed 循环
 * - 无 AABB.nextDeflated → 手写 ULP 收缩
 * - 无 kill(level, source) → kill(level)（26.2 无 lastDamageSource）
 * - 无 CUSHION_COLOR 组件 → 颜色由物品显式传递（MtvCushionItem.setColor）
 */
public class MtvCushion extends BlockAttachedEntity {
	private static final DyeColor DEFAULT_COLOR = DyeColor.WHITE;
	private static final int LIGHTNING_DROP_INVULNERABLE_TICKS = 20;
	private static final int CHECK_INTERVAL = 100;
	private static final EntityDataAccessor<Byte> DATA_COLOR =
			SynchedEntityData.defineId(MtvCushion.class, EntityDataSerializers.BYTE);

	/** 100t 周期检查计数（26.2 父类字段私有且无钩子，自带一份）。 */
	private int checkInterval;

	/** 流体进入效果收集器（26.4 Entity 内建字段，26.2 自建同型）。 */
	private final InsideBlockEffectApplier.StepBasedCollector insideEffectCollector =
			new InsideBlockEffectApplier.StepBasedCollector();

	public MtvCushion(EntityType<MtvCushion> type, Level level) {
		super(type, level);
	}

	public DyeColor getColor() {
		return DyeColor.byId(this.entityData.get(DATA_COLOR));
	}

	public void setColor(DyeColor color) {
		this.entityData.set(DATA_COLOR, (byte) color.getId());
	}

	@Override
	public void dropItem(ServerLevel level, Entity causedBy) {
		this.playSound(MtvCushions.CUSHION_BREAK, 1.0F, 1.0F);
		this.showBreakingParticles();
		if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
			if (!(causedBy instanceof Player player && player.hasInfiniteMaterials())) {
				ItemEntity itemEntity = this.spawnAtLocation(level, this.getCushionItemStackWithData());
				if (itemEntity != null && causedBy instanceof LightningBolt) {
					// 26.4：闪电掉落物 20t 无敌（26.2 无 setInvulnerableTime → 公开字段）
					itemEntity.invulnerableTime = LIGHTNING_DROP_INVULNERABLE_TICKS;
				}
			}
		}
	}

	private static boolean isBreakingDeniedFor(DamageSource source) {
		return source.getEntity() instanceof Player player && !player.mayBuild();
	}

	private boolean isBreakingDeniedAtPosFor(ServerLevel level, DamageSource source) {
		return source.getEntity() instanceof Player player && !level.mayInteract(player, this.pos);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return !isBreakingDeniedFor(source) && !isBreakingDeniedAtPosFor(level, source)
				? super.hurtServer(level, source, damage) : false;
	}

	@Override
	public boolean hurtClient(DamageSource source) {
		return isBreakingDeniedFor(source) ? false : super.hurtClient(source);
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isSecondaryUseActive() || this.isVehicle()) {
			return InteractionResult.PASS;
		} else if (!this.level().isClientSide() && player.startRiding(this)) {
			this.playSound(MtvCushions.CUSHION_SIT, 1.0F, 1.0F);
			return InteractionResult.SUCCESS_SERVER;
		} else {
			return InteractionResult.CONSUME;
		}
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);
		if (!this.level().isClientSide() && this.getRemovalReason() == null) {
			this.playSound(MtvCushions.CUSHION_GET_UP, 1.0F, 1.0F);
		}
	}

	@Override
	public ItemStack getPickResult() {
		return new ItemStack(MtvCushions.item(this.getColor()));
	}

	@Override
	public void tick() {
		if (this.level() instanceof ServerLevel level) {
			this.checkBelowWorld();
			if (this.checkInterval++ == CHECK_INTERVAL) {
				this.checkInterval = 0;
				this.tickAtCheckInterval(level);
				if (!this.isRemoved() && !this.survives()) {
					this.discard();
					this.dropItem(level, null);
				}
			}
		}
	}

	/** 26.4 Cushion.tickAtCheckInterval：流体进入效果 + 火焰销毁（26.2 父类无此钩子）。 */
	private void tickAtCheckInterval(ServerLevel level) {
		BlockPos blockPos = this.blockPosition();
		FluidState fluidState = level.getBlockState(blockPos).getFluidState();
		if (this.collidedWithFluid(fluidState, blockPos, this.position(), this.position())) {
			fluidState.entityInside(level, blockPos, this, this.insideEffectCollector);
			this.insideEffectCollector.applyAndClear(this);
		}
		this.destroyIfInFire(level);
	}

	/** 下一膨胀盒内有火焰即以 inFire 伤害自毁一次（26.4：findBlocksIn(nextDeflated) 找到即中止）。 */
	public void destroyIfInFire(ServerLevel level) {
		if (!this.isRemoved()) {
			for (BlockPos blockPos : BlockPos.betweenClosed(nextDeflated(this.getBoundingBox()))) {
				if (level.getBlockState(blockPos).is(BlockTags.FIRE)) {
					this.hurtServer(level, this.damageSources().inFire(), 1.0F);
					break;
				}
			}
		}
	}

	@Override
	public void thunderHit(ServerLevel level, LightningBolt lightningBolt) {
		if (!this.isRemoved()) {
			// 26.4 kill(level, lightningBolt())；26.2 无 kill(level, source) → kill(level)
			this.kill(level);
			this.dropItem(level, lightningBolt);
		}
	}

	@Override
	public void setPos(double x, double y, double z) {
		// 父类 setPos 只更新 blockPos/碰撞箱，不更新实体坐标 → 先 setPosRaw（26.4 同款）
		this.setPosRaw(x, y, z);
		super.setPos(x, y, z);
	}

	private void showBreakingParticles() {
		if (this.level() instanceof ServerLevel level) {
			level.sendParticles(
					new BlockParticleOption(ParticleTypes.BLOCK, Blocks.WOOL.pick(this.getColor()).defaultBlockState()),
					this.getX(),
					this.getY(0.6666666666666666),
					this.getZ(),
					10,
					this.getBbWidth() / 4.0F,
					this.getBbHeight() / 4.0F,
					this.getBbWidth() / 4.0F,
					0.05
			);
		}
	}

	// ==================== 放置校验（26.4 静态方法组逐条适配） ====================

	public static boolean canBePlacedAt(Level level, AABB boundingBox) {
		return wouldSurviveAt(level, boundingBox) && !isAnchorBuried(level, boundingBox);
	}

	public static boolean wouldSurviveAt(Level level, AABB boundingBox) {
		return hasAnchorBelow(level, boundingBox) && !isCoveredBySuffocatingBlocks(level, boundingBox);
	}

	private static boolean hasAnchorBelow(Level level, AABB boundingBox) {
		AABB anchorBox = new AABB(
				boundingBox.minX, boundingBox.minY - 0.015625, boundingBox.minZ,
				Math.nextDown(boundingBox.maxX), boundingBox.minY, Math.nextDown(boundingBox.maxZ)
		);
		// 26.4 findBlocksIn(searchBox).forEachUntil(相交即 ABORT) → 找到即 true
		AABB searchBox = anchorBox.expandTowards(0.0, -0.125, 0.0);
		for (BlockPos blockPos : BlockPos.betweenClosed(searchBox)) {
			BlockState blockState = level.getBlockState(blockPos);
			VoxelShape shape = blockState.getShape(level, blockPos);
			if (!shape.isEmpty() && shape.bounds().move(blockPos).intersects(anchorBox)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isAnchorBuried(Level level, AABB boundingBox) {
		AABB restingSlice = nextDeflated(new AABB(
				boundingBox.minX, boundingBox.minY, boundingBox.minZ,
				boundingBox.maxX, boundingBox.minY + 0.015625, boundingBox.maxZ
		));
		VoxelShape exposedSurface = Shapes.create(restingSlice);
		for (VoxelShape collider : level.getBlockCollisions(null, restingSlice)) {
			exposedSurface = Shapes.join(exposedSurface, collider, BooleanOp.ONLY_FIRST);
			if (exposedSurface.isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static boolean isCoveredBySuffocatingBlocks(Level level, AABB boundingBox) {
		for (BlockPos blockPos : BlockPos.betweenClosed(nextDeflated(boundingBox))) {
			if (!level.getBlockState(blockPos).isSuffocating(level, blockPos)) {
				return false;
			}
		}
		return true;
	}

	/** 26.4 AABB.nextDeflated（26.2 无此方法）：各轴向内收缩 1 ULP。 */
	private static AABB nextDeflated(AABB box) {
		return new AABB(
				Math.nextUp(box.minX), Math.nextUp(box.minY), Math.nextUp(box.minZ),
				Math.nextDown(box.maxX), Math.nextDown(box.maxY), Math.nextDown(box.maxZ)
		);
	}

	// ==================== Entity 生命周期（26.4 同款） ====================

	@Override
	public boolean survives() {
		return wouldSurviveAt(this.level(), this.getBoundingBox());
	}

	@Override
	protected void recalculateBoundingBox() {
		this.setBoundingBox(this.makeBoundingBox());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_COLOR, (byte) DEFAULT_COLOR.getId());
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("color", DyeColor.CODEC, this.getColor());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setColor(input.read("color", DyeColor.CODEC).orElse(DEFAULT_COLOR));
	}

	private ItemStack getCushionItemStackWithData() {
		ItemStack itemStack = new ItemStack(MtvCushions.item(this.getColor()));
		if (this.getCustomName() != null) {
			itemStack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
		}
		return itemStack;
	}
}
