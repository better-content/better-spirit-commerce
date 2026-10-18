package com.bettercontent.spiritcommerce.client;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.spiritcommerce.trader.SpiritCampfires;
import net.minecraft.client.particle.CampfireSmokeParticle;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.CampfireRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = BetterSpiritCommerce.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SpiritCampfireClient {
    private SpiritCampfireClient() { }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            for (SpiritKind kind : SpiritKind.values()) ItemBlockRenderTypes.setRenderLayer(SpiritCampfires.block(kind), RenderType.cutout());
        });
    }
    @SubscribeEvent public static void blockColors(RegisterColorHandlersEvent.Block event) {
        for (SpiritKind kind : SpiritKind.values()) event.register((state, level, pos, index) -> SpiritCampfires.color(kind), SpiritCampfires.block(kind));
    }
    @SubscribeEvent public static void itemColors(RegisterColorHandlersEvent.Item event) {
        for (SpiritKind kind : SpiritKind.values()) event.register((stack, index) -> SpiritCampfires.color(kind), SpiritCampfires.item(kind));
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SpiritCampfires.ENTITY.get(), CampfireRenderer::new);
    }
    @SubscribeEvent public static void particles(RegisterParticleProvidersEvent event) {
        for (SpiritKind kind : SpiritKind.values()) {
            int color = SpiritCampfires.color(kind);
            event.registerSpriteSet(SpiritCampfires.FLAMES.get(kind).get(), sprites ->
                    tinted(new FlameParticle.Provider(sprites), color));
            event.registerSpriteSet(SpiritCampfires.SMOKE.get(kind).get(), sprites ->
                    tinted(new CampfireSmokeParticle.CosyProvider(sprites), color));
        }
    }
    private static ParticleProvider<net.minecraft.core.particles.SimpleParticleType> tinted(
            ParticleProvider<net.minecraft.core.particles.SimpleParticleType> provider, int color) {
        return (type, level, x, y, z, dx, dy, dz) -> {
            Particle particle = provider.createParticle(type, level, x, y, z, dx, dy, dz);
            if (particle != null) particle.setColor(((color >> 16) & 255) / 255f,
                    ((color >> 8) & 255) / 255f, (color & 255) / 255f);
            return particle;
        };
    }
}
