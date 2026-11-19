package dev.scathiard.compactfacilities.registry;

import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CompactFacilities.ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidSourceEntity>> FLUID_SOURCE =
        TYPES.register("compact_fluid_source", () -> BlockEntityType.Builder.of(FluidSourceEntity::new, ModBlocks.FLUID_SOURCE.get()).build(null));

    private ModBlockEntities() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
