package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

final class VisitOutcomeTest {
    @Test void nativeAdmissionOrRollbackNeverBecomesACommittedVisit() {
        for (var outcome : VisitOutcome.values()) if (outcome != VisitOutcome.COMMITTED) {
            assertEquals("retry_pending", outcome.result());
            assertNotEquals("committed", outcome.result());
            assertTrue(outcome.reason().matches("[a-z_]+"));
        }
    }
    @Test void onlyPublishedTraderCampAndScheduleUsesCommittedResult() {
        assertEquals("committed", VisitOutcome.COMMITTED.result());
        assertEquals("trader_camp_and_schedule_updated", VisitOutcome.COMMITTED.reason());
        assertEquals("camp_rejected", VisitOutcome.CAMP_REJECTED.reason());
    }
}
