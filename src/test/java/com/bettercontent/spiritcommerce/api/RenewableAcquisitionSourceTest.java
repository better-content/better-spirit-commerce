package com.bettercontent.spiritcommerce.api;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source-wiring contracts complement pure policy tests; not native trade acceptance. */
class RenewableAcquisitionSourceTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of("src/main/java/com/bettercontent/spiritcommerce/"+relative));
    }
    @Test void residentInterceptionIsRemovedButMerchantPolicyRemains() throws Exception {
        String main=source("BetterSpiritCommerce.java");
        assertFalse(main.contains("ResidentNetwork.register()"));
        assertFalse(main.contains("PlayerStallRegistries.register("));
        assertFalse(main.contains("SettlementGeneration.class"));
        String merchant=source("mixin/AbstractVillagerMixin.java");
        assertFalse(merchant.contains("ResidentRules"));
        assertFalse(merchant.contains("offers.clear()"));
        assertTrue(merchant.contains("MerchantCurrencyPolicy.normalize(offers);"));
        assertTrue(merchant.contains("VillagerCatalogue.ensureFontOfferIfAvailable(villager, offers);"));
    }
    @Test void nativeMerchantOwnershipIsExplicitAndNoOwnerlessOfferMixinExists() throws Exception {
        String facade=source("api/RenewableAcquisitionPolicy.java");
        assertTrue(facade.contains("merchant instanceof net.minecraft.world.entity.npc.AbstractVillager"));
        assertTrue(facade.contains("!merchant.isClientSide()"));
        String slot=source("mixin/NpcMerchantResultSlotMixin.java");
        assertTrue(slot.contains("@Shadow @Final private Merchant merchant"));
        assertTrue(slot.contains("@Override public boolean mayPickup"));
        assertTrue(slot.contains("@Inject(method = \"onTake\", at = @At(\"HEAD\")"));
        assertTrue(slot.contains("RenewableAcquisitionPolicy.allowsMerchant(merchant,offer.getResult()) && offer.take(first,second)"));
        assertFalse(slot.contains("@Mixin(MerchantOffer.class)"));
        assertTrue(source("mixin/NpcMerchantMenuMixin.java").contains("@Inject(method = \"quickMoveStack\", at = @At(\"HEAD\")"));
        assertTrue(source("mixin/NpcMerchantContainerMixin.java").contains("itemStacks.set(2,ItemStack.EMPTY); activeOffer = null; futureXp = 0"));
    }
    @Test void listingAndDailyGenerationGuardsDoNotResetPermittedWanderingStock() throws Exception {
        String main=source("mixin/AbstractVillagerMixin.java");
        int font=main.indexOf("ensureFontOfferIfAvailable("), finalGuard=main.lastIndexOf("offers.removeIf(");
        assertTrue(font>=0 && finalGuard>font);
        assertTrue(main.indexOf("MerchantCurrencyPolicy.normalize(offers);")>=0);
        assertFalse(main.contains("ResidentRules"));
        String wandering=source("trader/WanderingTraderCatalogue.java");
        int selected=wandering.indexOf("List<Row> selected = selectedRows(trader, spirit);");
        int guard=wandering.indexOf("offers.removeIf(offer -> !RenewableAcquisitionPolicy.allowsMerchant(trader, offer.getResult()))");
        int check=wandering.indexOf("if (!hasSelectedCatalogue(offers, selected, payment)) {");
        int rebuildCall=wandering.indexOf("rebuildPreservingUses(offers, selected, payment, spirit);");
        assertTrue(selected>=0 && guard>selected && check>guard && rebuildCall>check);
        assertTrue(wandering.contains("if (!hasSelectedCatalogue(offers, selected, payment)) {\n            rebuildPreservingUses(offers, selected, payment, spirit);"));

        int selectionStart=wandering.indexOf("static List<Row> selectedRows(");
        int outputStart=wandering.indexOf("private static ItemStack outputFor(");
        int existingStart=wandering.indexOf("private static boolean hasSelectedCatalogue(");
        int nativeStart=wandering.indexOf("private static boolean validNativeState(");
        int rebuildStart=wandering.indexOf("private static void rebuildPreservingUses(");
        int fontStart=wandering.indexOf("static boolean appendFontOfferIfAvailable(");
        assertTrue(selectionStart>=0 && outputStart>selectionStart && existingStart>outputStart
                && nativeStart>existingStart && rebuildStart>nativeStart && fontStart>rebuildStart);
        String selectionBody=wandering.substring(selectionStart,outputStart);
        int selectedOutput=selectionBody.indexOf("ItemStack output = outputFor(row);");
        int selectedPolicy=selectionBody.indexOf("RenewableAcquisitionPolicy.allows(output)");
        assertTrue(selectedOutput>=0 && selectedPolicy>selectedOutput);
        String outputBody=wandering.substring(outputStart,existingStart);
        assertTrue(outputBody.contains("putByte(\"Flight\", (byte) 1)"));
        assertTrue(outputBody.contains("remove(\"Explosions\")"));
        String existingBody=wandering.substring(existingStart,nativeStart);
        assertTrue(existingBody.contains("!validNativeState(offer.getResult())"));
        assertFalse(existingBody.contains("getUses()"));
        String nativeBody=wandering.substring(nativeStart,rebuildStart);
        assertTrue(nativeBody.contains("getByte(\"Flight\") == 1"));
        assertTrue(nativeBody.contains("!stack.getTag().getCompound(\"Fireworks\").contains(\"Explosions\")"));
        String rebuildBody=wandering.substring(rebuildStart,fontStart);
        int rebuiltOutput=rebuildBody.indexOf("ItemStack output = outputFor(row);");
        int rebuiltPolicy=rebuildBody.indexOf("RenewableAcquisitionPolicy.allows(output)");
        assertTrue(rebuiltOutput>=0 && rebuiltPolicy>rebuiltOutput);
        int spentMerge=rebuildBody.indexOf("spentUnits.merge(id, (long) Math.max(0, old.getUses()) * old.getResult().getCount(), Long::sum);");
        int clear=rebuildBody.indexOf("offers.clear();");
        int spentLookup=rebuildBody.indexOf("spentUnits.getOrDefault(ForgeRegistries.ITEMS.getKey(output.getItem()), 0L)");
        int cappedUses=rebuildBody.indexOf("Math.min(row.maxUses(), (spent + row.result().count() - 1) / row.result().count())");
        int newOffer=rebuildBody.indexOf("output, uses, row.maxUses(), row.xp()");
        assertTrue(spentMerge>=0 && clear>spentMerge && spentLookup>clear && cappedUses>spentLookup && newOffer>cappedUses);
        assertTrue(source("trader/PlagueDoctorCatalogue.java").contains("!RenewableAcquisitionPolicy.allows(new ItemStack(result,row.result().count()))"));
    }
}
