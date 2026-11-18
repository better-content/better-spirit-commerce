package com.bettercontent.spiritcommerce.registry;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
final class CurrencyIdentityCueTest {
    @Test void everyOrdinaryIdentityHasTextCueAndDedicatedItemImplementation() throws Exception {
        String lang = Files.readString(Path.of("src/main/resources/assets/better_spirit_commerce/lang/en_us.json"));
        String source = Files.readString(Path.of("src/main/java/com/bettercontent/spiritcommerce/registry/CurrencyItems.java"));
        for (CurrencyIdentity identity : CurrencyIdentity.values()) assertTrue(lang.contains("item.better_spirit_commerce." + identity.id() + "_spirit"));
        assertTrue(source.contains("new CurrencyItem(identity)"));
        String item = Files.readString(Path.of("src/main/java/com/bettercontent/spiritcommerce/registry/CurrencyItem.java"));
        assertTrue(item.contains("identity.id()"));
        var translations = JsonParser.parseString(lang).getAsJsonObject();
        var cues = new HashSet<String>();
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            String cue = translations.get("tooltip.better_spirit_commerce." + identity.id()).getAsString();
            assertTrue(cue.contains(identity.id().substring(0, 1).toUpperCase() + identity.id().substring(1)));
            assertTrue(cue.toLowerCase(java.util.Locale.ROOT).contains("trade"));
            cues.add(cue);
        }
        assertTrue(cues.size() == CurrencyIdentity.values().length);
    }
}
