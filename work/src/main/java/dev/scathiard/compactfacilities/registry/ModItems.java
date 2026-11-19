package dev.scathiard.compactfacilities.registry;

import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceBlockItem;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceWorkpieceItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CompactFacilities.ID);
    public static final DeferredItem<FluidSourceWorkpieceItem> FLUID_SOURCE_BLANK = ITEMS.register("compact_fluid_source_blank",
        () -> new FluidSourceWorkpieceItem(new Item.Properties()));
    public static final DeferredItem<FluidSourceWorkpieceItem> INCOMPLETE_FLUID_SOURCE = ITEMS.register("incomplete_compact_fluid_source",
        () -> new FluidSourceWorkpieceItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<FluidSourceBlockItem> FLUID_SOURCE = ITEMS.register("compact_fluid_source",
        () -> new FluidSourceBlockItem(ModBlocks.FLUID_SOURCE.get(), new Item.Properties()));

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
