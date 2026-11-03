package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Binds the native-only overlay to exact broken upstream resources and existing authored pack fixes. */
final class NativeFixtureResourceTest {
    private static final Map<String, String> NATIVE = Map.of(
            "impetus_creation_zinc", "cd42713472f59e57043d7e63e620d3031ea0374fabb8199ba6deb438875ce6ff",
            "node_focusing_zinc", "5e1ec036207d3f65c4cdccd40b2a6824277970bc6f304bd3aede0774b1b11fe9",
            "zinc_from_node_smelting", "5d127eb837422fc894ba3edfafe0628fda9ac147986a9f8ac777901d9421072e",
            "zinc_from_node_blasting", "5d127eb837422fc894ba3edfafe0628fda9ac147986a9f8ac777901d9421072e");
    private static final String AUTHORED = "3040901c6c74b67c2182bfc63ef29609ba4200890b9ff23892f27294d048cd5e";

    @Test
    void nativeProfileUsesExactExistingAuthoredFixesForPinnedMalformedAdvancements() throws Exception {
        for (var entry : NATIVE.entrySet()) {
            String resource = "data/malum/advancements/recipes/" + entry.getKey() + ".json";
            byte[] upstream;
            try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(stream, "Pinned Malum runtime input missing: " + resource);
                upstream = stream.readAllBytes();
            }
            assertEquals(entry.getValue(), sha256(upstream), "Pinned native advancement changed: " + resource);
            var conditional = JsonParser.parseString(new String(upstream, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("advancements").get(0).getAsJsonObject();
            assertTrue(!conditional.has("advancement")
                    || conditional.getAsJsonObject("advancement").getAsJsonObject("criteria").size() == 0,
                    "Overlay must not hide a newly repaired upstream advancement");
            byte[] authored = Files.readAllBytes(Path.of("../../better-content-modpack/kubejs", resource));
            assertEquals(AUTHORED, sha256(authored), "Authored pack fix changed; review the native profile input");
            assertArrayEquals(authored, Files.readAllBytes(Path.of("build/resources/nativeFixture", resource)),
                    "Native profile must materialize exact authored bytes, not generated suppression");
            assertFalse(Files.exists(Path.of("src/main/resources", resource)), "Pack fix must not ship in Commerce");
        }
    }

    @Test
    void crushingProfileUsesExactAuthoredSevenOutputFix() throws Exception {
        String resource = "data/malum/recipes/create/crushing/crush_rare_earths.json";
        byte[] upstream;
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            upstream = stream.readAllBytes();
        }
        assertEquals("edef9364753fea6c6e3034bab5a6680004e0081c32c800185c4e0d47b286d5aa", sha256(upstream));
        assertEquals(8, JsonParser.parseString(new String(upstream, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("results").size());
        byte[] authored = Files.readAllBytes(Path.of("../../better-content-modpack/kubejs", resource));
        assertEquals("d6df27189a97394c28173ac952458aafb91a474ec806e602d9c5394a40be9e80", sha256(authored));
        assertEquals(7, JsonParser.parseString(new String(authored, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("results").size());
        assertArrayEquals(authored, Files.readAllBytes(Path.of("build/resources/nativeFixture", resource)));
    }

    @Test
    void jeedDevDependencyMatchesCommittedPackPin() throws Exception {
        String packPin = Files.readString(Path.of("../../better-content-modpack/mods/just-enough-effect-descriptions-jeed.pw.toml"));
        assertTrue(packPin.contains("file-id = 6172935") && packPin.contains("project-id = 532286")
                && packPin.contains("9f6b1884ad8b81c0e8796daf7871eeb54271f27b"));
        String build = Files.readString(Path.of("build.gradle.kts"));
        assertTrue(build.contains("runtimeOnly(fg.deobf(\"curse.maven:jeed-532286:6172935\"))"));
        String jeiPin = Files.readString(Path.of("../../better-content-modpack/mods/jei.pw.toml"));
        assertTrue(jeiPin.contains("file-id = 7920915") && jeiPin.contains("project-id = 238222")
                && jeiPin.contains("1c51bf6df51ffbb1c5efe23780a7a3d053421f34"));
        assertTrue(build.contains("runtimeOnly(fg.deobf(\"curse.maven:jei-238222:7920915\"))"));
    }

    @Test
    void startupTickEvidenceRemainsInTheNonshippingNativeFixture() throws Exception {
        String relative = "com/bettercontent/spiritcommerce/nativefixture/StartupTickBoundary";
        Path source = Path.of("src/nativeFixture/java", relative + ".java");
        assertTrue(Files.isRegularFile(source));
        assertFalse(Files.exists(Path.of("src/main/java", relative + ".java")));
        assertFalse(Files.exists(Path.of("build/classes/java/main", relative + ".class")),
                "Native evidence markers must never enter the production class output");
        String code = Files.readString(source);
        assertTrue(code.contains("forge.gameTestServer") && code.contains("instanceof GameTestServer"));
        assertTrue(code.contains("TickEvent.ServerTickEvent"));
        assertTrue(code.contains("mark(\"START_1\")"));
        assertTrue(code.contains("mark(\"END_1\")"));
        assertTrue(code.contains("mark(\"START_2\")"));
        assertTrue(code.contains("native-diagnostic-nonce"));
        assertTrue(code.contains("starts <= 3"));
        assertTrue(code.contains("SPIRIT_NATIVE_TICK_TIMING nonce={}"));
        assertTrue(code.contains("Util.getNanos()") && code.contains("Util.getMillis()"));
        assertTrue(code.contains("current.getTickCount()") && code.contains("current.getNextTickTime()"));
        assertFalse(code.contains("nextTickTime ="), "Evidence must not reset the server schedule");
        assertTrue(code.contains("UUID.fromString(nonce).toString().equals(nonce)"));
    }

    private static String sha256(final byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
