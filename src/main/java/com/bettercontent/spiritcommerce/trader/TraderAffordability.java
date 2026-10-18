package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.config.EconomyPolicy;
import com.bettercontent.spiritcommerce.spirit.CurrencyIdentity;
import com.sammy.malum.common.item.curiosities.SpiritPouchItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import top.theillusivec4.curios.api.CuriosApi;

/** Low early-game admission threshold, using carried funds without consuming them. */
public final class TraderAffordability {
    public static final int ARRIVAL_SPIRITS = 10;

    private TraderAffordability() { }

    public static boolean hasArrivalFunds(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return false;
        long total = 0;
        for (long count : carriedSpirits(player)) total += count;
        return total >= ARRIVAL_SPIRITS;
    }

    public static List<WanderingTraderTheme> affordableThemes(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return List.of();
        long[] counts = carriedSpirits(player);
        long total = 0;
        for (long count : counts) total += count;
        if (total < ARRIVAL_SPIRITS) return List.of();
        List<WanderingTraderTheme> themes = new ArrayList<>();
        for (WanderingTraderTheme theme : WanderingTraderTheme.values()) {
            int cheapest = EconomyPolicy.wanderingRows(theme.spirit()).stream()
                    .filter(row -> ForgeRegistries.ITEMS.containsKey(new ResourceLocation(row.result().id())))
                    .mapToInt(EconomyPolicy.WanderingRow::cost).min().orElse(Integer.MAX_VALUE);
            if (counts[theme.currencyIdentity().ordinal()] >= cheapest) themes.add(theme);
        }
        return themes;
    }

    static long[] carriedSpirits(ServerPlayer player) {
        long[] counts = new long[CurrencyIdentity.values().length];
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            countStack(player.getInventory().getItem(slot), counts);
        }
        CuriosApi.getCuriosHelper().findCurios(player, stack -> stack.getItem() instanceof SpiritPouchItem).stream()
                .filter(result -> !result.slotContext().cosmetic() && result.slotContext().entity() == player)
                .forEach(result -> countStack(result.stack(), counts));
        return counts;
    }

    private static void countStack(ItemStack stack, long[] counts) {
        CurrencyIdentity identity = CurrencyIdentity.fromItemId(ForgeRegistries.ITEMS.getKey(stack.getItem()));
        if (identity != null) counts[identity.ordinal()] += stack.getCount();
        if (stack.getItem() instanceof SpiritPouchItem) {
            var inventory = SpiritPouchItem.getInventory(stack);
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack spirit = inventory.getItem(slot);
                CurrencyIdentity stored = CurrencyIdentity.fromItemId(ForgeRegistries.ITEMS.getKey(spirit.getItem()));
                if (stored != null) counts[stored.ordinal()] += spirit.getCount();
            }
        }
    }
}
