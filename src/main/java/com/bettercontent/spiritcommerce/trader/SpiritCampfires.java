package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Trader-exclusive decorative campfires; the palette is shared with the existing market awnings. */
public final class SpiritCampfires {
    private static final Map<SpiritKind, RegistryObject<SpiritCampfireBlock>> BLOCKS = new EnumMap<>(SpiritKind.class);
    private static final Map<SpiritKind, RegistryObject<Item>> ITEMS = new EnumMap<>(SpiritKind.class);
    public static final Map<SpiritKind, RegistryObject<SimpleParticleType>> FLAMES = new EnumMap<>(SpiritKind.class);
    public static final Map<SpiritKind, RegistryObject<SimpleParticleType>> SMOKE = new EnumMap<>(SpiritKind.class);
    private static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, BetterSpiritCommerce.MOD_ID);

    static {
        for (SpiritKind kind : SpiritKind.values()) {
            String name = kind.id() + "_campfire";
            var block = TraderCampRegistries.BLOCKS.register(name, () -> new SpiritCampfireBlock(kind));
            BLOCKS.put(kind, block);
            ITEMS.put(kind, TraderCampRegistries.ITEMS.register(name,
                    () -> new BlockItem(block.get(), new Item.Properties())));
            FLAMES.put(kind, PARTICLES.register(kind.id() + "_flame", () -> new SimpleParticleType(false)));
            SMOKE.put(kind, PARTICLES.register(kind.id() + "_smoke", () -> new SimpleParticleType(false)));
        }
    }
    public static final RegistryObject<BlockEntityType<SpiritCampfireBlockEntity>> ENTITY =
            TraderCampRegistries.BLOCK_ENTITIES.register("spirit_campfire", () -> BlockEntityType.Builder.of(
                    SpiritCampfireBlockEntity::new, BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private SpiritCampfires() { }
    public static void register(IEventBus bus) { PARTICLES.register(bus); }
    public static SpiritCampfireBlock block(SpiritKind kind) { return BLOCKS.get(kind).get(); }
    public static Item item(SpiritKind kind) { return ITEMS.get(kind).get(); }
    public static int color(SpiritKind kind) {
        return switch (kind) {
            case POWER -> 0xFF4055;
            case MOTION -> 0xE0B01F;
            case ENDURANCE -> 0x52606A;
            case RENEWAL -> 0x6CCAF0;
            case CONTROL -> 0x8E5BB7;
        };
    }
}
