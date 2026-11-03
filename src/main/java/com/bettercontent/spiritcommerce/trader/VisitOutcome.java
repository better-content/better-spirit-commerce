package com.bettercontent.spiritcommerce.trader;

/** Observations after existing native rollback or schedule publication, never a spawn-admission policy. */
public enum VisitOutcome {
    SPAWN_REJECTED("retry_pending", "native_spawn_rejected"),
    TRADER_MISSING("retry_pending", "new_trader_not_observed"),
    ANCHOR_MISSING("retry_pending", "anchor_unavailable"),
    CAMP_REJECTED("retry_pending", "camp_rejected"),
    COMMITTED("committed", "trader_camp_and_schedule_updated");
    private final String result, reason;
    VisitOutcome(String result, String reason) { this.result = result; this.reason = reason; }
    public String result() { return result; }
    public String reason() { return reason; }
}
