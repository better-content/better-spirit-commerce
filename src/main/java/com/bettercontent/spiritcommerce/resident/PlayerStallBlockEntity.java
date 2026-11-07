package com.bettercontent.spiritcommerce.resident;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Stock, exact barter listings, and earnings belong to the placed stall. */
public final class PlayerStallBlockEntity extends BlockEntity {
    public static final int SLOTS = 18;
    public static final int OFFERS = 6;
    public record Offer(ItemStack sale, ItemStack payment, boolean enabled) {
        public boolean valid() { return !sale.isEmpty() && !payment.isEmpty(); }
    }

    private final NonNullList<ItemStack> stock = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> proceeds = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final List<Offer> offers = new ArrayList<>(OFFERS);
    private final List<String> events = new ArrayList<>();
    @Nullable private UUID owner;
    @Nullable private UUID customer;
    private int selectedOffer = -1;
    private long visitStarted;
    private long nextScan;
    private long cooldown;

    public PlayerStallBlockEntity(BlockPos pos, BlockState state) {
        super(PlayerStallRegistries.ENTITY.get(), pos, state);
        for (int i = 0; i < OFFERS; i++) offers.add(new Offer(ItemStack.EMPTY, ItemStack.EMPTY, false));
    }

    @Nullable public UUID owner() { return owner; }
    public void claim(ServerPlayer player) { if (owner == null) { owner = player.getUUID(); setChanged(); } }
    public boolean owns(ServerPlayer player) { return owner != null && owner.equals(player.getUUID()); }
    public List<ItemStack> stock() { return stock.stream().map(ItemStack::copy).toList(); }
    public List<ItemStack> proceeds() { return proceeds.stream().map(ItemStack::copy).toList(); }
    public List<Offer> offers() { return List.copyOf(offers); }
    public List<String> events() { return List.copyOf(events); }
    public boolean visiting() { return customer != null; }

    public boolean configure(int index, ItemStack sale, ItemStack payment, boolean enabled) {
        if (index < 0 || index >= OFFERS || sale.getCount() > sale.getMaxStackSize()
                || payment.getCount() > payment.getMaxStackSize()) return false;
        offers.set(index, new Offer(sale.copy(), payment.copy(), enabled && !sale.isEmpty() && !payment.isEmpty()));
        setChanged();
        return true;
    }

    public int stockCount(ItemStack wanted) { return count(stock, wanted); }
    public boolean canReceive(ItemStack item) { return add(copy(proceeds), item); }

    public boolean seedStock(ItemStack item) {
        NonNullList<ItemStack> staged = copy(stock);
        if (!add(staged, item)) return false;
        replace(stock, staged); setChanged(); return true;
    }

    boolean seedProceeds(ItemStack item) {
        NonNullList<ItemStack> staged = copy(proceeds);
        if (!add(staged, item)) return false;
        replace(proceeds, staged); setChanged(); return true;
    }

    public boolean deposit(ServerPlayer player, int inventorySlot) {
        if (inventorySlot < 0 || inventorySlot >= player.getInventory().items.size()) return false;
        ItemStack carried = player.getInventory().items.get(inventorySlot);
        if (carried.isEmpty()) return false;
        NonNullList<ItemStack> staged = copy(stock);
        if (!add(staged, carried)) return false;
        replace(stock, staged);
        player.getInventory().items.set(inventorySlot, ItemStack.EMPTY);
        player.getInventory().setChanged();
        setChanged();
        return true;
    }

    public boolean withdraw(ServerPlayer player, boolean fromProceeds, int index) {
        if (index < 0 || index >= SLOTS) return false;
        NonNullList<ItemStack> slots = fromProceeds ? proceeds : stock;
        ItemStack item = slots.get(index);
        if (item.isEmpty() || !room(player, item) || !player.getInventory().add(item.copy())) return false;
        slots.set(index, ItemStack.EMPTY);
        player.getInventory().setChanged();
        setChanged();
        return true;
    }

