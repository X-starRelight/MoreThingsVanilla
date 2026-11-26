package com.tt23xrstudio.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.tt23xrstudio.client.skybox.SkyBoxRenderer;

import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 把原版云层临时重定向到天空立方体的当前面。
 *
 * <p>CloudRenderer.render 自己决定写主目标还是 cloudsTarget；捕获期间
 * SkyBoxRenderer 设置一个“当前面”目标，这里把两个出口都指向它。
 */
@Mixin(CloudRenderer.class)
public class CloudRendererTargetMixin {
	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
			)
	)
	private RenderTarget moreThingsVanilla$redirectMainTarget(GameRenderer gameRenderer) {
		RenderTarget captureTarget = SkyBoxRenderer.getCloudCaptureTarget();
		return captureTarget != null ? captureTarget : gameRenderer.mainRenderTarget();
	}

	@Redirect(
			method = "render",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/LevelRenderer;cloudsTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"
			)
	)
	private RenderTarget moreThingsVanilla$redirectCloudsTarget(LevelRenderer levelRenderer) {
		RenderTarget captureTarget = SkyBoxRenderer.getCloudCaptureTarget();
		return captureTarget != null ? captureTarget : levelRenderer.cloudsTarget();
	}
}
