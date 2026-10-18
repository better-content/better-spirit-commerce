package com.bettercontent.spiritcommerce.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class EconomyConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue RECURRING_VISITS;
    private static final ForgeConfigSpec.BooleanValue ANNOUNCE_ARRIVAL;
    private static final ForgeConfigSpec.BooleanValue OBSERVATIONAL_EXPORT;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("wanderingTrader");
        RECURRING_VISITS = builder.define("recurringVisits", true);
        ANNOUNCE_ARRIVAL = builder.define("announceArrival", true);
        builder.pop();
        builder.push("observationalExport");
        OBSERVATIONAL_EXPORT = builder.define("enabled", false);
        builder.pop();
        SPEC = builder.build();
    }

    private EconomyConfig() {}
    public static boolean wanderingTraderRecurringVisits() { return RECURRING_VISITS.get(); }
    public static boolean wanderingTraderAnnounceArrival() { return ANNOUNCE_ARRIVAL.get(); }
    /** Owner export remains disabled until explicitly enabled in server configuration. */
    public static boolean observationalExportEnabled() { return OBSERVATIONAL_EXPORT.get(); }
}
