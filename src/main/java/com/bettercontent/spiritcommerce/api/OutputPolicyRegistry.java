package com.bettercontent.spiritcommerce.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Pure registration/commit gate shared by the native facade and deterministic tests. */
public final class OutputPolicyRegistry<K,T> {
    public record Decision<K>(boolean allowed, List<K> denyingOwners) {
        public Decision { denyingOwners = List.copyOf(denyingOwners); }
    }
    private final Map<K,Predicate<T>> providers = new LinkedHashMap<>();
    private final UnaryOperator<T> isolate;
    private boolean frozen;
    public OutputPolicyRegistry(UnaryOperator<T> isolate) { this.isolate = Objects.requireNonNull(isolate); }

    public synchronized void register(K owner, Predicate<T> allowed) {
        Objects.requireNonNull(owner); Objects.requireNonNull(allowed);
        if (frozen) throw new IllegalStateException("Renewable output registration closed: " + owner);
        if (providers.putIfAbsent(owner,allowed) != null) throw new IllegalArgumentException("Duplicate renewable output policy: " + owner);
    }
    /** Providers are conjunctive: any denial wins, including overlapping policies. */
    public synchronized Decision<K> decision(T output) {
        Objects.requireNonNull(output); frozen = true;
        List<K> denied = new ArrayList<>();
        for (var provider : providers.entrySet()) {
            try { if (!provider.getValue().test(isolate.apply(output))) denied.add(provider.getKey()); }
            catch (RuntimeException failure) { throw new IllegalStateException("Renewable output policy failed: " + provider.getKey(),failure); }
        }
        return new Decision<>(denied.isEmpty(),denied);
    }
    public boolean allows(T output) { return decision(output).allowed(); }
    /** Owner-negative path never applies NPC production policy to player-funded/unknown merchants. */
    public boolean allowsForOwner(boolean renewableNpcOwner, T output) {
        return !renewableNpcOwner || allows(output);
    }
    /** Re-evaluate current policy immediately BEFORE the real transaction callback. No refund path needed. */
    public boolean commitIfAllowed(Iterable<T> generatedOutputs, BooleanSupplier transaction) {
        Objects.requireNonNull(generatedOutputs); Objects.requireNonNull(transaction);
        for (T output : generatedOutputs) if (!allows(output)) return false;
        return transaction.getAsBoolean();
    }
}
