package com.bettercontent.spiritcommerce.trader;

import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;

/** Five themed visits; old string labels load as canonical themes, never extra spirits. */
public enum WanderingTraderTheme {
    POWER, MOTION, ENDURANCE, RENEWAL, CONTROL;

    // Retired source aliases are not theme values or additional scheduled visits.
    @Deprecated public static final WanderingTraderTheme SACRED = RENEWAL, WICKED = CONTROL;
    @Deprecated public static final WanderingTraderTheme ARCANE = POWER, INFERNAL = POWER;
    @Deprecated public static final WanderingTraderTheme AERIAL = MOTION, TEMPO = MOTION;
    @Deprecated public static final WanderingTraderTheme AQUEOUS = ENDURANCE, EARTHEN = ENDURANCE;

    private static final WanderingTraderTheme[] VALUES = values();
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public Component displayName() {
        return Component.translatable("entity.better_spirit_commerce.wandering_trader." + id());
    }
    public static WanderingTraderTheme fromIndex(int index) { return VALUES[Math.floorMod(index, VALUES.length)]; }
    public static WanderingTraderTheme fromId(String id) {
        SpiritKind kind = SpiritKind.fromId(id);
        return kind == null ? null : valueOf(kind.name());
    }
    public static WanderingTraderTheme forUuid(UUID uuid) { return fromIndex(uuid.hashCode()); }
    public WanderingTraderTheme next() { return fromIndex(ordinal() + 1); }
    public SpiritKind spirit() { return SpiritKind.valueOf(name()); }
    public CurrencyIdentity currencyIdentity() { return CurrencyIdentity.valueOf(name()); }
}
