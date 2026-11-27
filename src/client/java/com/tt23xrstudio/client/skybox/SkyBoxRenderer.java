package com.tt23xrstudio.client.skybox;

import java.util.Optional;
import java.util.OptionalDouble;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.tt23xrstudio.MoreThingsVanilla;
import com.tt23xrstudio.skybox.SkyBoxBlockEntity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

/** 保留天空 pass 已经绘制的颜色，只在方块覆盖的像素上暂时阻挡后方地形。 */
public final class SkyBoxRenderer {
	private static final RenderPipeline MASK = pipeline(false);
	private static final RenderPipeline RESTORE = pipeline(true);
	private static final StagedVertexBuffer BUFFER = new StagedVertexBuffer(() -> "Sky box depth mask", RenderType.SMALL_BUFFER_SIZE);
	private static final Vector4f WHITE = new Vector4f(1.0f);
	private static final Vector3f ZERO = new Vector3f();
	private static final Matrix4f IDENTITY = new Matrix4f();
	private static StagedVertexBuffer.ExecuteInfo geometry;
	private static GpuBufferSlice transform;
	private static GpuTextureView maskedDepth;
	private static GpuTexture snapshot;
	private static GpuTextureView snapshotView;

	private SkyBoxRenderer() {}

	private static RenderPipeline pipeline(boolean restore) {
		var shader = MoreThingsVanilla.id("core/sky_box");
		var builder = RenderPipeline.builder()
				.withLocation(MoreThingsVanilla.id(restore ? "pipeline/sky_box_restore" : "pipeline/sky_box_mask"))
				.withVertexShader(shader).withFragmentShader(shader)
				.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
				.withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.QUADS)
				.withColorTargetState(new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE))
				.withDepthStencilState(new DepthStencilState(restore ? CompareOp.ALWAYS_PASS : CompareOp.GREATER_THAN_OR_EQUAL, true))
				.withCull(false);
		if (restore) builder.withShaderDefine("RESTORE_SKY_DEPTH")
				.withBindGroupLayout(BindGroupLayout.builder().withSampler("SceneDepth").build());
		return RenderPipelines.register(builder.build());
	}

	public static void register() {
		LevelRenderEvents.START_MAIN.register(SkyBoxRenderer::mask);
		// Before fabulous copies depth and Iris starts its deferred sky pass.
		LevelRenderEvents.AFTER_SOLID_FEATURES.register(ctx -> restore(ctx.gameRenderer().mainRenderTarget()));
		// Iris has finished deferred lighting at this point. Hide rear water and glass
		// during terrain rendering, without leaving surface depth for the clouds pass.
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(ctx -> {
			RenderTarget target = ctx.levelRenderer().translucentTarget();
			if (target == null) target = ctx.gameRenderer().mainRenderTarget();
			if (geometry != null && usable(target)) {
				maskedDepth = target.getDepthTextureView();
				draw(target, MASK);
			} else maskedDepth = null;
		});
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ctx -> {
			RenderTarget target = ctx.levelRenderer().translucentTarget();
			restore(target != null ? target : ctx.gameRenderer().mainRenderTarget());
		});
		LevelRenderEvents.END_MAIN.register(ctx -> {
			geometry = null;
			maskedDepth = null;
			transform = null;
			BUFFER.endFrame();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { closeSnapshot(); BUFFER.close(); });
	}

	private static boolean usable(RenderTarget target) {
		var depth = target.getDepthTextureView();
		var color = target.getColorTextureView();
		return depth != null && color != null && color.texture().getFormat() == GpuFormat.RGBA8_UNORM
				&& (depth.texture().usage() & GpuTexture.USAGE_COPY_SRC) != 0;
	}

	private static void mask(LevelTerrainRenderContext ctx) {
		geometry = null;
		RenderTarget target = ctx.gameRenderer().mainRenderTarget();
		if (!usable(target)) return;
		var camera = ctx.levelState().cameraRenderState;
		var level = Minecraft.getInstance().level;
		StagedVertexBuffer.Draw batch = null;
		VertexConsumer out = null;
		for (var box : SkyBoxBlockEntity.getClientActive()) {
			if (box.isRemoved() || box.getLevel() != level) continue;
			var pos = box.getBlockPos();
			if (camera.cullFrustum != null && !camera.cullFrustum.isVisible(new AABB(pos))) continue;
			float x = (float)(pos.getX() - camera.pos.x), y = (float)(pos.getY() - camera.pos.y), z = (float)(pos.getZ() - camera.pos.z);
			for (Direction face : Direction.values()) {
				if (!box.shouldRenderFace(face)) continue;
				if (batch == null) {
					batch = BUFFER.appendDraw(DefaultVertexFormat.POSITION, PrimitiveTopology.QUADS);
					out = BUFFER.getVertexBuilder(batch);
				}
				addFace(out, face, x, y, z);
			}
		}
		if (batch == null) return;
		BUFFER.upload();
		geometry = BUFFER.getExecuteInfo(batch);
		if (geometry == null) return;
		maskedDepth = target.getDepthTextureView();
		transform = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), WHITE, ZERO, IDENTITY);
		draw(target, MASK);
	}

	private static void restore(RenderTarget target) {
		if (geometry == null || maskedDepth == null || target.getDepthTextureView() != maskedDepth) return;
		GpuTexture source = maskedDepth.texture();
		int w = source.getWidth(0), h = source.getHeight(0);
		if (snapshot == null || snapshot.getWidth(0) != w || snapshot.getHeight(0) != h || snapshot.getFormat() != source.getFormat()) {
			closeSnapshot();
			snapshot = RenderSystem.getDevice().createTexture("Sky box scene depth", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
					source.getFormat(), w, h, 1, 1);
			snapshotView = RenderSystem.getDevice().createTextureView(snapshot);
		}
		RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source, snapshot, 0, 0, 0, 0, 0, w, h);
		draw(target, RESTORE);
	}

	private static void draw(RenderTarget target, RenderPipeline pipeline) {
		try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Sky box depth",
				target.getColorTextureView(), Optional.empty(), maskedDepth, OptionalDouble.empty())) {
			pass.setPipeline(pipeline);
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("DynamicTransforms", transform);
			if (pipeline == RESTORE) pass.bindTexture("SceneDepth", snapshotView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
			pass.setVertexBuffer(0, geometry.vertexBuffer().slice());
			pass.setIndexBuffer(geometry.indexBuffer(), geometry.indexType());
			pass.drawIndexed(geometry.indexCount(), 1, geometry.firstIndex(), geometry.baseVertex(), 0);
		}
	}

	private static void addFace(VertexConsumer out, Direction face, float x, float y, float z) {
		for (int i = 0; i < 4; i++) {
			int u = (i == 1 || i == 2) ? 1 : 0, v = i >= 2 ? 1 : 0;
			switch (face.getAxis()) {
				case X -> out.addVertex(x + (face == Direction.EAST ? 1 : 0), y + u, z + v);
				case Y -> out.addVertex(x + u, y + (face == Direction.UP ? 1 : 0), z + v);
				case Z -> out.addVertex(x + u, y + v, z + (face == Direction.SOUTH ? 1 : 0));
			}
		}
	}

	private static void closeSnapshot() {
		if (snapshotView != null) snapshotView.close();
		if (snapshot != null) snapshot.close();
		snapshotView = null;
		snapshot = null;
	}
}
