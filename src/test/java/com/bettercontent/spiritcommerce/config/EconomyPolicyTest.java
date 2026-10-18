package com.bettercontent.spiritcommerce.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class EconomyPolicyTest {
    @Test void centralPolicyOwnsAllCommerceRowsAndRetiredItems() {
        assertEquals(280, EconomyPolicy.villagerRowCount());
        assertEquals(112, EconomyPolicy.wanderingRowCount());
        assertEquals(48, EconomyPolicy.plagueDoctorRowCount());
        assertEquals(24, EconomyPolicy.retiredItems().size());
        assertTrue(EconomyPolicy.isRetired(new ResourceLocation("createdeco:copper_coin")));
        assertFalse(EconomyPolicy.isRetired(new ResourceLocation("malum:eldritch_spirit")));
        assertEquals("bc.economy_policy.v4", EconomyPolicy.SCHEMA);
        assertEquals(CurrencyIdentity.RENEWAL,
                CurrencyIdentity.fromLegacyNativeSpirit(new ResourceLocation("malum:sacred_spirit")));
        assertEquals(8, SpiritKind.values().length);
        for (SpiritKind kind : SpiritKind.values()) {
            assertEquals(35, EconomyPolicy.villagerRows(kind).size());
            assertEquals(14, EconomyPolicy.wanderingRows(kind).size());
            assertEquals(6, EconomyPolicy.plagueDoctorRows().stream().filter(row -> row.kind() == kind).count());
        }
        assertTrue(EconomyPolicy.plagueDoctorRows().stream()
                .anyMatch(row -> "rats:plague_tome".equals(row.result().id()) && row.cost() == 8));
        assertTrue(EconomyPolicy.plagueDoctorRows().stream().allMatch(row ->
                row.maxUses() == EconomyPolicy.plagueDoctorMaxUses(row.cost()) && row.xp() == row.cost() * 2));
    }
}
