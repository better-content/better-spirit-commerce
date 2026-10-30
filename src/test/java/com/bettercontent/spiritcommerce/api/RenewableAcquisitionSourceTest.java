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
    @Test void actualRegisteredResidentEndpointsReachGuardedProductionNotObsoleteVillagerCatalogue() throws Exception {
        String main=source("BetterSpiritCommerce.java"), network=source("resident/ResidentNetwork.java");
        assertTrue(main.contains("EVENT_BUS.register(ResidentService.class)"));
        assertTrue(main.contains("EVENT_BUS.register(ResidentInteractions.class)"));
        assertTrue(network.contains("ResidentBarter.offers(")); assertTrue(network.contains("ResidentBarter.execute("));
        String barter=source("resident/ResidentBarter.java");
        int gate=barter.indexOf("RenewableAcquisitionPolicy.commitIfAllowed(plan.generated()");
        assertTrue(gate>=0 && gate<barter.indexOf("takePayment.getAsBoolean()"));
        assertTrue(barter.contains("work.generated.add(remainder.copy())"));
        assertTrue(barter.contains("work.generated.add(result.copy())"));
    }
    @Test void finiteStockBypassesProductionAndPlayerStockedStallCodeRemainsUnfiltered() throws Exception {
        String barter=source("resident/ResidentBarter.java");
        assertTrue(barter.contains("new Plan(stock, null, List.of(), List.of(),"));
        for (String file : new String[]{"resident/ResidentState.java","resident/PlayerStallTrade.java","resident/PlayerStallBlockEntity.java"})
            assertFalse(source(file).contains("RenewableAcquisitionPolicy"),file);
        assertTrue(source("resident/PlayerStallTrade.java").contains("ResidentBarter.paymentPlan("));
    }
    @Test void sourceOutputVetoPrecedesCropWorldFuelWaterAndToolDebits() throws Exception {
        String service=source("resident/ResidentService.java");
        String crop=service.substring(service.indexOf("private static void harvestCrop"),service.indexOf("private static void harvestBlock"));
        assertTrue(crop.indexOf("RenewableAcquisitionPolicy.allowsAll(drops)")<crop.indexOf("seed.shrink(1)"));
        String block=service.substring(service.indexOf("private static void harvestBlock"),service.indexOf("private static void harvestTree"));
        assertTrue(block.indexOf("RenewableAcquisitionPolicy.allowsAll(drops)")<block.indexOf("equipFor("));
        assertTrue(block.indexOf("equipFor(")<block.indexOf("level.destroyBlock("));
        String tree=service.substring(service.indexOf("private static void harvestTree"),service.indexOf("static boolean addGenerated"));
        assertTrue(tree.contains("stagedDrops.stream().flatMap(List::stream)"));
        assertTrue(tree.indexOf("stagedDrops.stream().flatMap(List::stream)")<tree.indexOf("equipFor("));
        assertTrue(service.contains("if (!RenewableAcquisitionPolicy.allows(result)) return;\n                if (!state.remove(input.copyWithCount(1))"));
        assertTrue(service.contains("if (!RenewableAcquisitionPolicy.allows(filled)) return false;\n            if (!state.remove(new ItemStack(Items.GLASS_BOTTLE)))"));
    }
    @Test void bothInitialResidentSeedOutletsUseGenerationGate() throws Exception {
        String service=source("resident/ResidentService.java"), settlements=source("resident/SettlementGeneration.java");
        assertTrue(service.contains("addGenerated(state, new ItemStack(Items.CARROT, 3))"));
        assertTrue(service.contains("addGenerated(state, new ItemStack(Items.STONE_AXE))"));
        assertTrue(settlements.contains("ResidentService.addGenerated(state, new ItemStack(Items.CARROT, 8))"));
        assertTrue(settlements.contains("ResidentService.addGenerated(state, ResidentRules.waterBottle(3)"));
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
        assertTrue(main.lastIndexOf("offers.removeIf(")>main.indexOf("ensureFontOfferIfAvailable("));
        String wandering=source("trader/WanderingTraderCatalogue.java");
        assertTrue(wandering.indexOf("offers.removeIf(")<wandering.indexOf("boolean cataloguePresent"));
        assertTrue(wandering.contains("rowsFound[index] = output != null && !RenewableAcquisitionPolicy.allows"));
        assertTrue(source("trader/PlagueDoctorCatalogue.java").contains("!RenewableAcquisitionPolicy.allows(new ItemStack(result,row.result().count()))"));
    }
}
