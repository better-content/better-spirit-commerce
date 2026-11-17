package com.bettercontent.spiritcommerce.spirit;

import com.bettercontent.spiritcommerce.BetterSpiritCommerce;
import com.bettercontent.spiritcommerce.ops.ObservationalEconomyData;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.sammy.malum.common.capability.MalumLivingEntityDataCapability;
import com.sammy.malum.common.entity.spirit.SpiritItemEntity;
import com.sammy.malum.core.handlers.SpiritHarvestHandler;
import com.sammy.malum.registry.common.SoundRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Releases Malum's actual mob-specific spirit results at the dying mob for credited players. */
public final class SpiritAcquisition {
    public static final String GROUPED_PICKUP_MARKER = "BetterContentGroupedSpiritPickup";
    private static final TagKey<net.minecraft.world.entity.EntityType<?>> SPIRITLESS_ACTORS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(BetterSpiritCommerce.MOD_ID, "spiritless_economy_actors"));

    private SpiritAcquisition() {}

    // Claim the death before Malum's own exposed-soul listener can emit a second drop.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(final LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();
        if (event.isCanceled() || level.isClientSide || victim instanceof ServerPlayer || isEconomyActor(victim)) return;
        ServerPlayer recipient = playerSource(event.getSource().getEntity());
        if (recipient == null) return;

        var capability = MalumLivingEntityDataCapability.getCapability(victim);
        if (capability.soulData.spawnerSpawned || capability.soulData.soulless) return;

        List<ItemStack> nativeDrops = SpiritHarvestHandler.getSpawnedSpirits(victim, recipient, ItemStack.EMPTY);
        List<ItemStack> drops = mapDrops(nativeDrops,
                item -> {
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
                    CurrencyIdentity alreadyCore = CurrencyIdentity.fromItemId(id);
                    return alreadyCore != null ? alreadyCore : CurrencyIdentity.fromLegacyNativeSpirit(id);
                },
                identity -> CurrencyItems.item(identity).get());
        if (!drops.isEmpty()) {
            releaseGroupedSpirits(drops, victim, recipient);
            Map<CurrencyIdentity, Integer> acquired = new EnumMap<>(CurrencyIdentity.class);
            for (ItemStack stack : drops) {
                CurrencyIdentity identity = CurrencyIdentity.fromItemId(ForgeRegistries.ITEMS.getKey(stack.getItem()));
                if (identity != null) acquired.merge(identity, stack.getCount(), Integer::sum);
            }
            if (!acquired.isEmpty()) {
                ObservationalEconomyData observations = ObservationalEconomyData.get(recipient.server.overworld());
                var biome = level.getBiome(victim.blockPosition()).unwrapKey();
                String region = level.dimension().location() + ":"
                        + biome.map(key -> key.location().toString()).orElse("unknown");
                observations.recordRegion(region, acquired);
                observations.recordRelease(acquired);
                observations.recordActivity();
            }
        }
        capability.soulData.soulless = true;
    }

    /** Sum converging ordinary types without merging NBT-distinct ritual shards or exotics. */
    static List<ItemStack> mapDrops(final List<ItemStack> nativeDrops,
                                    final Function<Item, CurrencyIdentity> ordinaryIdentity,
                                    final Function<CurrencyIdentity, Item> commerceItem) {
        List<Object> ordered = new ArrayList<>();
        Map<CurrencyIdentity, Long> totals = new EnumMap<>(CurrencyIdentity.class);
        for (ItemStack nativeStack : nativeDrops) {
            if (nativeStack.isEmpty()) continue;
            CurrencyIdentity identity = ordinaryIdentity.apply(nativeStack.getItem());
            if (identity != null && !nativeStack.hasTag()) {
                if (!totals.containsKey(identity)) ordered.add(identity);
                totals.merge(identity, (long) nativeStack.getCount(), Math::addExact);
            } else {
                ItemStack converted = nativeStack.copy();
                if (identity != null) {
                    converted = new ItemStack(commerceItem.apply(identity), nativeStack.getCount());
                    converted.setTag(nativeStack.getTag().copy()); // keep stored_ritual identity
                }
                ordered.add(converted);
            }
        }
        List<ItemStack> drops = new ArrayList<>();
        for (Object source : ordered) {
            if (source instanceof CurrencyIdentity identity) {
                split(drops, new ItemStack(commerceItem.apply(identity)), totals.get(identity));
            } else if (source instanceof ItemStack stack) {
                split(drops, stack, stack.getCount());
            }
        }
        return drops;
    }

    private static void split(List<ItemStack> drops, ItemStack template, long count) {
        for (long left = count; left > 0;) {
            int size = (int) Math.min(left, template.getMaxStackSize());
            ItemStack part = template.copy();
            part.setCount(size);
            drops.add(part);
            left -= size;
        }
    }

    static void releaseGroupedSpirits(final List<ItemStack> drops, final LivingEntity victim,
                                      final ServerPlayer recipient) {
        Level level = victim.level();
        double x = victim.getX();
        double y = victim.getY() + victim.getBbHeight() / 2.0;
        double z = victim.getZ();
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            double vx = (level.random.nextDouble() - 0.5) * 0.3;
            double vz = (level.random.nextDouble() - 0.5) * 0.3;
            ItemStack grouped = stack.copy();
            // Malum reduces untagged spirits to a single default item even when given a stack.
            grouped.getOrCreateTag().putBoolean(GROUPED_PICKUP_MARKER, true);
            level.addFreshEntity(new SpiritItemEntity(level, recipient.getUUID(), grouped,
                    x, y, z, vx, 0.055, vz));
        }
        level.playSound(null, x, y, z, SoundRegistry.SOUL_SHATTER.get(), SoundSource.PLAYERS,
                1.0F, 0.7F + level.random.nextFloat() * 0.4F);
    }

    public static void removeGroupedPickupMarker(ItemStack stack) {
        if (stack.getTag() == null || !stack.getTag().getBoolean(GROUPED_PICKUP_MARKER)) return;
        stack.getTag().remove(GROUPED_PICKUP_MARKER);
        if (stack.getTag().isEmpty()) stack.setTag(null);
    }

    private static boolean isEconomyActor(final LivingEntity entity) {
        return entity instanceof AbstractVillager || entity instanceof IronGolem || entity.getType().is(SPIRITLESS_ACTORS);
    }

    // Only explicit player-owned effects count; arbitrary owned mobs are not followed.
    private static ServerPlayer playerSource(final Entity entity) {
        if (entity instanceof ServerPlayer player) return player;
        if (entity instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player) return player;
        if (entity instanceof AreaEffectCloud cloud && cloud.getOwner() instanceof ServerPlayer player) return player;
        if (entity instanceof EvokerFangs fangs && fangs.getOwner() instanceof ServerPlayer player) return player;
        return null;
    }
}
