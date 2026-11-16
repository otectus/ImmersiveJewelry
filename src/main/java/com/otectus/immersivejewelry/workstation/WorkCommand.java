package com.otectus.immersivejewelry.workstation;

/**
 * The discrete actions an operator can take at a station. Each is validated on the server against the lease, the real
 * stacks and the current work; arguments are read field by field from a small compound.
 */
public enum WorkCommand {
    /** Wheel: plan a cut ({@code Cut}); after material was removed only with {@code Confirm}, as a recut. */
    PLAN_CUT,
    /** Wheel: drop a plan nothing was done to yet. */
    CLEAR_PLAN,
    /** Wheel: finish the cut; with {@code Rough} also before every region reached its window. */
    FINISH_CUT,
    /** Wheel: take the stone (with its work) into the inventory. */
    TAKE_STONE,
    /** Bench: put a real stack ({@code From} an inventory slot, or -1 for the cursor) into {@code Target}/{@code Index}. */
    PLACE,
    /** Bench: take the stack at {@code Target}/{@code Index} into the inventory. */
    TAKE,
    /** Bench: choose the design ({@code Design}) for the frame in the clamp. */
    SELECT_DESIGN,
    /** Bench: choose the finish ({@code Finish}) the piece will get. */
    SELECT_FINISH,
    /** Bench: the tweezers let a tray stone ({@code Tray}) go into a socket ({@code Socket}) at {@code Dx}, {@code Dy}, {@code Turn}. */
    SEAT,
    /** Bench: lift a stone that nothing holds yet out of its socket ({@code Socket}) into the tray ({@code Tray}, -1 = first free). */
    LIFT,
    /** Bench: let go of an uncommitted plan; resting stones go back to the tray. */
    CANCEL_PLAN,
    /** Bench: start working on a finished piece: {@code Kind} retighten, remove, repair or refinish ({@code Finish}). */
    BEGIN,
    /** Bench: declare retightening done. */
    FINISH_WORK,
    /** Bench: pack committed work into an unfinished-jewelry carrier. */
    PACK,
    // Added in protocol 5 (1.3.0). Ordinals are the wire format: new actions go at the end, never between.
    /** Bench: open ({@code Open} true) or shut the staging drawer. */
    DRAWER,
    /** Bench: put {@code Count} (0 = all) of an inventory stack ({@code From}, -1 = the cursor) into the staging drawer. */
    STAGE_IN,
    /** Bench: move {@code Count} (0 = all) of staged stack {@code Index} {@code To} {@code work} (where it belongs) or {@code inventory}. */
    MOVE_STAGED,
    /**
     * Bench: move exactly the stones design {@code Design} still needs into the tray: of gem {@code Gem} (optional), preferred
     * inventory slots {@code Prefer} first, staged stones too with {@code FromStaging}. The server counts and chooses.
     */
    PREPARE
}
