package com.bettercontent.spiritcommerce.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class EconomyConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue RECURRING_VISITS;
    private static final ForgeConfigSpec.BooleanValue ANNOUNCE_ARRIVAL;
    private static final ForgeConfigSpec.BooleanValue OBSERVATIONAL_EXPORT;
    private static final ForgeConfigSpec.IntValue SPIRIT_RELEASE_CADENCE;

    static {
        var builder = new ForgeConfigSpec.Builder();
        builder.push("wanderingTrader");
        RECURRING_VISITS = builder.define("recurringVisits", true);
        ANNOUNCE_ARRIVAL = builder.define("announceArrival", true);
        builder.pop();
        builder.push("observationalExport");
        OBSERVATIONAL_EXPORT = builder.define("enabled", false);
        builder.pop();
        builder.push("spiritRelease");
        SPIRIT_RELEASE_CADENCE = builder.defineInRange("cadenceTicks", 100, 20, 1_200);
        builder.pop();
        SPEC = builder.build();
    }

    private EconomyConfig() {}
    public static boolean wanderingTraderRecurringVisits() { return RECURRING_VISITS.get(); }
    public static boolean wanderingTraderAnnounceArrival() { return ANNOUNCE_ARRIVAL.get(); }
    /** Owner export remains disabled until explicitly enabled in server configuration. */
    public static boolean observationalExportEnabled() { return OBSERVATIONAL_EXPORT.get(); }
    public static int spiritReleaseCadenceTicks() { return SPIRIT_RELEASE_CADENCE.get(); }
}