    private static boolean room(ServerPlayer player, ItemStack item) {
        int capacity = 0;
        for (ItemStack slot : player.getInventory().items) {
            if (slot.isEmpty()) capacity += item.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(slot, item)) capacity += slot.getMaxStackSize() - slot.getCount();
            if (capacity >= item.getCount()) return true;
        }
        return false;
    }

    public static int count(List<ItemStack> slots, ItemStack wanted) {
        int total = 0;
        for (ItemStack stack : slots) if (ItemStack.isSameItemSameTags(stack, wanted)) total += stack.getCount();
        return total;
    }

    public static NonNullList<ItemStack> copy(List<ItemStack> slots) {
        NonNullList<ItemStack> result = NonNullList.withSize(slots.size(), ItemStack.EMPTY);
        for (int i = 0; i < slots.size(); i++) result.set(i, slots.get(i).copy());
        return result;
    }

    public static boolean remove(List<ItemStack> slots, ItemStack wanted) {
        if (wanted.isEmpty() || count(slots, wanted) < wanted.getCount()) return false;
        int left = wanted.getCount();
        for (int i = 0; i < slots.size() && left > 0; i++) {
            ItemStack stack = slots.get(i);
            if (!ItemStack.isSameItemSameTags(stack, wanted)) continue;
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken); left -= taken;
            if (stack.isEmpty()) slots.set(i, ItemStack.EMPTY);
        }
        return true;
    }

    public static boolean add(List<ItemStack> slots, ItemStack incoming) {
        if (incoming.isEmpty()) return false;
        int left = incoming.getCount();
        for (ItemStack stack : slots) if (ItemStack.isSameItemSameTags(stack, incoming)) {
            int room = Math.min(left, stack.getMaxStackSize() - stack.getCount());
            stack.grow(room); left -= room;
        }
        for (int i = 0; i < slots.size() && left > 0; i++) if (slots.get(i).isEmpty()) {
            int amount = Math.min(left, incoming.getMaxStackSize());
            slots.set(i, incoming.copyWithCount(amount)); left -= amount;
        }
        return left == 0;
    }

    private static void replace(NonNullList<ItemStack> slots, List<ItemStack> next) {
        for (int i = 0; i < slots.size(); i++) slots.set(i, next.get(i).copy());
    }

    public void commit(List<ItemStack> newStock, List<ItemStack> newProceeds, LivingEntity buyer,
                       Offer offer, boolean crafted) {
        replace(stock, newStock);
        replace(proceeds, newProceeds);
        String line = "Sold " + offer.sale().getCount() + " " + offer.sale().getHoverName().getString()
                + " to " + buyer.getDisplayName().getString() + " for " + offer.payment().getCount()
                + " " + offer.payment().getHoverName().getString() + (crafted ? " (crafted)" : "");
        events.add(line.length() > 240 ? line.substring(0, 240) : line);
        if (events.size() > 16) events.remove(0);
        setChanged();
    }

    public void dropContents(ServerLevel level) {
        for (ItemStack item : stock) if (!item.isEmpty()) Block.popResource(level, worldPosition, item.copy());
        for (ItemStack item : proceeds) if (!item.isEmpty()) Block.popResource(level, worldPosition, item.copy());
        stock.replaceAll(ignored -> ItemStack.EMPTY);
        proceeds.replaceAll(ignored -> ItemStack.EMPTY);
    }

    public static void tick(ServerLevel level, BlockPos pos, BlockState state, PlayerStallBlockEntity stall) {
        long now = level.getGameTime();
        if (stall.customer != null) {
            Entity entity = level.getEntity(stall.customer);
            if (!(entity instanceof PathfinderMob buyer) || !buyer.isAlive()
                    || !ResidentRules.isResident(buyer) || now - stall.visitStarted > 200) {
                stall.release(now); return;
            }
            // Native villager bed/POI navigation owns the night; a stall must not pull it away.
            if (buyer instanceof Villager && !level.isDay()) { stall.release(now); return; }
            BlockPos front = pos.relative(state.getValue(PlayerStallBlock.FACING));
            if (buyer.blockPosition().distSqr(front) <= 4) {
                if (stall.selectedOffer >= 0 && stall.selectedOffer < OFFERS)
                    PlayerStallTrade.execute(level, stall, buyer, stall.selectedOffer);
                stall.release(now);
                return;
            }
            if (now % 20 == 0 && buyer.getNavigation().isDone()) {
                if (!buyer.getNavigation().moveTo(front.getX() + .5, front.getY(), front.getZ() + .5, .7)) {
                    stall.release(now); return;
                }
                ResidentState.of(buyer).setDoing("Going to the player stall");
            }
            return;
        }
        if (now < stall.nextScan || now < stall.cooldown) return;
        stall.nextScan = now + 100;
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos).inflate(24, 8, 24), entity -> entity.isAlive()
                        && entity instanceof PathfinderMob && ResidentRules.isResident(entity)
                        && (!(entity instanceof Villager) || level.isDay()));
        nearby.sort(java.util.Comparator.comparingDouble(e -> e.blockPosition().distSqr(pos)));
        if (nearby.size() > 16) nearby = nearby.subList(0, 16);
        for (LivingEntity buyer : nearby) for (int i = 0; i < OFFERS; i++) {
            if (!PlayerStallTrade.quote(level, stall, buyer, i).available()) continue;
            if (!PlayerStallReservations.reserve(level, buyer.getUUID(), pos, now)) continue;
            stall.customer = buyer.getUUID();
            stall.selectedOffer = i;
            stall.visitStarted = now;
            return;
        }
    }

    private void release(long now) {
        if (customer != null && level instanceof ServerLevel server)
            PlayerStallReservations.release(server, customer, worldPosition);
        customer = null; selectedOffer = -1; cooldown = now + 200;
    }

    @Override public void setRemoved() {
        release(level instanceof ServerLevel server ? server.getGameTime() : 0);
        super.setRemoved();
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.put("Stock", saveStacks(stock)); tag.put("Proceeds", saveStacks(proceeds));
        ListTag rows = new ListTag();
        for (Offer offer : offers) {
            CompoundTag row = new CompoundTag();
            row.put("Sale", offer.sale().save(new CompoundTag()));
            row.put("Payment", offer.payment().save(new CompoundTag()));
            row.putBoolean("Enabled", offer.enabled()); rows.add(row);
        }
        tag.put("Offers", rows);
        ListTag history = new ListTag();
        for (String line : events) history.add(StringTag.valueOf(line));
        tag.put("Events", history);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        loadStacks(stock, tag.getList("Stock", Tag.TAG_COMPOUND));
        loadStacks(proceeds, tag.getList("Proceeds", Tag.TAG_COMPOUND));
        ListTag rows = tag.getList("Offers", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(OFFERS, rows.size()); i++) {
            CompoundTag row = rows.getCompound(i);
            offers.set(i, new Offer(ItemStack.of(row.getCompound("Sale")),
                    ItemStack.of(row.getCompound("Payment")), row.getBoolean("Enabled")));
        }
        events.clear();
        for (Tag raw : tag.getList("Events", Tag.TAG_STRING)) {
            String line = raw.getAsString();
            events.add(line.length() > 240 ? line.substring(0, 240) : line);
        }
        if (events.size() > 16) events.subList(0, events.size() - 16).clear();
    }

    private static ListTag saveStacks(List<ItemStack> slots) {
        ListTag result = new ListTag();
        for (int i = 0; i < slots.size(); i++) if (!slots.get(i).isEmpty()) {
            CompoundTag row = slots.get(i).save(new CompoundTag());
            row.putByte("Slot", (byte) i); result.add(row);
        }
        return result;
    }

    private static void loadStacks(List<ItemStack> slots, ListTag rows) {
        for (int i = 0; i < slots.size(); i++) slots.set(i, ItemStack.EMPTY);
        for (Tag raw : rows) {
            CompoundTag row = (CompoundTag) raw;
            int index = row.getByte("Slot") & 255;
            if (index < slots.size()) slots.set(index, ItemStack.of(row));
        }
    }
}
