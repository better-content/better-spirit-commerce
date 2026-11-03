package com.bettercontent.spiritcommerce.nativefixture;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModList;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Fresh defaults from this launch's registered native specs, never a retained generated fixture. */
@Mod.EventBusSubscriber(modid = "better_spirit_commerce", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PrepareNativeConfigs {
    private PrepareNativeConfigs() { }

    @SubscribeEvent
    public static void prepare(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!Boolean.getBoolean("forge.gameTestServer")) throw new IllegalStateException("Native fixture outside GameTest");
            try {
                Path root = Path.of("defaultconfigs").toAbsolutePath().normalize();
                Files.createDirectories(root);
                JsonObject metadata = new JsonObject();
                metadata.addProperty("nonce", Files.readString(Path.of("native-diagnostic-nonce")).trim());
                JsonObject mods = new JsonObject();
                ModList.get().getMods().forEach(mod -> mods.addProperty(mod.getModId(), mod.getVersion().toString()));
                metadata.add("mods", mods);
                metadata.addProperty("dist", net.minecraftforge.fml.loading.FMLEnvironment.dist.name());
                metadata.addProperty("sleeping_server_state_present", PrepareNativeConfigs.class.getClassLoader()
                        .getResource("github/cosmicdan/sleepingoverhaul/server/ServerState.class") != null);
                JsonObject corrections = new JsonObject();
                for (var type : ModConfig.Type.values()) {
                    for (var config : ConfigTracker.INSTANCE.configSets().get(type)) {
                        if (!(config.getSpec() instanceof ForgeConfigSpec spec)) {
                            throw new IllegalStateException("Unclassified native config spec " + config.getFileName());
                        }
                        JsonArray messages = new JsonArray();
                        var defaults = CommentedConfig.inMemory();
                        spec.correct(defaults, (action, path, incorrect, corrected) -> {
                            if (incorrect != null) throw new IllegalStateException("Nonempty bootstrap config");
                            messages.add("Incorrect key " + String.join(".", path)
                                    + " was corrected from null to its default, " + corrected + ". ");
                        });
                        String file = type == ModConfig.Type.SERVER ? "./world/serverconfig/" + config.getFileName()
                                : Path.of("config", config.getFileName()).toAbsolutePath().normalize().toString();
                        corrections.add("Configuration file " + file + " is not correct. Correcting", messages);
                    }
                }
                metadata.add("corrections", corrections);
                Files.writeString(Path.of("native-diagnostic-inputs.json"), metadata.toString());
                int count = 0;
                for (var config : ConfigTracker.INSTANCE.configSets().get(ModConfig.Type.SERVER)) {
                    Path file = root.resolve(config.getFileName()).normalize();
                    if (!file.startsWith(root)) throw new IllegalStateException("Unsafe native config path");
                    Files.createDirectories(file.getParent());
                    try (var defaults = CommentedFileConfig.builder(file).sync().build()) {
                        // Do not load an existing file: materialize current native defaults from empty config.
                        var spec = config.getSpec();
                        spec.correct(defaults);
                        if (!spec.isCorrect(defaults)) throw new IllegalStateException("Invalid native defaults: " + file);
                        defaults.save();
                    }
                    count++;
                }
                if (count == 0) throw new IllegalStateException("No native SERVER config specs registered");
                System.out.println("SPIRIT_FAIR_NATIVE_CONFIGS prepared=" + count);
            } catch (Exception failure) {
                throw new IllegalStateException("Cannot prepare native fixture defaults", failure);
            }
        });
    }
}
