package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Exact CurseMaven pins declared in build.gradle.kts supply native bytecode, not historical evidence. */
final class SpiritCampfireParticleStackTest {
    private ClassNode nativeClass(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
    private boolean calls(ClassNode node, String method, String owner, String name) {
        for (var candidate : node.methods) if (candidate.name.equals(method)) {
            for (var instruction : candidate.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return true;
            }
        }
        return false;
    }
    @Test void nativeEmitterDiscoveryIncludesCustomBlockEntitiesAndStateChanges() throws Exception {
        String locations = "com/endertech/minecraft/mods/adchimneys/world/SmokeLocations";
        var events = nativeClass("com/endertech/minecraft/mods/adchimneys/world/Events");
        assertTrue(calls(events, "onBlockChanged", locations, "updateSmokeSourceAt"));
        assertTrue(calls(events, "onChunkLoad", locations, "updateSmokeSourcesIn"));
        var sources = nativeClass(locations);
        assertTrue(calls(sources, "updateSmokeSourcesIn", "net/minecraft/world/level/chunk/ChunkAccess", "getBlockEntitiesPos"),
                "Discovery must include custom campfire BEs, not only vanilla's BE type");
        assertTrue(calls(sources, "updateSmokeSourceAt", "com/endertech/minecraft/mods/adchimneys/init/Emitters", "get"));
        assertTrue(calls(nativeClass("com/endertech/minecraft/mods/adchimneys/init/Emitters"), "get",
                "com/endertech/minecraft/forge/units/EmittersInit", "get"), "Custom config-backed emitter lookup must remain available");
    }
    @Test void optionalSmokeSafetyTargetsAndWindContractsExist() throws Exception {
        var smoke = nativeClass("com/endertech/minecraft/mods/adchimneys/particles/AbstractSmokeParticle$Instance");
        assertTrue(smoke.fields.stream().anyMatch(f -> f.name.equals("startPos") && f.desc.equals("Lnet/minecraft/core/BlockPos;")));
        assertTrue(smoke.methods.stream().anyMatch(m -> m.name.equals("updatePoisonEffect") && m.desc.equals("()V")));
        boolean ownWind = false;
        for (var method : smoke.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("com/endertech/minecraft/forge/world/IWind")
                    && call.name.equals("getMotion")) ownWind = true;
        }
        assertTrue(ownWind, "Advanced Chimneys already applies ForgeEndertech wind");
        var weather = nativeClass("weather2/client/SceneEnhancer");
        boolean genericWind = false;
        for (var method : weather.methods) for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("weather2/weathersystem/wind/WindManager")
                    && call.name.equals("applyWindForceNew")) genericWind = true;
        }
        assertTrue(genericWind, "Weather2 also adjusts particle motion; do not add a third force");
    }
    @Test void authoredPackEmittersMatchEveryRegisteredThemeAndSignalState() throws Exception {
        Path root = Path.of("../../better-content-modpack/config/adchimneys/Emitters");
        String registry = Files.readString(Path.of("src/main/java/com/bettercontent/spiritcommerce/trader/SpiritCampfires.java"));
        for (var kind : com.bettercontent.spiritcommerce.spirit.SpiritKind.values()) {
            var palette = java.util.regex.Pattern.compile("case " + kind.name() + " -> 0x([0-9A-Fa-f]{6});").matcher(registry);
            assertTrue(palette.find(), "Missing registered palette: " + kind);
            String color = "S:color=0xff" + palette.group(1).toLowerCase(java.util.Locale.ROOT);
            for (boolean signal : new boolean[]{false, true}) {
                String name = "better_spirit_commerce$" + kind.id() + "_campfire" + (signal ? "_signal" : "") + ".cfg";
                String emitter = Files.readString(root.resolve(name));
                assertTrue(emitter.contains(color), name);
                assertTrue(emitter.contains("S:id=better_spirit_commerce:" + kind.id()
                        + "_campfire:[lit=true, signal_fire=" + signal + "]"), name);
                assertTrue(emitter.contains("B:emitWithoutChimney=true"), name);
                assertTrue(emitter.contains("S:type=TILE"), name);
            }
        }
    }
    @Test void replacementAndFallbackAreMutuallyExclusiveAndPaletteIsShared() throws Exception {
        String tick = Files.readString(Path.of("src/main/java/com/bettercontent/spiritcommerce/trader/SpiritCampfireBlockEntity.java"));
        assertTrue(tick.contains("!ModList.get().isLoaded(\"adchimneys\")"));
        assertFalse(tick.contains("CampfireBlockEntity.particleTick"));
        assertFalse(tick.contains("applyWindForce"));
        String mixin = Files.readString(Path.of("src/main/java/com/bettercontent/spiritcommerce/mixin/client/SpiritSmokeSafetyMixin.java"));
        assertTrue(mixin.contains("instanceof SpiritCampfireBlock"));
        assertTrue(mixin.contains("if (betterSpiritCommerce$harmless) ci.cancel()"));
        for (var kind : com.bettercontent.spiritcommerce.spirit.SpiritKind.values()) {
            String name = kind.id() + "_campfire";
            assertNotNull(getClass().getResource("/assets/better_spirit_commerce/blockstates/" + name + ".json"));
            assertNotNull(getClass().getResource("/assets/better_spirit_commerce/particles/" + kind.id() + "_flame.json"));
            assertNull(getClass().getResource("/data/better_spirit_commerce/recipes/" + name + ".json"), "No crafting acquisition");
        }
    }
}
