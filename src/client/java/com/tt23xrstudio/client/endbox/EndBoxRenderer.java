package com.tt23xrstudio.client.endbox;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tt23xrstudio.endbox.EndBoxBlockEntity;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public final class EndBoxRenderer extends AbstractEndPortalRenderer<EndBoxBlockEntity, EndPortalRenderState> {
	public static void register() {
		BlockEntityRenderers.register(EndBoxBlockEntity.type(), context -> new EndBoxRenderer());
	}

	@Override
	public EndPortalRenderState createRenderState() {
		return new EndPortalRenderState();
	}

	@Override
	public void submit(EndPortalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		// Iris hooks submitCube; leave the pose unscaled so the portal covers the entire box.
		submitCube(state.facesToShow, RenderTypes.endPortal(), poseStack, collector);
	}
}
