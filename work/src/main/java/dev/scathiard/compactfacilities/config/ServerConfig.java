package dev.scathiard.compactfacilities.config;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    private static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue REQUIRED_FILLS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REQUIRED_FILLS = builder.comment("Spout operations for new non-water sources. 0 follows Create's hose pulley threshold.",
                "Water takes 3 operations. Each operation consumes 1000 mB.")
            .defineInRange("requiredFills", 0, 0, Integer.MAX_VALUE);
        SPEC = builder.build();
    }

    private ServerConfig() {}

    public static void register(ModContainer mod) {
        mod.registerConfig(ModConfig.Type.SERVER, SPEC);
    }
}
