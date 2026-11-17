package com.bettercontent.spiritcommerce.trader;

import com.bettercontent.spiritcommerce.registry.SpiritProfessions;
import com.bettercontent.spiritcommerce.spirit.SpiritKind;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Small, visible utilities performed while a spirit villager is working at its own station. */
public final class ProfessionBehaviors {
    private static final int INTERVAL = 100;
    private static final double RADIUS = 6.0D;
    private static final java.util.Set<Block> REPLACED_JOB_SITES = java.util.Set.of(
            Blocks.BARREL, Blocks.BLAST_FURNACE, Blocks.BREWING_STAND, Blocks.CARTOGRAPHY_TABLE,
            Blocks.COMPOSTER, Blocks.FLETCHING_TABLE, Blocks.GRINDSTONE, Blocks.LECTERN,
            Blocks.LOOM, Blocks.SMITHING_TABLE, Blocks.SMOKER, Blocks.STONECUTTER, Blocks.CAULDRON);

    private ProfessionBehaviors() {}

    @SubscribeEvent
    public static void onLivingTick(final LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || villager.tickCount % INTERVAL != 0) return;
        SpiritKind kind = SpiritProfessions.kindOf(villager.getVillagerData().getProfession());
        if (kind == null || !isAtJobSite(villager, level)) return;
        replaceLegacyJobSite(villager, level, kind);

        switch (kind) {
            case RENEWAL -> nearby(villager).stream()
                    .filter(entity -> !(entity instanceof Enemy) && entity.getHealth() < entity.getMaxHealth())
                    .limit(3).forEach(entity -> entity.heal(1.0F));
            case CONTROL -> nearby(villager).stream().filter(entity -> entity instanceof Enemy).limit(3)
                    .forEach(entity -> entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0)));
            case MOTION -> nearby(villager).stream().filter(entity -> !(entity instanceof Enemy)).limit(4)
                    .forEach(entity -> entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 0)));
            case ENDURANCE -> nearby(villager).stream().filter(LivingEntity::isOnFire).limit(4)
                    .forEach(LivingEntity::clearFire);
            case POWER -> accelerateFurnace(level, villager);
        }
        level.sendParticles(ParticleTypes.ENCHANT, villager.getX(), villager.getY() + 1.4D, villager.getZ(),
                4, 0.25D, 0.2D, 0.25D, 0.0D);
    }

    private static boolean isAtJobSite(final Villager villager, final ServerLevel level) {
        GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        return site != null && site.dimension().equals(level.dimension())
                && villager.blockPosition().closerThan(site.pos(), 3.0D);
    }

    private static java.util.List<LivingEntity> nearby(final Villager villager) {
        return villager.level().getEntitiesOfClass(LivingEntity.class,
                villager.getBoundingBox().inflate(RADIUS), entity -> entity != villager && entity.isAlive());
    }

    private static void replaceLegacyJobSite(final Villager villager, final ServerLevel level, final SpiritKind kind) {
        GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || !REPLACED_JOB_SITES.contains(level.getBlockState(site.pos()).getBlock())) return;
        level.setBlockAndUpdate(site.pos(), SpiritProfessions.definition(kind).block().get().defaultBlockState());
    }

    private static void accelerateFurnace(final ServerLevel level, final Villager villager) {
        GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null) return;
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(site.pos().offset(-2, -1, -2), site.pos().offset(2, 1, 2))) {
            if (!(level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace)) continue;
            CompoundTag tag = furnace.saveWithFullMetadata();
            int total = tag.getShort("CookTimeTotal");
            int cooked = tag.getShort("CookTime");
            if (tag.getShort("BurnTime") <= 0 || total <= 0 || cooked >= total) continue;
            tag.putShort("CookTime", (short) Math.min(total - 1, cooked + 20));
            furnace.load(tag);
            furnace.setChanged();
            return;
        }
    }
}
