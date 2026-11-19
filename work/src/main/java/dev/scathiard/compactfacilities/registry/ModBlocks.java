package dev.scathiard.compactfacilities.registry;

import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CompactFacilities.ID);
    public static final DeferredBlock<FluidSourceBlock> FLUID_SOURCE = BLOCKS.register("compact_fluid_source",
        () -> new FluidSourceBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.COPPER).noOcclusion()));

    private ModBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
