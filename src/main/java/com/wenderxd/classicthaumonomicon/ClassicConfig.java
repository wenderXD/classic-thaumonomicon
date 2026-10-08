package com.wenderxd.classicthaumonomicon;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings, editable from the mod list's Config button. */
public final class ClassicConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue CLASSIC_BACKGROUNDS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ENABLED = builder.comment("Open the classic research map in place of Thaumaturge's own.").translation("classic_thaumonomicon.configuration.enabled").define("enabled", true);
        CLASSIC_BACKGROUNDS = builder
                .comment("Show every Thaumaturge category over the one violet nebula, as Thaumcraft 4 did. Eldritch keeps its own sky either way, as it did in Thaumcraft 4,",
                        "and categories added by other mods always use their own background. Off: every category uses the background Thaumaturge gives it.")
                .translation("classic_thaumonomicon.configuration.classicBackgrounds").define("classicBackgrounds", true);
        SPEC = builder.build();
    }

    private ClassicConfig() {}
}
