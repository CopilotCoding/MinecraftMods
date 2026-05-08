package com.grace.ezenchant;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HIGHLIGHT_TREASURE = BUILDER
            .comment("Highlight treasure enchantments (Mending, Frost Walker, etc.) in gold")
            .define("highlightTreasureEnchantments", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}
