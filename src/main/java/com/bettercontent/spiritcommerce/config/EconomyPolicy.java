package com.bettercontent.spiritcommerce.config;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;

/** The one loaded and validated authority for acquisition, commerce rows, and retired surfaces. */
public final class EconomyPolicy {
    public static final String SCHEMA = "bc.economy_policy.v4";
    private static final Document DOCUMENT = load();
    private static final Set<ResourceLocation> RETIRED = DOCUMENT.retiredItems().stream()
            .map(ResourceLocation::new).collect(Collectors.toUnmodifiableSet());
    private static final Map<SpiritKind, List<VillagerRow>> VILLAGERS = groupVillagers();
    private static final Map<SpiritKind, List<WanderingRow>> WANDERERS = groupWanderers();

    private EconomyPolicy() {}

    public static Acquisition acquisition() { return DOCUMENT.acquisition(); }
    public static Set<ResourceLocation> retiredItems() { return RETIRED; }
    public static boolean isRetired(final ResourceLocation id) { return RETIRED.contains(id); }
    public static List<VillagerRow> villagerRows(final SpiritKind spirit) { return VILLAGERS.get(spirit); }
    public static List<WanderingRow> wanderingRows(final SpiritKind spirit) { return WANDERERS.get(spirit); }
    public static List<PlagueDoctorRow> plagueDoctorRows() { return java.util.stream.Stream.concat(DOCUMENT.plagueDoctorRows().stream(), tempoDoctorRows().stream()).toList(); }
    public static int villagerRowCount() { return 280; }
    public static int wanderingRowCount() { return WANDERERS.values().stream().mapToInt(List::size).sum(); }
    public static int plagueDoctorRowCount() { return 48; }

    private static Document load() {
        var stream = EconomyPolicy.class.getResourceAsStream(
                "/data/better_spirit_commerce/spiritcommerce/spirit_economy_policy.json");
        try (var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            Document document = new Gson().fromJson(reader, Document.class);
            validate(document);
            return document;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load Better Content spirit economy policy", exception);
        }
    }

