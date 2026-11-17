package com.bettercontent.spiritcommerce.registry;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.config.EconomyPolicy;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.google.common.collect.ImmutableSet;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registry boundary for the five spirit-market professions and their job sites. */
public final class SpiritProfessions {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BetterSpiritCommerce.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BetterSpiritCommerce.MOD_ID);
    public static final DeferredRegister<PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, BetterSpiritCommerce.MOD_ID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, BetterSpiritCommerce.MOD_ID);

    private static final Map<SpiritKind, Definition> DEFINITIONS = new EnumMap<>(SpiritKind.class);

    static {
        // Reuse one existing registry/job-site ID per merged market; do not erase
        // any placed job-site data or invent eight new stock/currency definitions.
        define(SpiritKind.POWER, "infernal_kiln", "infernal_stoker", "Power Stoker", SoundEvents.VILLAGER_WORK_ARMORER);
        define(SpiritKind.MOTION, "aerial_waypost", "aerial_courier", "Motion Courier", SoundEvents.VILLAGER_WORK_CARTOGRAPHER);
        define(SpiritKind.ENDURANCE, "aqueous_font", "aqueous_tidekeeper", "Endurance Keeper", SoundEvents.VILLAGER_WORK_FISHERMAN);
        define(SpiritKind.RENEWAL, "sacred_reliquary", "sacred_caretaker", "Renewal Caretaker", SoundEvents.VILLAGER_WORK_CLERIC);
        define(SpiritKind.CONTROL, "wicked_effigy", "wicked_hexbinder", "Control Binder", SoundEvents.VILLAGER_WORK_WEAPONSMITH);
    }

    private SpiritProfessions() {}

    public static void register(final IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        POIS.register(bus);
        PROFESSIONS.register(bus);
        bus.addListener(EventPriority.LOWEST, SpiritProfessions::rewriteCreativeTab);
    }

    public static Definition definition(final SpiritKind kind) {
        return DEFINITIONS.get(kind);
    }

    public static SpiritKind kindOf(final VillagerProfession profession) {
        for (var entry : DEFINITIONS.entrySet()) {
            if (entry.getValue().profession().isPresent() && entry.getValue().profession().get() == profession) return entry.getKey();
        }
        return null;
    }

    public static boolean isSpiritProfession(final VillagerProfession profession) {
        return kindOf(profession) != null;
    }

    public static Component displayName(final SpiritKind kind) {
        Definition definition = definition(kind);
        String translationKey = "entity.minecraft.villager." + BetterSpiritCommerce.MOD_ID + "." + definition.professionName();
        return Component.translatableWithFallback(translationKey, definition.fallbackName());
    }

    private static void define(
            final SpiritKind kind,
            final String stationName,
            final String professionName,
            final String fallbackName,
            final net.minecraft.sounds.SoundEvent workSound) {
        RegistryObject<Block> block = BLOCKS.register(stationName,
                () -> new Block(BlockBehaviour.Properties.copy(Blocks.LECTERN).strength(2.5F).noOcclusion()));
        RegistryObject<Item> item = ITEMS.register(stationName,
                () -> new BlockItem(block.get(), new Item.Properties()));
        RegistryObject<PoiType> poi = POIS.register(professionName,
                () -> new PoiType(ImmutableSet.copyOf(block.get().getStateDefinition().getPossibleStates()), 1, 1));
        RegistryObject<VillagerProfession> profession = PROFESSIONS.register(professionName,
                () -> new VillagerProfession(
                        professionName,
                        holder -> holder.value() == poi.get(),
                        holder -> holder.value() == poi.get(),
                        ImmutableSet.of(),
                        ImmutableSet.of(),
                        workSound));
        DEFINITIONS.put(kind, new Definition(block, item, poi, profession, professionName, fallbackName));
    }

    private static void rewriteCreativeTab(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            for (SpiritKind kind : SpiritKind.values()) event.accept(definition(kind).item());
        }
        var iterator = event.getEntries().iterator();
        while (iterator.hasNext()) {
            var item = ForgeRegistries.ITEMS.getKey(iterator.next().getKey().getItem());
            if (item != null && (EconomyPolicy.isRetired(item)
                    || CurrencyIdentity.fromLegacyNativeSpirit(item) != null)) iterator.remove();
        }
    }

    public record Definition(
            RegistryObject<Block> block,
            RegistryObject<Item> item,
            RegistryObject<PoiType> poi,
            RegistryObject<VillagerProfession> profession,
            String professionName,
            String fallbackName) {}
}
