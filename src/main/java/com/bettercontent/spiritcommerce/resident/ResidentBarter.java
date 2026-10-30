package com.bettercontent.spiritcommerce.resident;

import com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.state.BlockState;

/** Bounded recipe graph evaluated at quote time; stock changes only on commit. */
public final class ResidentBarter {
    public record Offer(ItemStack result, ResourceLocation recipe, String reason) {}
    public record Quote(ItemStack result, ItemStack payment, ResourceLocation recipe,
                        List<ItemStack> ingredients, String blocker) {
        public boolean available() { return blocker == null; }
    }
    public record PaymentPlan(List<ItemStack> after, ResourceLocation recipe, int cost) {}
    private record Plan(List<ItemStack> after, ResourceLocation recipe, List<ItemStack> inputs,
                        List<ItemStack> generated, int cost) {}
    private record Context(ServerLevel level, Set<String> stations, Map<Item, List<Recipe<?>>> byOutput,
                           List<Recipe<?>> recipes) {}

    private ResidentBarter() {}

    public static List<Offer> offers(ServerLevel level, LivingEntity resident) {
        ResidentState state = ResidentState.of(resident);
        List<Offer> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ItemStack stock : state.items()) if (!stock.isEmpty() && surplus(state, stock) > 0) {
            if (seen.add(key(stock))) result.add(new Offer(stock.copyWithCount(
                    Math.min(stock.getMaxStackSize(), surplus(state, stock))), null, "In stock"));
        }
        Context context = context(level, ResidentRules.worksite(resident));
        int examined = 0;
        for (Recipe<?> recipe : context.recipes()) {
            if (result.size() >= 48 || examined >= 128) break;
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty() || output.getCount() > output.getMaxStackSize()
                    || seen.contains(key(output))) continue;
            boolean touchesStock = false;
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack stock : state.items()) {
                    if (!stock.isEmpty() && ingredient.test(stock)) { touchesStock = true; break; }
                }
                if (touchesStock) break;
            }
            if (!touchesStock) continue;
            examined++;
            if (prepare(context, state, output) == null) continue;
            seen.add(key(output));
            result.add(new Offer(output.copy(), recipe.getId(), "Can make at a nearby station"));
        }
        result.sort(Comparator.comparing(o -> o.result().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    public static Quote quote(ServerLevel level, LivingEntity resident, ItemStack wanted, ItemStack payment) {
        if (wanted.isEmpty() || wanted.getCount() < 1 || wanted.getCount() > wanted.getMaxStackSize())
            return blocked(wanted, payment, "Invalid amount");
        if (payment.isEmpty() || payment.getCount() < 1 || payment.getCount() > payment.getMaxStackSize())
            return blocked(wanted, payment, "Select payment");
        ResidentState state = ResidentState.of(resident);
        Context context = context(level, ResidentRules.worksite(resident));
        if (!usefulPayment(context, state, payment))
            return blocked(wanted, payment, "Does not need this item");
        Plan plan = prepare(context, state, wanted);
        if (plan == null) return blocked(wanted, payment, "Needs ingredients or a workstation");
        int value = ResidentRules.basicValue(payment) * payment.getCount();
        if (value < plan.cost()) return blocked(wanted, payment, "Offer more goods");
        if (!add(plan.after(), payment)) return blocked(wanted, payment, "Inventory full");
        return new Quote(wanted.copy(), payment.copy(), plan.recipe(), List.copyOf(plan.inputs()), null);
    }

    private static Quote blocked(ItemStack wanted, ItemStack payment, String reason) {
        return new Quote(wanted.copy(), payment.copy(), null, List.of(), reason);
    }

    /** The caller preflights its own inventory. This prepares all resident changes before taking payment. */
    public static boolean execute(ServerLevel level, LivingEntity resident, ItemStack wanted, ItemStack payment,
                                  BooleanSupplier takePayment, Consumer<ItemStack> giveResult) {
        Quote quote = quote(level, resident, wanted, payment);
        if (!quote.available()) return false;
        ResidentState state = ResidentState.of(resident);
        Plan plan = prepare(context(level, ResidentRules.worksite(resident)), state, wanted);
        if (plan == null || !add(plan.after(), payment)) return false;
        return RenewableAcquisitionPolicy.commitIfAllowed(plan.generated(), () -> {
            if (!takePayment.getAsBoolean()) return false;
            state.replaceItems(plan.after());
            giveResult.accept(quote.result().copy());
            state.setDoing(quote.recipe() == null ? "Bartering stock" : "Crafting to barter");
            state.event("bartered " + quote.result().getCount() + " " + quote.result().getHoverName().getString()
                    + (quote.recipe() == null ? "" : " via " + quote.recipe()));
            return true;
        });
    }

    private static Plan prepare(Context context, ResidentState state, ItemStack wanted) {
        if (wanted.isEmpty()) return null;
        List<ItemStack> stock = copy(state.items());
        if (surplus(state, wanted) >= wanted.getCount() && remove(stock, wanted))
            return new Plan(stock, null, List.of(), List.of(), ResidentRules.basicValue(wanted) * wanted.getCount());
        for (Recipe<?> recipe : context.byOutput().getOrDefault(wanted.getItem(), List.of())) {
            ItemStack output = recipe.getResultItem(context.level().registryAccess());
            if (!ItemStack.isSameItemSameTags(output, wanted) || output.getCount() != wanted.getCount()) continue;
            Work work = new Work(copy(state.items()));
            if (craft(context, recipe, work, 0, new HashSet<>(), new Budget()) && remove(work.slots, wanted))
                return RenewableAcquisitionPolicy.allowsAll(work.generated)
                        ? new Plan(work.slots, recipe.getId(), work.inputs, List.copyOf(work.generated), Math.max(1, work.cost)) : null;
        }
        return null;
    }

    /** A resident-to-resident exchange can stage production without changing world state. */
    static List<ItemStack> preview(ServerLevel level, LivingEntity resident, ItemStack wanted) {
        Plan plan = prepare(context(level, ResidentRules.worksite(resident)), ResidentState.of(resident), wanted);
        return plan == null ? null : plan.after();
    }

    public static PaymentPlan paymentPlan(ServerLevel level, LivingEntity resident, ItemStack wanted) {
        Plan plan = prepare(context(level, ResidentRules.worksite(resident)), ResidentState.of(resident), wanted);
        return plan == null ? null : new PaymentPlan(plan.after(), plan.recipe(), plan.cost());
    }

    public static boolean wants(ServerLevel level, LivingEntity resident, ItemStack item) {
        ResidentState state = ResidentState.of(resident);
        if (ResidentRules.needs(item, state)) return true;
        return usefulPayment(context(level, ResidentRules.worksite(resident)), state, item);
    }

    static boolean addTo(List<ItemStack> inventory, ItemStack item) { return add(inventory, item); }

    private static final class Work {
        List<ItemStack> slots;
        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> generated = new ArrayList<>();
        int cost;
        Work(List<ItemStack> slots) { this.slots = slots; }
        Work copy() {
            Work out = new Work(ResidentBarter.copy(slots));
            out.inputs.addAll(inputs.stream().map(ItemStack::copy).toList());
            out.generated.addAll(generated.stream().map(ItemStack::copy).toList());
            out.cost = cost;
            return out;
        }
        void use(Work other) { slots = other.slots; inputs = other.inputs; generated = other.generated; cost = other.cost; }
    }

    private static final class Budget { int calls; }

    private static boolean craft(Context context, Recipe<?> recipe,
                                 Work target, int depth, Set<String> visiting, Budget budget) {
        if (depth > 2 || ++budget.calls > 96) return false;
        ItemStack result = recipe.getResultItem(context.level().registryAccess());
        if (result.isEmpty() || result.getCount() > result.getMaxStackSize()
                || !RenewableAcquisitionPolicy.allows(result) || !visiting.add(key(result))) return false;
        Work work = target.copy();
        List<ItemStack> consumed = new ArrayList<>();
        int index = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient.isEmpty()) continue;
            int quantity = index++ == 0 && recipeType(recipe).equals("hexerei:woodcutting")
                    ? ResidentOptionalRecipes.woodcuttingCount(recipe) : 1;
            ItemStack selected = find(work.slots, ingredient, quantity);
            if (selected.isEmpty() && depth < 2) {
                int alternatives = 0;
                for (ItemStack option : ingredient.getItems()) {
                    if (++alternatives > 32) break;
                    for (Recipe<?> sub : context.byOutput().getOrDefault(option.getItem(), List.of())) {
                        ItemStack subResult = sub.getResultItem(context.level().registryAccess());
                        if (subResult.isEmpty() || !ingredient.test(subResult)) continue;
                        Work branch = work.copy();
                        if (craft(context, sub, branch, depth + 1, visiting, budget)) {
                            selected = find(branch.slots, ingredient, quantity);
                            if (!selected.isEmpty()) { work.use(branch); break; }
                        }
                    }
                    if (!selected.isEmpty()) break;
                }
            }
            if (selected.isEmpty() || !remove(work.slots, selected)) { visiting.remove(key(result)); return false; }
            consumed.add(selected);
        }
        String type = recipeType(recipe);
        if (type.equals("minecraft:smelting") || type.equals("minecraft:smoking") || type.equals("minecraft:blasting")) {
            ItemStack fuel = work.slots.stream().filter(s -> s.is(net.minecraft.world.item.Items.COAL)
                    || s.is(net.minecraft.world.item.Items.CHARCOAL)
                    || s.is(net.minecraft.tags.ItemTags.LOGS)).findFirst().orElse(ItemStack.EMPTY);
            if (fuel.isEmpty()) { visiting.remove(key(result)); return false; }
            ItemStack one = fuel.copyWithCount(1);
            remove(work.slots, one); consumed.add(one);
        }
        if (type.equals("farmersdelight:cutting")
                && !ResidentOptionalRecipes.canCut(recipe, work.slots)) {
            visiting.remove(key(result)); return false;
        }
        if (type.equals("farmersdelight:cutting")) {
            ResidentOptionalRecipes.wearCuttingTool(recipe, work.slots);
            work.cost++;
        }
        if (type.equals("farmersdelight:cooking")) {
            ItemStack container = ResidentOptionalRecipes.cookingContainer(recipe);
            if (!container.isEmpty()) {
                if (!remove(work.slots, container)) { visiting.remove(key(result)); return false; }
                consumed.add(container.copy());
            }
        }
        for (ItemStack input : consumed) {
            work.inputs.add(input.copy());
            work.cost += ResidentRules.basicValue(input) * input.getCount();
            if (input.hasCraftingRemainingItem()) {
                ItemStack remainder = input.getCraftingRemainingItem();
                remainder.setCount(remainder.getCount() * input.getCount());
                if (!RenewableAcquisitionPolicy.allows(remainder) || !add(work.slots, remainder)) { visiting.remove(key(result)); return false; }
                work.generated.add(remainder.copy());
            }
        }
        work.cost++;
        if (!add(work.slots, result)) { visiting.remove(key(result)); return false; }
        work.generated.add(result.copy());
        target.use(work);
        visiting.remove(key(result));
        return true;
    }

    private static ItemStack find(List<ItemStack> slots, Ingredient ingredient, int quantity) {
        for (ItemStack stack : slots) if (ingredient.test(stack) && count(slots, stack) >= quantity)
            return stack.copyWithCount(quantity);
        return ItemStack.EMPTY;
    }

    private static List<ItemStack> copy(List<ItemStack> source) { return source.stream().map(ItemStack::copy).collect(java.util.stream.Collectors.toCollection(ArrayList::new)); }
    private static int count(List<ItemStack> slots, ItemStack wanted) {
        int count = 0;
        for (ItemStack stack : slots) if (ItemStack.isSameItemSameTags(stack, wanted)) count += stack.getCount();
        return count;
    }
    private static boolean remove(List<ItemStack> slots, ItemStack wanted) {
        if (wanted.isEmpty() || count(slots, wanted) < wanted.getCount()) return false;
        int left = wanted.getCount();
        for (int i = 0; i < slots.size() && left > 0; i++) {
            ItemStack stack = slots.get(i);
            if (!ItemStack.isSameItemSameTags(stack, wanted)) continue;
            int take = Math.min(left, stack.getCount()); stack.shrink(take); left -= take;
            if (stack.isEmpty()) slots.set(i, ItemStack.EMPTY);
        }
        return true;
    }
    private static boolean add(List<ItemStack> slots, ItemStack incoming) {
        if (incoming.isEmpty()) return false;
        List<ItemStack> staged = copy(slots);
        int left = incoming.getCount();
        for (ItemStack stack : staged) if (ItemStack.isSameItemSameTags(stack, incoming)) {
            int room = Math.min(left, stack.getMaxStackSize() - stack.getCount());
            stack.grow(room); left -= room;
        }
        for (int i = 0; i < staged.size() && left > 0; i++) if (staged.get(i).isEmpty()) {
            int amount = Math.min(left, incoming.getMaxStackSize());
            staged.set(i, incoming.copyWithCount(amount)); left -= amount;
        }
        if (left > 0) return false;
        slots.clear(); slots.addAll(staged); return true;
    }

    private static String key(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + String.valueOf(stack.getTag());
    }
    private static boolean usefulPayment(Context context, ResidentState state, ItemStack payment) {
        if (ResidentRules.needs(payment, state)) return true;
        if (state.count(payment) >= 8) return false;
        for (Recipe<?> recipe : context.recipes())
            for (Ingredient ingredient : recipe.getIngredients()) if (ingredient.test(payment)) return true;
        return false;
    }
    private static Context context(ServerLevel level, BlockPos center) {
        Set<String> stations = stations(level, center);
        Map<Item, List<Recipe<?>>> index = new java.util.HashMap<>();
        List<Recipe<?>> recipes = new ArrayList<>();
        for (Recipe<?> recipe : level.getRecipeManager().getRecipes()) {
            if (!supported(recipe) || !hasStation(stations, recipe)) continue;
            ItemStack output = recipe.getResultItem(level.registryAccess());
            if (output.isEmpty() || recipe.getIngredients().isEmpty()
                    || !RenewableAcquisitionPolicy.allows(output)) continue;
            recipes.add(recipe);
            index.computeIfAbsent(output.getItem(), ignored -> new ArrayList<>()).add(recipe);
        }
        return new Context(level, stations, index, recipes);
    }
    private static int surplus(ResidentState state, ItemStack stack) {
        int reserve = ResidentRules.isSafeWater(stack) && state.water() < 14 ? 2
                : stack.getFoodProperties(null) != null && state.food() < 14 ? 2
                : stack.is(net.minecraft.tags.ItemTags.LOGS) || stack.is(net.minecraft.world.item.Items.COAL)
                || stack.is(net.minecraft.world.item.Items.CHARCOAL)
                || stack.is(net.minecraft.world.item.Items.GLASS_BOTTLE) ? 2 : 0;
        return Math.max(0, state.count(stack) - reserve);
    }
    private static boolean supported(Recipe<?> recipe) {
        String id = recipeType(recipe);
        return id.equals("minecraft:crafting") || id.equals("minecraft:stonecutting")
                || id.equals("minecraft:smelting") || id.equals("minecraft:smoking")
                || id.equals("minecraft:blasting") || id.equals("farmersdelight:cutting")
                || id.equals("farmersdelight:cooking") || id.equals("hexerei:woodcutting")
                || id.equals("tconstruct:item_part_builder") || id.equals("tconstruct:tool_building");
    }
    private static String recipeType(Recipe<?> recipe) {
        ResourceLocation id = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        return id == null ? "" : id.toString();
    }
    private static Set<String> stations(ServerLevel level, BlockPos center) {
        Set<String> found = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-8, -3, -8), center.offset(8, 3, 8))) {
            BlockState state = level.getBlockState(pos);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id == null) continue;
            String name = id.toString();
            if (name.equals("farmersdelight:cooking_pot") && !level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.CAMPFIRES)
                    && !BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock()).toString()
                    .equals("farmersdelight:stove")) continue;
            found.add(name);
        }
        return found;
    }
    private static boolean hasStation(Set<String> stations, Recipe<?> recipe) {
        String type = recipeType(recipe);
        String station = switch (type) {
            case "minecraft:crafting" -> "minecraft:crafting_table";
            case "minecraft:stonecutting" -> "minecraft:stonecutter";
            case "minecraft:smelting" -> "minecraft:furnace";
            case "minecraft:smoking" -> "minecraft:smoker";
            case "minecraft:blasting" -> "minecraft:blast_furnace";
            case "farmersdelight:cutting" -> "farmersdelight:cutting_board";
            case "farmersdelight:cooking" -> "farmersdelight:cooking_pot";
            case "hexerei:woodcutting" -> "hexerei:willow_woodcutter";
            case "tconstruct:item_part_builder" -> "tconstruct:part_builder";
            case "tconstruct:tool_building" -> "tconstruct:tinker_station";
            default -> "";
        };
        return stations.contains(station) || type.equals("minecraft:crafting")
                && stations.contains("tconstruct:crafting_station")
                || type.equals("hexerei:woodcutting") && (stations.contains("hexerei:mahogany_woodcutter")
                || stations.contains("hexerei:witch_hazel_woodcutter"));
    }
}
