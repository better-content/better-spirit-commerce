package com.bettercontent.spiritcommerce.spirit;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.mojang.authlib.GameProfile;
import com.sammy.malum.common.container.SpiritPouchContainer;
import com.sammy.malum.common.block.curiosities.spirit_altar.SpiritAltarBlockEntity;
import com.sammy.malum.core.systems.recipe.SpiritWithCount;
import com.sammy.malum.registry.common.SpiritTypeRegistry;
import com.sammy.malum.registry.common.block.BlockRegistry;
import com.sammy.malum.common.entity.spirit.SpiritItemEntity;
import com.sammy.malum.registry.common.ContainerRegistry;
import com.sammy.malum.registry.common.item.ItemRegistry;
import java.util.UUID;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@PrefixGameTestTemplate(false)
public final class SpiritPouchGameTests {
    private SpiritPouchGameTests() {}

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void pouchMenuAcceptsSpiritsAndRejectsOtherItems(final GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("spirit-pouch-gametest".getBytes()), "spirit-pouch-test"));
        SpiritPouchContainer menu = new SpiritPouchContainer(ContainerRegistry.SPIRIT_POUCH.get(), 1,
                player.getInventory(), new SimpleContainer(9));
        var pouchSlot = menu.slots.get(0);
        var currencies = java.util.Arrays.stream(CurrencyIdentity.values())
                .map(identity -> CurrencyItems.item(identity).get())
                .collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(currencies.size() == 5, "No duplicate physical core shard registrations");
        for (var currency : currencies) helper.assertTrue(pouchSlot.mayPlace(new ItemStack(currency)),
                "Native Malum pouch must accept every real core reagent");
        helper.assertTrue(pouchSlot.mayPlace(new ItemStack(ItemRegistry.ELDRITCH_SPIRIT.get()))
                && pouchSlot.mayPlace(new ItemStack(ItemRegistry.UMBRAL_SPIRIT.get())),
                "Pouch must keep both specialist Malum reagents");
        helper.assertTrue(menu.slots.size() >= 27,
                "Pouch remains 27 general storage slots, not nine hardwired identity buckets");
        helper.assertTrue(!pouchSlot.mayPlace(new ItemStack(Items.DIRT)),
                "Pouch must reject ordinary items");
        menu.removed(player);
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void nativeSpiritAltarUsesSevenRealReagentsInNineGenericSavedSlots(final GameTestHelper helper) {
        BlockPos position = new BlockPos(2, 2, 2);
        helper.setBlock(position, BlockRegistry.SPIRIT_ALTAR.get());
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(blockEntity instanceof SpiritAltarBlockEntity, "Native spirit altar must be placed");
        var inventory = ((SpiritAltarBlockEntity) blockEntity).spiritInventory;
        helper.assertTrue(inventory.getSlots() == SpiritTypeRegistry.SPIRITS.size()
                && inventory.getSlots() == 9,
                "Keep original generic saved slot capacity; it is not nine active reagent identities");
        int slot = 0;
        for (CurrencyIdentity identity : CurrencyIdentity.values()) {
            ItemStack reagent = new ItemStack(CurrencyItems.item(identity).get(), 10);
            helper.assertTrue(inventory.isItemValid(slot, reagent), "Native altar must accept " + identity);
            inventory.setStackInSlot(slot++, reagent);
        }
        for (ItemStack specialist : List.of(new ItemStack(ItemRegistry.ELDRITCH_SPIRIT.get()),
                new ItemStack(ItemRegistry.UMBRAL_SPIRIT.get()))) {
            helper.assertTrue(inventory.isItemValid(slot, specialist), "Native altar must keep specialist reagent");
            inventory.setStackInSlot(slot++, specialist);
        }
        helper.assertTrue(slot == 7 && !inventory.isItemValid(slot,
                new ItemStack(CurrencyItems.item(CurrencyIdentity.POWER).get())),
                "A second physical Power cannot occupy a retired Arcane/Infernal slot identity");
        helper.assertTrue(new SpiritWithCount(SpiritTypeRegistry.ARCANE_SPIRIT, 10)
                .matches(inventory.getStackInSlot(0))
                && new SpiritWithCount(SpiritTypeRegistry.INFERNAL_SPIRIT, 10)
                .matches(inventory.getStackInSlot(0)),
                "One summed Power stack satisfies each convergent native type matcher");
        helper.succeed();
    }

    @GameTest(templateNamespace = BetterSpiritCommerce.MOD_ID, template = "empty")
    public static void creditedSpiritStackReleasesAsOneOwnedPickup(final GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes("grouped-spirit-gametest".getBytes()), "grouped-spirit"));
        player.moveTo(helper.absolutePos(net.minecraft.core.BlockPos.ZERO).getCenter());
        ItemStack credit = new ItemStack(CurrencyItems.item(CurrencyIdentity.WORK).get(), 4);
        SpiritAcquisition.releaseGroupedSpirits(List.of(credit), player, player);
        var spirits = helper.getLevel().getEntitiesOfClass(SpiritItemEntity.class,
                new AABB(player.blockPosition()).inflate(4));
        helper.assertTrue(spirits.size() == 1 && spirits.get(0).getItem().getCount() == 4,
                "One credited kill must release its full stack as one Malum spirit pickup; entities="
                        + spirits.size() + ", first stack=" + (spirits.isEmpty() ? "none" : spirits.get(0).getItem()));
        helper.succeed();
    }
}