    private static void validate(final Document document) {
        if (document == null || !SCHEMA.equals(document.schema())) throw new IllegalArgumentException("Unsupported economy policy schema");
        if (document.legacyCurrencyMapping() == null || document.legacyCurrencyMapping().size() != 7) {
            throw new IllegalArgumentException("Economy policy must map the seven legacy native spirits once");
        }
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            if (identity.legacyNativeSpirit() == null) continue;
            String mapped = document.legacyCurrencyMapping().get(identity.legacyNativeSpirit().toString());
            if (!identity.id().equals(mapped)) {
                throw new IllegalArgumentException("Incorrect legacy currency mapping for " + identity.legacyNativeSpirit());
            }
        }
        if (document.acquisition() == null || !document.acquisition().creditedPlayerKillsOnly()
                || !document.acquisition().excludeSpawnerOrigin() || !document.acquisition().excludeEconomyActors()
                || document.acquisition().unmappedHostileSpiritCount() != 2) {
            throw new IllegalArgumentException("Economy acquisition must be player-kill-only with two-spirit hostile fallback");
        }
        if (document.retiredItems() == null || document.retiredItems().size() != 24
                || new HashSet<>(document.retiredItems()).size() != 24) {
            throw new IllegalArgumentException("Economy policy must list exactly 24 unique retired items");
        }
        if (document.villagerRows() == null || document.villagerRows().size() != 245) {
            throw new IllegalArgumentException("Economy policy must contain exactly 245 villager rows");
        }
        if (document.wanderingRows() == null || document.wanderingRows().size() != 91) {
            throw new IllegalArgumentException("Economy policy must contain exactly 91 wandering goods");
        }
        if (document.plagueDoctorRows() == null || document.plagueDoctorRows().size() != 42) {
            throw new IllegalArgumentException("Economy policy must contain exactly 42 plague doctor oddities");
        }
        for (SpiritKind spirit : SpiritKind.values()) {
            if (spirit == SpiritKind.TEMPO) continue;
            List<VillagerRow> villagers = document.villagerRows().stream().filter(row -> row.kind() == spirit).toList();
            if (villagers.size() != 35) throw new IllegalArgumentException(spirit.id() + " must have 35 villager rows");
            for (int level = 1; level <= 5; level++) {
                final int currentLevel = level;
                if (villagers.stream().filter(row -> row.level() == currentLevel).count() != 7) {
                    throw new IllegalArgumentException(spirit.id() + " must have seven offers at level " + level);
                }
            }
            List<WanderingRow> wanderers = document.wanderingRows().stream().filter(row -> row.kind() == spirit).toList();
            if (wanderers.size() != 13) throw new IllegalArgumentException(spirit.id() + " must have 13 wandering goods");
            List<PlagueDoctorRow> oddities = document.plagueDoctorRows().stream().filter(row -> row.kind() == spirit).toList();
            if (oddities.size() != 6) throw new IllegalArgumentException(spirit.id() + " must have six plague doctor oddities");
        }
        document.villagerRows().forEach(EconomyPolicy::validateRow);
        document.wanderingRows().forEach(EconomyPolicy::validateRow);
        document.plagueDoctorRows().forEach(EconomyPolicy::validatePlagueDoctorRow);
    }

    private static void validateRow(final TradeRow row) {
        if (row.kind() == null || row.cost() < 1 || row.cost() > 8 || row.result() == null
                || row.result().id() == null || row.result().id().isBlank() || row.result().count() < 1
                || row.maxUses() < 1 || row.xp() < 0) throw new IllegalArgumentException("Invalid spirit trade row");
        new ResourceLocation(row.result().id());
    }

    private static void validatePlagueDoctorRow(final PlagueDoctorRow row) {
        validateRow(row);
        if (row.result().count() != 1 || row.maxUses() != plagueDoctorMaxUses(row.cost())
                || row.xp() != row.cost() * 2) {
            throw new IllegalArgumentException("Invalid plague doctor stock policy for " + row.result().id());
        }
    }

    public static int plagueDoctorMaxUses(final int cost) {
        if (cost <= 2) return 6;
        if (cost <= 4) return 3;
        if (cost <= 6) return 2;
        return 1;
    }

    private static Map<SpiritKind, List<VillagerRow>> groupVillagers() {
        Map<SpiritKind, List<VillagerRow>> rows = new EnumMap<>(SpiritKind.class);
        for (SpiritKind kind : SpiritKind.values()) {
            rows.put(kind, kind == SpiritKind.TEMPO ? tempoVillagerRows()
                    : DOCUMENT.villagerRows().stream().filter(row -> row.kind() == kind).toList());
        }
        return Map.copyOf(rows);
    }

    private static Map<SpiritKind, List<WanderingRow>> groupWanderers() {
        Map<SpiritKind, List<WanderingRow>> rows = new EnumMap<>(SpiritKind.class);
        for (SpiritKind kind : SpiritKind.values()) {
            var goods = new java.util.ArrayList<>(kind == SpiritKind.TEMPO ? tempoWanderingRows()
                    : DOCUMENT.wanderingRows().stream().filter(row -> row.kind() == kind).toList());
            goods.add(new WanderingRow(kind.id(), 1,
                    new StackSpec("better_spirit_commerce:" + kind.id() + "_campfire", 1), 16, 0));
            rows.put(kind, List.copyOf(goods));
        }
        return Map.copyOf(rows);
    }

    private static List<VillagerRow> tempoVillagerRows() {
        String[] results = {"minecraft:clock", "minecraft:compass", "minecraft:repeater", "minecraft:comparator", "minecraft:redstone", "minecraft:redstone_torch", "minecraft:daylight_detector", "minecraft:observer", "minecraft:piston", "minecraft:sticky_piston", "minecraft:slime_ball", "minecraft:honey_bottle", "minecraft:sugar", "minecraft:rabbit_foot", "minecraft:feather", "minecraft:ender_pearl", "minecraft:chorus_fruit", "minecraft:firework_rocket", "minecraft:oak_boat", "minecraft:minecart", "minecraft:rail", "minecraft:powered_rail", "minecraft:detector_rail", "minecraft:activator_rail", "minecraft:tripwire_hook", "minecraft:lead", "minecraft:saddle", "minecraft:carrot_on_a_stick", "minecraft:warped_fungus_on_a_stick", "minecraft:map", "minecraft:recovery_compass", "minecraft:spyglass", "minecraft:lightning_rod", "minecraft:amethyst_shard", "minecraft:echo_shard"};
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
        return DOCUMENT.villagerRows().stream().filter(row -> row.kind() == SpiritKind.ARCANE)
                .map(row -> new VillagerRow("tempo", row.level(), row.cost(),
                        new StackSpec(results[index.getAndIncrement()], row.result().count()), row.maxUses(), row.xp())).toList();
    }
    private static List<WanderingRow> tempoWanderingRows() {
        String[] results = {"minecraft:clock", "minecraft:repeater", "minecraft:comparator", "minecraft:observer", "minecraft:slime_ball", "minecraft:ender_pearl", "minecraft:chorus_fruit", "minecraft:firework_rocket", "minecraft:rail", "minecraft:powered_rail", "minecraft:lead", "minecraft:saddle", "minecraft:spyglass"};
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
        return DOCUMENT.wanderingRows().stream().filter(row -> row.kind() == SpiritKind.ARCANE)
                .map(row -> new WanderingRow("tempo", row.cost(),
                        new StackSpec(results[index.getAndIncrement()], row.result().count()), row.maxUses(), row.xp())).toList();
    }
    private static List<PlagueDoctorRow> tempoDoctorRows() {
        String[] results = {"minecraft:rabbit_foot", "minecraft:chorus_fruit", "minecraft:ender_pearl", "minecraft:echo_shard", "minecraft:recovery_compass", "minecraft:clock"};
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger();
        return DOCUMENT.plagueDoctorRows().stream().filter(row -> row.kind() == SpiritKind.ARCANE)
                .map(row -> new PlagueDoctorRow("tempo", row.cost(),
                        new StackSpec(results[index.getAndIncrement()], 1), row.maxUses(), row.xp())).toList();
    }

    private interface TradeRow {
        String spirit();
        int cost();
        StackSpec result();
        int maxUses();
        int xp();
        default SpiritKind kind() { return SpiritKind.fromId(spirit()); }
    }

    public record Acquisition(boolean creditedPlayerKillsOnly, int unmappedHostileSpiritCount,
                              boolean excludeSpawnerOrigin, boolean excludeEconomyActors) {}
    public record StackSpec(String id, int count) {}
    public record VillagerRow(String spirit, int level, int cost, StackSpec result, int maxUses, int xp)
            implements TradeRow {}
    public record WanderingRow(String spirit, int cost, StackSpec result, int maxUses, int xp)
            implements TradeRow {}
    public record PlagueDoctorRow(String spirit, int cost, StackSpec result, int maxUses, int xp)
            implements TradeRow {}
    private record Document(String schema, Map<String, String> legacyCurrencyMapping, Acquisition acquisition, List<String> retiredItems,
                            List<VillagerRow> villagerRows, List<WanderingRow> wanderingRows,
                            List<PlagueDoctorRow> plagueDoctorRows) {}
}
