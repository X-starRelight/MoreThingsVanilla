package com.tt23xrstudio.client.cushion;

import com.tt23xrstudio.MoreThingsVanilla;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/** 坐垫渲染状态（复刻 26.4 CushionRenderState）。 */
public class CushionRenderState extends EntityRenderState {
	private static final Identifier DEFAULT_TEXTURE =
			MoreThingsVanilla.id("textures/entity/cushion/white_cushion.png");

	public Direction direction = Direction.NORTH;
	public Identifier texture = DEFAULT_TEXTURE;
}
