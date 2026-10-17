package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.celestial.api.CelestialEvent;
import com.bettercontent.celestial.api.CelestialEventsApi;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

/** The provider types are resolved only inside the guarded optional bridge. */
final class SpiritFairBridge {
    private SpiritFairBridge() { }

    static SpiritFairVisits.Window snapshot(final MinecraftServer server) {
        return ModList.get().isLoaded("better_celestial_events") ? Loaded.snapshot(server) : null;
    }

    private static final class Loaded {
        static SpiritFairVisits.Window snapshot(final MinecraftServer server) {
            var snapshot = CelestialEventsApi.snapshot(server);
            return snapshot.active() && snapshot.event() == CelestialEvent.SPIRIT_FAIR
                    ? new SpiritFairVisits.Window(snapshot.occurrenceId(), snapshot.scheduledDay()) : null;
        }
    }
}
