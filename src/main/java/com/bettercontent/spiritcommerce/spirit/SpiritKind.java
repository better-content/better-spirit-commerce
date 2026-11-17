package com.bettercontent.spiritcommerce.spirit;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

/** Five live commerce themes. Old row labels are read-only catalogue aliases. */
public enum SpiritKind {
    POWER, MOTION, ENDURANCE, RENEWAL, CONTROL;

    // Internal source aliases for old fixture/call sites; values() remains five.
    @Deprecated public static final SpiritKind SACRED = RENEWAL, WICKED = CONTROL;
    @Deprecated public static final SpiritKind ARCANE = POWER, INFERNAL = POWER;
    @Deprecated public static final SpiritKind AERIAL = MOTION, TEMPO = MOTION;
    @Deprecated public static final SpiritKind AQUEOUS = ENDURANCE, EARTHEN = ENDURANCE;

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public ResourceLocation itemId() { return currencyIdentity().itemId(); }
    public CurrencyIdentity currencyIdentity() { return CurrencyIdentity.valueOf(name()); }

    public static SpiritKind fromId(String id) {
        if (id == null) return null;
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (path.endsWith("_spirit")) path = path.substring(0, path.length() - 7);
        CurrencyIdentity nativeIdentity = CurrencyIdentity.fromNativeName(path);
        if (nativeIdentity != null) return valueOf(nativeIdentity.name());
        // Prior Tempo stock is a Motion template; it does not mint a sixth spirit.
        if (path.equals("tempo") || path.equals("mobility")) return MOTION;
        if (path.equals("impact") || path.equals("work")) return POWER;
        if (path.equals("robustness")) return ENDURANCE;
        for (SpiritKind kind : values()) if (kind.id().equals(path)) return kind;
        return null;
    }
}
