package com.bettercontent.spiritcommerce.visualharness;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Captures unmodified client framebuffers after server-directed camera changes settle. */
public final class CampVisualScreenshot {
    private static String pending;
    private static int settleTicks;
    private static boolean installed;

    private CampVisualScreenshot() { }

    static void request(final String name) {
        if (!installed) {
            MinecraftForge.EVENT_BUS.register(CampVisualScreenshot.class);
            installed = true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        // Typed harness operation, not simulated Escape/pointer input. Review the world framebuffer.
        minecraft.setScreen(null);
        minecraft.options.hideGui = true;
        pending = name + ".png";
        settleTicks = 0;
    }

    @SubscribeEvent
    public static void tick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ++settleTicks < 25) return;
        String fileName = pending;
        pending = null;
        Screenshot.grab(minecraft.gameDirectory, fileName, minecraft.getMainRenderTarget(), message -> {
            Path output = minecraft.gameDirectory.toPath().resolve("screenshots").resolve(fileName);
            if (!Files.isRegularFile(output)) {
                throw new IllegalStateException("Camp visual screenshot failed: " + message.getString());
            }
            System.out.println("CAMP_VISUAL captured " + output.toAbsolutePath());
        });
    }
}
