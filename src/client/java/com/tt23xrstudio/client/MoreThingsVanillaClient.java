package com.tt23xrstudio.client;

import com.tt23xrstudio.client.cushion.CushionModel;
import com.tt23xrstudio.client.cushion.CushionRenderer;
import com.tt23xrstudio.cushion.MtvCushions;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.EntityRenderers;

public final class MoreThingsVanillaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 坐垫（计划 3）：模型层 + 实体渲染器
		// 实体渲染器用原版 EntityRenderers.register（Fabric EntityRendererRegistry 已弃用，
		// 由 fabric-transitive-access-wideners-v1 放宽原版私有方法为 public）
		ModelLayerRegistry.registerModelLayer(CushionRenderer.MODEL_LAYER, CushionModel::createBodyLayer);
		EntityRenderers.register(MtvCushions.CUSHION, CushionRenderer::new);
	}
}
