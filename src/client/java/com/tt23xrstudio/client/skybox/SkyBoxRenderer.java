package com.tt23xrstudio.client.skybox;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

import com.tt23xrstudio.client.mixin.SkyRendererAccessor;
import com.tt23xrstudio.skybox.SkyBoxBlockEntity;

/**
 * 基础款 Block of Sky 渲染器。
 *
 * <p>每帧在 {@code START_MAIN}（原版天空已经画完、不透明地形尚未画）时，用原版
 * {@link SkyRenderer} 把当前天空分别以 90° FOV 渲染到六张 512×512 纹理。六个
 * 方向依次是 +X、-X、+Y、-Y、+Z、-Z；云层通过 {@link CloudRenderer} 复用原版
 * 网格，并由客户端 Mixin 临时重定向到当前面。下界等无天空维度改用当前雾色填充。
 *
 * <p>随后在 {@code AFTER_OPAQUE_TERRAIN} 用 sky_box 管线把每个天空盒的外露面
 * 画到主目标。所有方块沿相机到片元的视线采样同一片天空。
 */
public final class SkyBoxRenderer {
	private static final String MOD_ID = "more-things-vanilla";

	private static final int FACE_SIZE = 512;
	private static final float FACE_FOV_DEGREES = 90.0F;

	private static final int FACE_POS_X = 0;
	private static final int FACE_NEG_X = 1;
	private static final int FACE_POS_Y = 2;
	private static final int FACE_NEG_Y = 3;
	private static final int FACE_POS_Z = 4;
	private static final int FACE_NEG_Z = 5;

