package com.bettercontent.spiritcommerce.nativefixture;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Nonshipping evidence boundary: GameTestServer creates its first arenas after superclass END_1. */
@Mod.EventBusSubscriber(modid = "better_spirit_commerce", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StartupTickBoundary {
    private static final Logger LOGGER = LoggerFactory.getLogger("better_spirit_commerce.native_startup");
    private static GameTestServer server;
    private static String nonce;
    private static int starts;
    private static boolean firstEnded;

    private StartupTickBoundary() {}

    @SubscribeEvent
    public static void tick(final TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("forge.gameTestServer")) return;
        if (!(event.getServer() instanceof GameTestServer current)) {
            throw new IllegalStateException("Native startup boundary requires GameTestServer");
        }
        if (server == null) {
            server = current;
            try {
                nonce = Files.readString(Path.of("native-diagnostic-nonce")).trim();
                if (!UUID.fromString(nonce).toString().equals(nonce)) {
                    throw new IllegalStateException("Noncanonical native invocation nonce");
                }
            } catch (Exception error) {
                throw new IllegalStateException("Missing native invocation nonce", error);
            }
        }
        if (server != current) throw new IllegalStateException("Native startup server identity changed");
        if (event.phase == TickEvent.Phase.START) {
            starts++;
            if (starts == 1) mark("START_1");
            else if (starts == 2) {
                if (!firstEnded) throw new IllegalStateException("First native tick did not end");
                mark("START_2");
            }
        } else if (starts == 1) {
            if (firstEnded) throw new IllegalStateException("Duplicate first native END");
            firstEnded = true;
            mark("END_1");
        }
    }

    private static void mark(String phase) {
        LOGGER.info("SPIRIT_NATIVE_STARTUP_BOUNDARY nonce={} phase={}", nonce, phase);
    }
}
