package com.otectus.immersivejewelry.data;

/** The datapack definition folders, {@code data/<namespace>/immersive_jewelry/<folder>/<path>.json}. */
public enum DefinitionKind {
    FORMS("jewelry_forms"),
    CUTS("gem_cuts"),
    FINISHES("finishes"),
    METALS("metals"),
    TRAITS("traits"),
    GEMS("gems"),
    /** Optional interaction tuning (1.2.0): {@code data/<ns>/immersive_jewelry/work_profiles/<path>.json}. */
    WORK_PROFILES("work_profiles");

    private final String folder;

    DefinitionKind(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }

    /** The resource-manager directory for this kind. */
    public String directory() {
        return "immersive_jewelry/" + folder;
    }
}
