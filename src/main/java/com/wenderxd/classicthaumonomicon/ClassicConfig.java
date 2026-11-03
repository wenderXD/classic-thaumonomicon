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
                .comment("Use the same violet nebula behind every category with one of Thaumaturge's backgrounds, like Thaumcraft 4. Eldritch keeps its own background either way,",
                        "and so do categories from other mods that have their own art. When off, every category uses the background it was given.")
                .translation("classic_thaumonomicon.configuration.classicBackgrounds").define("classicBackgrounds", true);
        SPEC = builder.build();
    }

    private ClassicConfig() {}
}
