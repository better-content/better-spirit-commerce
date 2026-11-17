package com.bettercontent.spiritcommerce.spirit;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/** Five physical economy spirits that are also actual native Malum crafting reagents. */
public enum CurrencyIdentity {
    POWER("infernal", "arcane"),
    MOTION("aerial"),
    ENDURANCE("aqueous", "earthen"),
    RENEWAL("sacred"),
    CONTROL("wicked");

    // Source compatibility only. These are NOT enum values, items, or distinct reagents.
    @Deprecated public static final CurrencyIdentity IMPACT = POWER, WORK = POWER;
    @Deprecated public static final CurrencyIdentity MOBILITY = MOTION, TEMPO = MOTION;
    @Deprecated public static final CurrencyIdentity ROBUSTNESS = ENDURANCE;

    private final String[] nativeNames;

    CurrencyIdentity(String... nativeNames) { this.nativeNames = nativeNames; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public ResourceLocation itemId() { return new ResourceLocation(BetterSpiritCommerce.MOD_ID, id() + "_spirit"); }

    /** Existing native keys are internal aliases, not separate player reagents. */
    public static CurrencyIdentity fromNativeName(String name) {
        if (name == null) return null;
        for (CurrencyIdentity identity : values())
            for (String nativeName : identity.nativeNames)
                if (nativeName.equals(name)) return identity;
        return null;
    }

    public static CurrencyIdentity fromLegacyNativeSpirit(ResourceLocation id) {
        if (id == null || !"malum".equals(id.getNamespace()) || !id.getPath().endsWith("_spirit")) return null;
        return fromNativeName(id.getPath().substring(0, id.getPath().length() - "_spirit".length()));
    }

    public static CurrencyIdentity fromItemId(ResourceLocation id) {
        if (id == null) return null;
        for (CurrencyIdentity identity : values()) if (id.equals(identity.itemId())) return identity;
        return null;
    }
}
