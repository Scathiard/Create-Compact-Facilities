package dev.scathiard.compactfacilities;

import dev.scathiard.compactfacilities.config.ServerConfig;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceEntity;
import dev.scathiard.compactfacilities.registry.ModBlockEntities;
import dev.scathiard.compactfacilities.registry.ModBlocks;
import dev.scathiard.compactfacilities.registry.ModCreativeContents;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModIngredientTypes;
import dev.scathiard.compactfacilities.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(CompactFacilities.ID)
public final class CompactFacilities {
    public static final String ID = "compact_facilities";

    public CompactFacilities(IEventBus bus, ModContainer mod) {
        ModBlocks.register(bus);
        ModItems.register(bus);
        ModBlockEntities.register(bus);
        ModDataComponents.register(bus);
        ModIngredientTypes.register(bus);
        ModCreativeContents.register(bus);
        ServerConfig.register(mod);
        bus.addListener(FluidSourceEntity::registerCapabilities);
    }
}
