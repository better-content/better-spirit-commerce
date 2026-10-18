package com.bettercontent.spiritcommerce.config;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/** Authored wandering goods and import ceilings, independent of resident barter and acquisition. */
public final class WanderingPool {
    public static final String SCHEMA = "bc.wandering_pool.v1";
    public static final int GOODS_PER_VISIT = 12;
    public static final int CORE_OFFERS_PER_VISIT = 13;
    private static final Document DOCUMENT = load();
    private static final Map<SpiritKind, List<Row>> ROWS = groupRows();

    private WanderingPool() {}

    public static List<Row> rows(final SpiritKind kind) { return ROWS.get(kind); }
    public static List<CommodityStock> commodityStocks() { return List.copyOf(DOCUMENT.commodityStocks()); }
    public static int rowCount() { return DOCUMENT.rows().size(); }

    private static Document load() {
        var stream = WanderingPool.class.getResourceAsStream(
                "/data/better_spirit_commerce/spiritcommerce/wandering_pool.json");
        try (var reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            Document document = new Gson().fromJson(reader, Document.class);
            validate(document);
            return document;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load authored wandering pool", exception);
        }
    }

    static void validate(final Document document) {
        if (document == null || !SCHEMA.equals(document.schema()) || document.rows() == null
                || document.rows().size() != 288 || document.commodityStocks() == null) {
            throw new IllegalArgumentException("Unsupported or incomplete wandering pool");
        }
        var stocks = new HashSet<String>();
        for (CommodityStock stock : document.commodityStocks()) {
            if (stock.item() == null || stock.units() <= 0 || stock.scope() == null
                    || !stocks.add(stock.item())) throw new IllegalArgumentException("Invalid commodity ceiling");
            new ResourceLocation(stock.item());
        }
        var ids = new HashSet<String>();
        var outputs = new HashSet<String>();
        for (Row row : document.rows()) {
            if (row == null || row.id() == null || row.id().isBlank() || !ids.add(row.id())
                    || row.bucket() == null || row.cost() < 1 || row.cost() > 64 || row.maxUses() < 1
                    || row.xp() != 0 || row.result() == null || row.result().id() == null
                    || row.result().count() < 1 || row.result().count() > 64
                    || "minecraft:air".equals(row.result().id()) || !outputs.add(row.result().id())) {
                throw new IllegalArgumentException("Invalid or duplicate wandering offer: " + row);
            }
            row.kind(); // Strict identity validation; no default-theme fallback.
            new ResourceLocation(row.result().id());
            if (row.renewable() == stocks.contains(row.result().id())) {
                throw new IllegalArgumentException("Renewable/finite stock mismatch: " + row.id());
            }
            if (row.bucket() == Bucket.CAMPFIRE && (!row.result().id().equals(
                    "better_spirit_commerce:" + row.spirit() + "_campfire")
                    || row.cost() != 1 || row.result().count() != 1 || row.maxUses() != 16)) {
                throw new IllegalArgumentException("Invalid matching campfire: " + row.id());
            }
        }
        for (SpiritKind kind : SpiritKind.values()) {
            List<Row> rows = document.rows().stream().filter(row -> row.kind() == kind).toList();
            if (rows.size() != 36) throw new IllegalArgumentException("Expected 36 pool rows for " + kind);
            for (Bucket bucket : Bucket.values()) {
                long count = rows.stream().filter(row -> row.bucket() == bucket).count();
                if (count < bucket.picks() || (bucket == Bucket.STAPLE || bucket == Bucket.CAMPFIRE)
                        && count != bucket.picks()) {
                    throw new IllegalArgumentException("Invalid " + bucket + " capacity for " + kind);
                }
            }
        }
    }

    private static Map<SpiritKind, List<Row>> groupRows() {
        var result = new EnumMap<SpiritKind, List<Row>>(SpiritKind.class);
        for (SpiritKind kind : SpiritKind.values()) {
            result.put(kind, DOCUMENT.rows().stream().filter(row -> row.kind() == kind).toList());
        }
        return Map.copyOf(result);
    }

    public enum Bucket {
        STAPLE(4), SUPPLY(4), EQUIPMENT(3), STANDOUT(1), CAMPFIRE(1);
        private final int picks;
        Bucket(final int picks) { this.picks = picks; }
        public int picks() { return picks; }
    }

    public enum StockScope { ALL_MERCHANTS, SPIRIT_SALES }

    public record Row(String id, String spirit, Bucket bucket, int cost, EconomyPolicy.StackSpec result,
                      int maxUses, int xp, boolean renewable) {
        public SpiritKind kind() {
            for (SpiritKind kind : SpiritKind.values()) if (kind.id().equals(spirit)) return kind;
            throw new IllegalArgumentException("Unknown pool theme: " + spirit);
        }
        public EconomyPolicy.WanderingRow trade() {
            return new EconomyPolicy.WanderingRow(spirit, cost, result, maxUses, xp);
        }
    }

    public record CommodityStock(String item, int units, StockScope scope) {}
    record Document(String schema, List<Row> rows, List<CommodityStock> commodityStocks) {}
}
