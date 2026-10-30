package com.bettercontent.spiritcommerce.api;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputPolicyRegistryTest {
    private static OutputPolicyRegistry<String,String> registry() { return new OutputPolicyRegistry<>(value -> value); }
    @Test void noProviderKeepsAllOutputsAndExistingTransactionsUnchanged() {
        var policies = registry(); var commits = new AtomicInteger();
        assertTrue(policies.allows("meal")); assertTrue(policies.allows("raw_carrot"));
        assertTrue(policies.commitIfAllowed(List.of("meal"), () -> { commits.incrementAndGet(); return true; }));
        assertEquals(1,commits.get());
    }
    @Test void mealProviderDoesNotDenyRawIngredientsStaplesOrNonfood() {
        var policies = registry(); policies.register("cooking",value -> !value.equals("meal"));
        assertFalse(policies.allows("meal"));
        for (String value : List.of("raw_carrot","flour","basic_bread","milk","tools")) assertTrue(policies.allows(value));
    }
    @Test void duplicateLateAndOverlappingPoliciesHaveExplicitSemantics() {
        var policies = registry(); policies.register("cooking",value -> !value.equals("meal"));
        assertThrows(IllegalArgumentException.class, () -> policies.register("cooking",value -> true));
        policies.register("other",value -> !value.equals("meal"));
        assertEquals(List.of("cooking","other"),policies.decision("meal").denyingOwners());
        assertThrows(IllegalStateException.class, () -> policies.register("late",value -> true));
        assertThrows(UnsupportedOperationException.class, () -> policies.decision("meal").denyingOwners().add("injected"));
    }
    @Test void eachPredicateGetsAnIsolatedCopy() {
        var policies = new OutputPolicyRegistry<String,StringBuilder>(value -> new StringBuilder(value));
        policies.register("mutating",value -> { value.append(" changed"); return true; });
        policies.register("observer",value -> value.toString().equals("meal"));
        var original = new StringBuilder("meal"); assertTrue(policies.allows(original)); assertEquals("meal",original.toString());
    }
    @Test void staleOfferIsRecheckedBeforePaymentOutputOrRefundCallbacks() {
        var currentlyAllowed = new AtomicBoolean(true); var policies = registry();
        policies.register("cooking",value -> !value.equals("meal") || currentlyAllowed.get());
        assertTrue(policies.allows("meal")); // An earlier listing/quote.
        currentlyAllowed.set(false);
        AtomicInteger payment = new AtomicInteger(), output = new AtomicInteger(), refund = new AtomicInteger();
        assertFalse(policies.commitIfAllowed(List.of("meal"), () -> {
            payment.incrementAndGet(); output.incrementAndGet(); return true;
        }));
        assertEquals(0,payment.get()); assertEquals(0,output.get()); assertEquals(0,refund.get());
    }
    @Test void deniedSubrecipeOrRemainderVetoesWholeTransactionBeforeAnyDebit() {
        var policies = registry(); policies.register("cooking",value -> !value.equals("meal"));
        var debits = new AtomicInteger();
        assertFalse(policies.commitIfAllowed(List.of("staple","meal","container"), () -> { debits.incrementAndGet(); return true; }));
        assertEquals(0,debits.get());
    }
    @Test void finiteOldOrPlayerGivenStockIsTransferredWithoutProductionRestriction() {
        var policies = registry(); policies.register("cooking",value -> !value.equals("meal"));
        assertFalse(policies.allows("meal")); var transfers = new AtomicInteger();
        assertTrue(policies.commitIfAllowed(List.of(), () -> { transfers.incrementAndGet(); return true; }));
        assertEquals(1,transfers.get()); // No new output: finite stock transfer, not renewable production.
    }
    @Test void providerFailureIsVisibleAndNeverFallsThroughToPayment() {
        var policies = registry(); policies.register("broken",value -> { throw new IllegalArgumentException("bad policy"); });
        var debits = new AtomicInteger();
        var failure = assertThrows(IllegalStateException.class, () -> policies.commitIfAllowed(List.of("meal"), () -> { debits.incrementAndGet(); return true; }));
        assertTrue(failure.getMessage().contains("broken")); assertEquals(0,debits.get());
    }
    @Test void playerFundedOrUnknownMerchantOwnerIsNotSubjectToNpcPolicy() {
        var policies = registry(); policies.register("cooking",value -> !value.equals("meal"));
        assertTrue(policies.allowsForOwner(false,"meal"));
        assertFalse(policies.allowsForOwner(true,"meal"));
        assertTrue(policies.allowsForOwner(true,"raw_carrot"));
    }
    @Test void ownerNegativeDoesNotEvenInvokeAProviderThatRejectsOrThrows() {
        var policies = registry(); var queries = new AtomicInteger();
        policies.register("broken",value -> { queries.incrementAndGet(); throw new IllegalArgumentException("broken"); });
        assertTrue(policies.allowsForOwner(false,"player_stocked_meal")); assertEquals(0,queries.get());
    }
    @Test void failedPaymentDoesNotBecomeAnApprovedCommit() {
        var policies = registry(); assertFalse(policies.commitIfAllowed(List.of("staple"), () -> false));
    }
}
