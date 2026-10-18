package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@net.minecraftforge.gametest.GameTestHolder(BetterSpiritCommerce.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WanderingPoolGameTests {
    private WanderingPoolGameTests() {}

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void selectedGoodsAndUsesSurviveReopeningSaveReloadAndForeignOfferRepair(final GameTestHelper helper) {
        WanderingTrader trader = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(2, 2, 2));
        WanderingTraderVisits.applyTheme(trader, WanderingTraderTheme.ARCANE, false);
        var selected = WanderingTraderCatalogue.selectedRows(trader, SpiritKind.ARCANE);
        // Vanilla + Create + Malum in the isolated native profile already provide 12 Work goods.
        helper.assertTrue(selected.size() == 13, "Registered Work pool did not fill 12 goods plus fire");
        var offers = trader.getOffers();
        MerchantOffer torch = offers.stream().filter(o -> o.getResult().is(Items.TORCH)).findFirst().orElseThrow();
        MerchantOffer egg = offers.stream().filter(o -> o.getResult().is(Items.VILLAGER_SPAWN_EGG)).findFirst().orElseThrow();
        torch.increaseUses(); egg.increaseUses();
        trader.getOffers();
        helper.assertTrue(offers.contains(torch) && torch.getUses() == 1 && egg.getUses() == 1,
                "Ordinary reopening reset or rebuilt valid offers");
        // An unrelated listing must not refill existing goods or the separate village starter.
        offers.add(new MerchantOffer(new ItemStack(CurrencyItems.item(CurrencyIdentity.WORK).get()),
                new ItemStack(Items.BREAD), 1, 0, 0.0F));
        trader.getOffers();
        helper.assertTrue(offers.stream().filter(o -> o.getResult().is(Items.TORCH)).findFirst().orElseThrow().getUses() == 1,
                "Catalogue repair refilled an already-used good");
        helper.assertTrue(offers.stream().filter(o -> o.getResult().is(Items.VILLAGER_SPAWN_EGG)).findFirst().orElseThrow().getUses() == 1,
                "Catalogue repair refilled the village starter");
        CompoundTag saved = new CompoundTag();
        trader.saveWithoutId(saved);
        WanderingTrader reloaded = EntityType.WANDERING_TRADER.create(helper.getLevel());
        helper.assertTrue(reloaded != null, "Could not create native reloaded trader");
        reloaded.load(saved);
        helper.assertTrue(trader.getUUID().equals(reloaded.getUUID()), "Native UUID persistence failed");
        helper.assertTrue(selected.equals(WanderingTraderCatalogue.selectedRows(reloaded, SpiritKind.ARCANE)),
                "Save/reload rerolled the basket");
        var restored = reloaded.getOffers();
        helper.assertTrue(restored.stream().filter(o -> o.getResult().is(Items.TORCH)).findFirst().orElseThrow().getUses() == 1,
                "Save/reload refilled the good");
        helper.assertTrue(restored.stream().filter(o -> o.getResult().is(Items.VILLAGER_SPAWN_EGG)).findFirst().orElseThrow().getUses() == 1,
                "Save/reload refilled the village starter");
        helper.assertTrue(restored.stream().noneMatch(o -> o.getResult().is(Items.BREAD)), "Foreign listing survived repair");
        reloaded.discard(); trader.discard(); helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void finiteImportsShareCurrencyIndependentStockButDoNotCapResidentBarter(final GameTestHelper helper) {
        MerchantOffer first = new MerchantOffer(new ItemStack(CurrencyItems.item(CurrencyIdentity.RENEWAL).get(), 3),
                new ItemStack(Items.MILK_BUCKET), 16, 0, 0.0F);
        MerchantOffer otherCurrency = new MerchantOffer(new ItemStack(CurrencyItems.item(CurrencyIdentity.TEMPO).get(), 7),
                new ItemStack(Items.MILK_BUCKET), 16, 0, 0.0F);
        MerchantOffer barter = new MerchantOffer(new ItemStack(Items.WHEAT, 3),
                new ItemStack(Items.MILK_BUCKET), 16, 0, 0.0F);
        AuthoredStockData stock = AuthoredStockData.createForTest(Map.of("minecraft:milk_bucket", 1));
        helper.assertTrue(AuthoredStockPolicy.isFinite(first) && AuthoredStockPolicy.isFinite(otherCurrency),
                "Spirit-paid imports escaped the common commodity cap");
        helper.assertTrue(!AuthoredStockPolicy.isFinite(barter), "Resident goods barter was coupled to spirit imports");
        helper.assertTrue(stock.recordPurchase(first) && !stock.canPurchase(otherCurrency),
                "Another price/currency resurrected exhausted physical stock");
        helper.assertTrue(stock.canPurchase(barter) && stock.recordPurchase(barter), "Independent goods barter was blocked");
        helper.assertTrue(stock.remaining("minecraft:milk_bucket") == 0, "Barter mutated the spirit import balance");
        AuthoredStockData loaded = AuthoredStockData.load(stock.save(new CompoundTag()));
        helper.assertTrue(!loaded.canPurchase(first), "Reload replenished finite imports");
        helper.succeed();
    }
}
