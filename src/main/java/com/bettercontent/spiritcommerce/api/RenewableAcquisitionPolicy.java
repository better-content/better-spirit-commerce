package com.bettercontent.spiritcommerce.api;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Setup-only extension point for NEW resident/NPC-generated outputs, never existing finite stock.
 * No cooking dependency. Register during common setup before any source/output query.
 * Predicates must be pure, server-safe, and return true for outputs they do not restrict.
 * Duplicate/late owners fail visibly; overlapping policies combine conjunctively (any denial wins).
 * Each callback sees an isolated stack; exceptions propagate visibly before transaction/debit.
 */
public final class RenewableAcquisitionPolicy {
    private static final OutputPolicyRegistry<ResourceLocation,ItemStack> POLICIES = new OutputPolicyRegistry<>(ItemStack::copy);
    private RenewableAcquisitionPolicy() {}
    public static void register(ResourceLocation owner, Predicate<ItemStack> allowed) { POLICIES.register(owner,allowed); }
    public static boolean allows(ItemStack output) { return POLICIES.allows(output); }
    public static OutputPolicyRegistry.Decision<ResourceLocation> decision(ItemStack output) { return POLICIES.decision(output); }
    /** Native NPC ownership is explicit; Wares/player-stocked Merchant implementations are not NPCs. */
    public static boolean allowsMerchant(net.minecraft.world.item.trading.Merchant merchant, ItemStack output) {
        return POLICIES.allowsForOwner(merchant instanceof net.minecraft.world.entity.npc.AbstractVillager
                && !merchant.isClientSide(),output);
    }
    public static boolean allowsAll(Iterable<ItemStack> outputs) {
        for (ItemStack output : outputs) if (!output.isEmpty() && !allows(output)) return false;
        return true;
    }
    /** generatedOutputs is EMPTY for a transfer of finite existing stock, not for production. */
    public static boolean commitIfAllowed(List<ItemStack> generatedOutputs, BooleanSupplier transaction) {
        return POLICIES.commitIfAllowed(generatedOutputs,transaction);
    }
}
