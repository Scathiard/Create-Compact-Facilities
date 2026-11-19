package dev.scathiard.compactfacilities.registry;

import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceCharge;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    private static final DeferredRegister.DataComponents TYPES = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CompactFacilities.ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FluidSourceCharge>> FLUID_SOURCE_CHARGE =
        TYPES.registerComponentType("charge", b -> b.persistent(FluidSourceCharge.CODEC).networkSynchronized(FluidSourceCharge.STREAM_CODEC));

    private ModDataComponents() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
