package com.bettercontent.spiritcommerce.registry;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Five physical currencies that are themselves ordinary Malum SpiritShardItem reagents. */
public final class CurrencyItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, BetterSpiritCommerce.MOD_ID);
    private static final Map<CurrencyIdentity, RegistryObject<Item>> ITEMS_BY_ID = new EnumMap<>(CurrencyIdentity.class);

    static {
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            ITEMS_BY_ID.put(identity, ITEMS.register(identity.id() + "_spirit", () -> new CurrencyItem(identity)));
        }
    }

    private CurrencyItems() {}

    public static void register(final IEventBus bus) {
        ITEMS.register(bus);
    }

    public static RegistryObject<Item> item(final CurrencyIdentity identity) {
        return ITEMS_BY_ID.get(identity);
    }
}
