package com.tt23xrstudio.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露原版天空渲染器的输出目标。
 *
 * <p>26.2 的 SkyRenderer 在构造时固定 renderTarget，所有天空 pass 都写入该字段。
 * 捕获天空立方体时临时把它切到某一张面纹理，画完再切回主目标。
 */
@Mixin(SkyRenderer.class)
public interface SkyRendererAccessor {
	@Accessor("renderTarget")
	RenderTarget moreThingsVanilla$getRenderTarget();

	@Mutable
	@Accessor("renderTarget")
	void moreThingsVanilla$setRenderTarget(RenderTarget renderTarget);
}
