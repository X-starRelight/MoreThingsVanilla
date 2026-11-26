package com.tt23xrstudio.client.endbox;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tt23xrstudio.endbox.EndBoxBlockEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 末地盒渲染器。
 *
 * <p>几何形状完全来自 {@code blockstates/end_box.json → models/block/end_box.json}，
 * 这里只把模型烘焙出的四边形交给原版 {@link RenderTypes#endPortal()} 绘制，
 * 因此六个面都使用原版末地传送门的动态星空，而不是模型自带的贴图。
 */
public final class EndBoxRenderer implements BlockEntityRenderer<EndBoxBlockEntity, EndBoxRenderer.State> {
	/** 模型没有随机变体，固定种子即可。 */
	private static final RandomSource RANDOM = RandomSource.create(0L);

	/** 渲染线程单线程调用，复用一个列表避免每帧分配。 */
	private final List<BlockStateModelPart> parts = new ArrayList<>();

	public static void register() {
		// 26.2：BlockEntityRenderers.register 由 fabric-transitive-access-wideners-v1 放宽为 public
		BlockEntityRenderers.register(EndBoxBlockEntity.type(), context -> new EndBoxRenderer());
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(EndBoxBlockEntity entity, State state, float partialTick,
			Vec3 cameraPos, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTick, cameraPos, breakProgress);
		state.blockState = entity.getBlockState();
		state.facesToShow.clear();
		for (Direction direction : Direction.values()) {
			if (entity.shouldRenderFace(direction)) {
				state.facesToShow.add(direction);
			}
		}
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.facesToShow.isEmpty() || state.blockState == null) {
			return;
		}

		BlockStateModelSet models = Minecraft.getInstance().getModelManager().getBlockStateModelSet();
		if (models == null) {
			return;
		}

		BlockStateModel model = models.get(state.blockState);
		this.parts.clear();
		model.collectParts(RANDOM, this.parts);
		if (this.parts.isEmpty()) {
			return;
		}

		// 提交的几何可能延后执行，不能捕获可复用的 parts 列表，先复制成局部列表。
		List<BakedQuad> quads = new ArrayList<>(6);
		for (Direction direction : state.facesToShow) {
			for (BlockStateModelPart part : this.parts) {
				for (BakedQuad quad : part.getQuads(direction)) {
					if (quad != null) {
						quads.add(quad);
					}
				}
			}
		}
		if (quads.isEmpty()) {
			return;
		}

		collector.submitCustomGeometry(poseStack, RenderTypes.endPortal(), (pose, vertices) -> {
			for (BakedQuad quad : quads) {
				for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
					vertices.addVertex(pose, quad.position(vertex));
				}
			}
		});
	}

	/** 额外保存方块状态，submit 时据此取出 JSON 烘焙的模型。 */
	public static final class State extends EndPortalRenderState {
		public BlockState blockState;
	}
}
