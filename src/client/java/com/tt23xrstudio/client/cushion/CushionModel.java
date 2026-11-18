package com.tt23xrstudio.client.cushion;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** 坐垫模型（复刻 26.4 CushionModel：单块 16×4×16，64×64 贴图）。 */
public class CushionModel extends EntityModel<CushionRenderState> {
	public CushionModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshDefinition = new MeshDefinition();
		PartDefinition root = meshDefinition.getRoot();
		root.addOrReplaceChild(
				"cushion",
				CubeListBuilder.create().texOffs(0, 0)
						.addBox(-31.0F, -4.0F, -1.0F, 16.0F, 4.0F, 16.0F, new CubeDeformation(-0.005F)),
				PartPose.offset(23.0F, 4.0F, -7.0F)
		);
		return LayerDefinition.create(meshDefinition, 64, 64);
	}
}
