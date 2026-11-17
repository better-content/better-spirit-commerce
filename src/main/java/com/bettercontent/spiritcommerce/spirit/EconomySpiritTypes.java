package com.bettercontent.spiritcommerce.spirit;

import com.bettercontent.spiritcommerce.mixin.MalumSpiritTypeAccessor;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.sammy.malum.common.item.spirit.SpiritShardItem;
import com.sammy.malum.core.systems.spirit.MalumSpiritType;
import com.sammy.malum.registry.common.SpiritTypeRegistry;
import java.util.EnumMap;
import java.util.Map;

/** Five physical reagents; old native types remain internal recipe/ritual aliases only. */
public final class EconomySpiritTypes {
    private static final Map<CurrencyIdentity, MalumSpiritType> TYPES = new EnumMap<>(CurrencyIdentity.class);
    private static boolean initialized;
    private EconomySpiritTypes() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        TYPES.put(CurrencyIdentity.POWER, SpiritTypeRegistry.INFERNAL_SPIRIT);
        TYPES.put(CurrencyIdentity.MOTION, SpiritTypeRegistry.AERIAL_SPIRIT);
        TYPES.put(CurrencyIdentity.ENDURANCE, SpiritTypeRegistry.AQUEOUS_SPIRIT);
        TYPES.put(CurrencyIdentity.RENEWAL, SpiritTypeRegistry.SACRED_SPIRIT);
        TYPES.put(CurrencyIdentity.CONTROL, SpiritTypeRegistry.WICKED_SPIRIT);
        bind(SpiritTypeRegistry.INFERNAL_SPIRIT, CurrencyIdentity.POWER);
        bind(SpiritTypeRegistry.ARCANE_SPIRIT, CurrencyIdentity.POWER);
        bind(SpiritTypeRegistry.AERIAL_SPIRIT, CurrencyIdentity.MOTION);
        bind(SpiritTypeRegistry.AQUEOUS_SPIRIT, CurrencyIdentity.ENDURANCE);
        bind(SpiritTypeRegistry.EARTHEN_SPIRIT, CurrencyIdentity.ENDURANCE);
        bind(SpiritTypeRegistry.SACRED_SPIRIT, CurrencyIdentity.RENEWAL);
        bind(SpiritTypeRegistry.WICKED_SPIRIT, CurrencyIdentity.CONTROL);
        // Eldritch/Umbral native types and shards are untouched specialist reagents.
    }

    private static void bind(MalumSpiritType type, CurrencyIdentity identity) {
        ((MalumSpiritTypeAccessor) type).betterContentEconomy$setSpiritShard(
                () -> (SpiritShardItem) CurrencyItems.item(identity).get());
    }

    public static MalumSpiritType type(CurrencyIdentity identity) {
        initialize();
        return TYPES.get(identity);
    }
}
