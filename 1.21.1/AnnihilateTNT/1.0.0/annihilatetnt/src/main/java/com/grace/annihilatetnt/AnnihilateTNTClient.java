package com.grace.annihilatetnt;

import com.grace.annihilatetnt.entity.PrimedAnnihilateTNT;
import com.grace.annihilatetnt.registry.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@OnlyIn(Dist.CLIENT)
public class AnnihilateTNTClient {

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.PRIMED_ANNIHILATE_TNT.get(),
                PrimedAnnihilateTNTRenderer::new
        );
    }

    @OnlyIn(Dist.CLIENT)
    public static class PrimedAnnihilateTNTRenderer extends EntityRenderer<PrimedAnnihilateTNT> {

        private final BlockRenderDispatcher blockRenderer;

        public PrimedAnnihilateTNTRenderer(EntityRendererProvider.Context ctx) {
            super(ctx);
            this.blockRenderer = ctx.getBlockRenderDispatcher();
        }

        @Override
        public void render(PrimedAnnihilateTNT entity, float entityYaw, float partialTick,
                           PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.5F, 0.0F);

            int fuse = entity.getFuse();
            if (fuse < 10) {
                float f = 1.0F - (fuse / 10.0F);
                float scale = 1.0F + f * 0.3F;
                poseStack.scale(scale, scale, scale);
            }

            blockRenderer.renderSingleBlock(
                    Blocks.TNT.defaultBlockState(),
                    poseStack,
                    buffers,
                    packedLight,
                    OverlayTexture.NO_OVERLAY
            );

            poseStack.popPose();
            super.render(entity, entityYaw, partialTick, poseStack, buffers, packedLight);
        }

        @Override
        public ResourceLocation getTextureLocation(PrimedAnnihilateTNT entity) {
            return ResourceLocation.withDefaultNamespace("textures/block/tnt_side.png");
        }
    }
}