	/** 六个采样器对应六张面纹理，顺序与捕获顺序、着色器分支一致。 */
	private static final BindGroupLayout FACE_SAMPLERS = BindGroupLayout.builder()
			.withSampler("Sampler0")
			.withSampler("Sampler1")
			.withSampler("Sampler2")
			.withSampler("Sampler3")
			.withSampler("Sampler4")
			.withSampler("Sampler5")
			.build();

	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder()
					.withLocation(Identifier.fromNamespaceAndPath(MOD_ID, "pipeline/sky_box"))
					.withVertexShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/sky_box"))
					.withFragmentShader(Identifier.fromNamespaceAndPath(MOD_ID, "core/sky_box"))
					.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
					.withBindGroupLayout(FACE_SAMPLERS)
					.withVertexBinding(0, DefaultVertexFormat.POSITION)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
					.withCull(false)
					.build()
	);

	private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
	private static final Vector3f ZERO = new Vector3f();
	private static final Matrix4f IDENTITY_TEXTURE_MATRIX = new Matrix4f();

	private static final StagedVertexBuffer VERTEX_BUFFER =
			new StagedVertexBuffer(() -> "MoreThingsVanilla SkyBox", RenderType.SMALL_BUFFER_SIZE);

	private static final Quaternionf[] FACE_ROTATIONS = new Quaternionf[6];
	private static final Vector3f[] FACE_NORMALS = new Vector3f[6];
	private static final Vector3f[] FACE_TANGENTS = new Vector3f[6];
	private static final Vector3f[] FACE_BITANGENTS = new Vector3f[6];

	static {
		// 捕获方向：相机默认朝 -Z，把对应世界轴旋转到 -Z 即可。
		FACE_ROTATIONS[FACE_POS_X] = new Quaternionf().rotationY((float) (Math.PI / 2.0));
		FACE_ROTATIONS[FACE_NEG_X] = new Quaternionf().rotationY((float) (-Math.PI / 2.0));
		FACE_ROTATIONS[FACE_POS_Y] = new Quaternionf().rotationX((float) (-Math.PI / 2.0));
		FACE_ROTATIONS[FACE_NEG_Y] = new Quaternionf().rotationX((float) (Math.PI / 2.0));
		FACE_ROTATIONS[FACE_POS_Z] = new Quaternionf().rotationY((float) Math.PI);
		FACE_ROTATIONS[FACE_NEG_Z] = new Quaternionf();

		// 每面的法线 / 切线 / 副切线，与 sky_box.fsh 的投影公式一一对应。
		setFaceAxes(Direction.EAST, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), new Vector3f(0, 1, 0));
		setFaceAxes(Direction.WEST, new Vector3f(-1, 0, 0), new Vector3f(0, 0, -1), new Vector3f(0, 1, 0));
		setFaceAxes(Direction.UP, new Vector3f(0, 1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, 1));
		setFaceAxes(Direction.DOWN, new Vector3f(0, -1, 0), new Vector3f(1, 0, 0), new Vector3f(0, 0, -1));
		setFaceAxes(Direction.SOUTH, new Vector3f(0, 0, 1), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0));
		setFaceAxes(Direction.NORTH, new Vector3f(0, 0, -1), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0));
	}

	private static TextureTarget[] faces;
	private static ProjectionMatrixBuffer projectionBuffer;
	private static GpuSampler faceSampler;
	private static final Matrix4f FACE_PROJECTION = new Matrix4f();

	/** 云层捕获期间由 Mixin 读取；非捕获期间必须为 null。 */
	private static RenderTarget cloudCaptureTarget;

	/** 本帧是否已准备好天空纹理；没有天空盒或捕获未完成时为 false，此时不绘制。 */
	private static boolean skyCapturedThisFrame;

	private static StagedVertexBuffer.Draw currentDraw;

	private SkyBoxRenderer() {
	}

	public static void register() {
		LevelRenderEvents.START_MAIN.register(SkyBoxRenderer::captureSkyCube);
		LevelRenderEvents.AFTER_OPAQUE_TERRAIN.register(SkyBoxRenderer::renderSkyBoxes);
		LevelRenderEvents.END_MAIN.register(context -> VERTEX_BUFFER.endFrame());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
	}

	public static RenderTarget getCloudCaptureTarget() {
		return cloudCaptureTarget;
	}

	private static void ensureInitialized() {
		if (faces != null) {
			return;
		}

		TextureTarget[] created = new TextureTarget[6];
		for (int face = 0; face < created.length; face++) {
			created[face] = new TextureTarget(
					"MoreThingsVanilla sky box face " + face,
					FACE_SIZE,
					FACE_SIZE,
					true,
					GpuFormat.RGBA8_UNORM
			);
		}
		faces = created;
		projectionBuffer = new ProjectionMatrixBuffer("more_things_vanilla_sky_box");
		faceSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}

	private static boolean hasClientSkyBoxes(ClientLevel level) {
		for (SkyBoxBlockEntity skyBox : SkyBoxBlockEntity.getClientActive()) {
			if (skyBox.getLevel() == level && !skyBox.isRemoved()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 用原版天空渲染器捕获六面天空（含云）。
	 *
	 * <p>START_MAIN 位于主 pass 内、原版天空 pass 之后，此时原版天空刚写完主目标，
	 * 但主目标颜色仍可被后续地形覆盖。捕获写入独立纹理，不会干扰主目标。
	 */
	private static void captureSkyCube(LevelTerrainRenderContext context) {
		skyCapturedThisFrame = false;

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || !hasClientSkyBoxes(level)) {
			return;
		}

		SkyRenderer skyRenderer = context.levelRenderer().skyRenderer();
		if (skyRenderer == null) {
			return;
		}

		SkyRenderState skyState = context.levelState().skyRenderState;
		// 下界等无天空维度没有日月星辰，但原版背景仍是雾色；方块显示这层雾色天空。
		boolean hasSky = skyState.skybox != DimensionType.Skybox.NONE;

		ensureInitialized();

		var cameraState = context.levelState().cameraRenderState;
		FACE_PROJECTION.setPerspective(
				(float) Math.toRadians(FACE_FOV_DEGREES),
				1.0F,
				cameraState.depthFar,
				0.05F,
				RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
		);

		SkyRendererAccessor skyRendererAccessor = (SkyRendererAccessor) skyRenderer;
		RenderTarget originalTarget = skyRendererAccessor.moreThingsVanilla$getRenderTarget();

		CloudStatus cloudStatus = client.options.getCloudStatus();
		int cloudColor = context.levelState().cloudColor;
		float cloudHeight = context.levelState().cloudHeight;
		long gameTime = context.levelState().gameTime;
		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 cameraPos = cameraState.pos;
		CloudRenderer cloudRenderer = context.levelRenderer().cloudRenderer();
		boolean renderClouds = hasSky && cloudStatus != CloudStatus.OFF && ARGB.alpha(cloudColor) > 0;

		Vector4f clearColor;
		if (!hasSky) {
			// 无天空维度的背景色就是当前雾色（下界会随生物群系变化）。
			clearColor = new Vector4f(cameraState.fogData.color);
		} else if (skyState.skybox == DimensionType.Skybox.END) {
			clearColor = new Vector4f(0.0F, 0.0F, 0.0F, 1.0F);
		} else {
			clearColor = ARGB.vector4fFromARGB32(skyState.skyColor);
		}

		RenderSystem.backupProjectionMatrix();
		try {
			RenderSystem.setProjectionMatrix(
					projectionBuffer.getBuffer(FACE_PROJECTION),
					ProjectionType.PERSPECTIVE
			);

			Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();

			for (int face = 0; face < faces.length; face++) {
				TextureTarget target = faces[face];

				// 26.2 使用反向深度：远处 = 0.0。
				RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
						target.getColorTexture(),
						clearColor,
						target.getDepthTexture(),
						0.0
				);

				modelViewStack.pushMatrix();
				modelViewStack.identity();
				modelViewStack.rotate(FACE_ROTATIONS[face]);

				if (hasSky) {
					// 原版 SkyRenderer 的所有方法都写自身的 renderTarget，临时切面。
					skyRendererAccessor.moreThingsVanilla$setRenderTarget(target);
					try {
						captureFace(skyRenderer, skyState);
					} finally {
						skyRendererAccessor.moreThingsVanilla$setRenderTarget(originalTarget);
					}

					if (renderClouds) {
						captureClouds(
								cloudRenderer,
								target,
								cloudColor,
								cloudStatus,
								cloudHeight,
								cameraPos,
								gameTime,
								partialTick
						);
					}
				}

				modelViewStack.popMatrix();
			}
		} finally {
			cloudCaptureTarget = null;
			RenderSystem.restoreProjectionMatrix();
		}

		skyCapturedThisFrame = true;
	}

	private static void captureFace(SkyRenderer skyRenderer, SkyRenderState skyState) {
		PoseStack poseStack = new PoseStack();

		if (skyState.skybox == DimensionType.Skybox.END) {
			skyRenderer.renderEndSky();
			if (skyState.endFlashIntensity > 1.0E-5F) {
				skyRenderer.renderEndFlash(
						poseStack,
						skyState.endFlashIntensity,
						skyState.endFlashXAngle,
						skyState.endFlashYAngle
				);
			}
			return;
		}

		skyRenderer.renderSkyDisc(skyState.skyColor);
		skyRenderer.renderSunriseAndSunset(poseStack, skyState.sunAngle, skyState.sunriseAndSunsetColor);
		skyRenderer.renderSunMoonAndStars(
				poseStack,
				skyState.sunAngle,
				skyState.moonAngle,
				skyState.starAngle,
				skyState.moonPhase,
				skyState.rainBrightness,
				skyState.starBrightness
		);
		if (skyState.shouldRenderDarkDisc) {
			skyRenderer.renderDarkDisc();
		}
	}

	private static void captureClouds(
			CloudRenderer cloudRenderer,
			RenderTarget target,
			int cloudColor,
			CloudStatus cloudStatus,
			float cloudHeight,
			Vec3 cameraPos,
			long gameTime,
			float partialTick
	) {
		cloudCaptureTarget = target;
		try {
			cloudRenderer.render(
					cloudColor,
					cloudStatus,
					cloudHeight,
					Minecraft.getInstance().options.cloudRange().get(),
					cameraPos,
					gameTime,
					partialTick
			);
		} finally {
			cloudCaptureTarget = null;
		}
	}

	private static void renderSkyBoxes(LevelTerrainRenderContext context) {
		currentDraw = null;

		if (faces == null || !skyCapturedThisFrame) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}

		List<SkyBoxBlockEntity> visible = new ArrayList<>();
		boolean anyFace = false;

		for (SkyBoxBlockEntity skyBox : SkyBoxBlockEntity.getClientActive()) {
			if (skyBox.getLevel() != level || skyBox.isRemoved()) {
				continue;
			}

			boolean hasFace = false;
			for (Direction direction : Direction.values()) {
				if (skyBox.shouldRenderFace(direction)) {
					hasFace = true;
					anyFace = true;
				}
			}

			if (hasFace) {
				visible.add(skyBox);
			}
		}

		if (!anyFace) {
			return;
		}

		VertexFormat format = PIPELINE.getVertexFormatBinding(0);
		PrimitiveTopology topology = PIPELINE.getPrimitiveTopology();

		currentDraw = VERTEX_BUFFER.appendDraw(
				format,
				topology,
				topology == PrimitiveTopology.QUADS
						? RenderSystem.getProjectionType().vertexSorting()
						: null
		);

		Vec3 camera = context.levelState().cameraRenderState.pos;

		VertexConsumer out = VERTEX_BUFFER.getVertexBuilder(currentDraw);

		for (SkyBoxBlockEntity skyBox : visible) {
			BlockPos pos = skyBox.getBlockPos();
			for (Direction direction : Direction.values()) {
				if (skyBox.shouldRenderFace(direction)) {
					addFace(out, camera, pos, direction);
				}
			}
		}

		VERTEX_BUFFER.upload();

		StagedVertexBuffer.ExecuteInfo info = VERTEX_BUFFER.getExecuteInfo(currentDraw);
		if (info != null) {
			draw(info);
		}
	}

	private static void draw(StagedVertexBuffer.ExecuteInfo info) {
		RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();

		GpuTextureView colorView = target.getColorTextureView();
		GpuTextureView depthView = target.getDepthTextureView();
		if (colorView == null || depthView == null) {
			return;
		}

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(
						() -> "MoreThingsVanilla SkyBox",
						colorView,
						java.util.Optional.empty(),
						depthView,
						java.util.OptionalDouble.empty()
				)) {

			renderPass.setPipeline(PIPELINE);
			RenderSystem.bindDefaultUniforms(renderPass);

			GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(
					RenderSystem.getModelViewMatrixCopy(),
					WHITE,
					ZERO,
					IDENTITY_TEXTURE_MATRIX
			);
			renderPass.setUniform("DynamicTransforms", dynamicTransforms);

			bindFaceTexture(renderPass, "Sampler0", FACE_POS_X);
			bindFaceTexture(renderPass, "Sampler1", FACE_NEG_X);
			bindFaceTexture(renderPass, "Sampler2", FACE_POS_Y);
			bindFaceTexture(renderPass, "Sampler3", FACE_NEG_Y);
			bindFaceTexture(renderPass, "Sampler4", FACE_POS_Z);
			bindFaceTexture(renderPass, "Sampler5", FACE_NEG_Z);

			renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
			renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
			renderPass.drawIndexed(
					info.indexCount(),
					1,
					info.firstIndex(),
					info.baseVertex(),
					0
			);
		}
	}

	private static void bindFaceTexture(RenderPass renderPass, String samplerName, int face) {
		renderPass.bindTexture(samplerName, faces[face].getColorTextureView(), faceSampler);
	}

	/** 输出相对相机的位置，同时用于几何投影和天空采样。 */
	private static void addFace(VertexConsumer out, Vec3 camera, BlockPos pos, Direction direction) {
		int index = direction.ordinal();
		Vector3f normal = FACE_NORMALS[index];
		Vector3f tangent = FACE_TANGENTS[index];
		Vector3f bitangent = FACE_BITANGENTS[index];

		float centerX = (float) (pos.getX() + 0.5 - camera.x);
		float centerY = (float) (pos.getY() + 0.5 - camera.y);
		float centerZ = (float) (pos.getZ() + 0.5 - camera.z);

		for (int corner = 0; corner < 4; corner++) {
			float u = (corner == 1 || corner == 2) ? 1.0F : 0.0F;
			float v = corner >= 2 ? 1.0F : 0.0F;

			float su = u * 2.0F - 1.0F;
			float sv = v * 2.0F - 1.0F;

			// 面中心 + 0.5 法线 + 切线偏移（半格范围）。
			float x = centerX + normal.x * 0.5F + tangent.x * (su * 0.5F) + bitangent.x * (sv * 0.5F);
			float y = centerY + normal.y * 0.5F + tangent.y * (su * 0.5F) + bitangent.y * (sv * 0.5F);
			float z = centerZ + normal.z * 0.5F + tangent.z * (su * 0.5F) + bitangent.z * (sv * 0.5F);

			out.addVertex(x, y, z);
		}
	}

	private static void setFaceAxes(Direction direction, Vector3f normal, Vector3f tangent, Vector3f bitangent) {
		int index = direction.ordinal();
		FACE_NORMALS[index] = normal;
		FACE_TANGENTS[index] = tangent;
		FACE_BITANGENTS[index] = bitangent;
	}

	public static void close() {
		cloudCaptureTarget = null;
		skyCapturedThisFrame = false;

		if (faces != null) {
			for (TextureTarget face : faces) {
				face.destroyBuffers();
			}
			faces = null;
		}

		if (projectionBuffer != null) {
			projectionBuffer.close();
			projectionBuffer = null;
		}

		faceSampler = null;
		VERTEX_BUFFER.close();
	}
}
