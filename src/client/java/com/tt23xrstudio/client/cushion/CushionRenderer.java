package com.tt23xrstudio.client.cushion;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import com.tt23xrstudio.MoreThingsVanilla;
import com.tt23xrstudio.cushion.MtvCushion;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

/** 坐垫渲染器（复刻 26.4 CushionRenderer）。 */
public class CushionRenderer extends EntityRenderer<MtvCushion, CushionRenderState> {
	/** 模型层 id（ModelLayerRegistry 与 bakeLayer 共用）。 */
	public static final ModelLayerLocation MODEL_LAYER =
			new ModelLayerLocation(MoreThingsVanilla.id("cushion"), "main");

	private static final Map<DyeColor, Identifier> TEXTURES_BY_COLOR = createTextures();

	private static Map<DyeColor, Identifier> createTextures() {
		Map<DyeColor, Identifier> textures = new EnumMap<>(DyeColor.class);
		for (DyeColor color : DyeColor.values()) {
			textures.put(color, MoreThingsVanilla.id(
					"textures/entity/cushion/" + color.getName() + "_cushion.png"));
		}
		return textures;
	}

	private final CushionModel model;

	public CushionRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new CushionModel(context.bakeLayer(MODEL_LAYER));
	}

	@Override
	public void extractRenderState(MtvCushion cushion, CushionRenderState state, float partialTicks) {
		super.extractRenderState(cushion, state, partialTicks);
		state.direction = Direction.fromYRot(cushion.getYRot());
		state.texture = TEXTURES_BY_COLOR.get(cushion.getColor());
	}

	@Override
	public void submit(CushionRenderState state, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
		poseStack.pushPose();
		// 26.4 poseStack.rotateDegrees(Axis, deg) → 26.2 mulPose(Axis.rotationDegrees)
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.direction.toYRot()));
		poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
		poseStack.translate(0.0, -0.25, 0.0);
		submitNodeCollector.submitModel(
				this.model, state, poseStack, this.model.renderType(state.texture),
				state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null
		);
		poseStack.popPose();
		super.submit(state, poseStack, submitNodeCollector, camera);
	}

	@Override
	public CushionRenderState createRenderState() {
		return new CushionRenderState();
	}
}
