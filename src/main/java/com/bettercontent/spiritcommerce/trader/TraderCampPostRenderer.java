package com.bettercontent.spiritcommerce.trader;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws a compact travelling awning with a gently sagged, supported cloth sheet. */
public final class TraderCampPostRenderer implements BlockEntityRenderer<TraderCampPostBlockEntity> {
    private static final ResourceLocation WOOL = new ResourceLocation("minecraft", "textures/block/white_wool.png");
    private static final ResourceLocation SPRUCE = new ResourceLocation("minecraft", "textures/block/spruce_planks.png");

    public TraderCampPostRenderer(final BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(final TraderCampPostBlockEntity post, final float partialTick, final PoseStack poseStack,
                       final MultiBufferSource buffers, final int packedLight, final int packedOverlay) {
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        Direction facing = post.getBlockState().getValue(TraderCampPostBlock.FACING);
        poseStack.mulPose(Axis.YP.rotationDegrees(TraderCampAwningGeometry.yawFor(facing.getStepX(), facing.getStepZ())));
        VertexConsumer support = buffers.getBuffer(RenderType.entityCutout(SPRUCE));
        for (TraderCampAwningGeometry.Quad face : TraderCampAwningGeometry.WOOD) {
            drawFace(poseStack, support, face, 0xFFFFFFFF, packedLight);
        }
        VertexConsumer cloth = buffers.getBuffer(RenderType.entityCutout(WOOL));
        int color = colorFor(post.themeId());
        for (TraderCampAwningGeometry.Quad face : TraderCampAwningGeometry.CLOTH) {
            float u = (face.a().u() + face.b().u() + face.c().u() + face.d().u()) * 0.25F;
            float amount = 0.92F + 0.08F * (1.0F - Math.abs(u - 0.5F) * 2.0F);
            drawFace(poseStack, cloth, face, shade(color, amount), packedLight);
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRender(final TraderCampPostBlockEntity post, final Vec3 cameraPosition) {
        return TraderCampPostBlockEntity.renderBounds(post.getBlockPos()).distanceToSqr(cameraPosition)
                < (double) getViewDistance() * getViewDistance();
    }

    private static int colorFor(final String theme) {
        return switch (theme) {
            case "wicked" -> 0xFF9D78C4;
            case "arcane" -> 0xFF757FE0;
            case "aerial" -> 0xFF8EC9E8;
            case "aqueous" -> 0xFF54B9B1;
            case "earthen" -> 0xFFB9895F;
            case "infernal" -> 0xFFC75D4D;
            case "tempo" -> 0xFFD8BE62;
            default -> 0xFFF0E7CC;
        };
    }

    private static int shade(final int color, final float amount) {
        int a = color >>> 24;
        int r = Math.min(255, Math.round(((color >>> 16) & 0xFF) * amount));
        int g = Math.min(255, Math.round(((color >>> 8) & 0xFF) * amount));
        int b = Math.min(255, Math.round((color & 0xFF) * amount));
        return a << 24 | r << 16 | g << 8 | b;
    }

    private static void drawFace(final PoseStack stack, final VertexConsumer consumer,
                                 final TraderCampAwningGeometry.Quad face, final int color, final int light) {
        Matrix4f pose = stack.last().pose();
        Matrix3f normal = stack.last().normal();
        TraderCampAwningGeometry.Normal n = face.normal();
        for (TraderCampAwningGeometry.Vertex p : face.vertices()) {
            consumer.vertex(pose, p.x(), p.y(), p.z()).color(color).uv(p.u(), p.v())
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                    .normal(normal, n.x(), n.y(), n.z()).endVertex();
        }
    }
}
