package dev.scathiard.compactfacilities.registry;

import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.content.fluidsource.BoundFluidSourceIngredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModIngredientTypes {
    private static final DeferredRegister<IngredientType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, CompactFacilities.ID);
    public static final DeferredHolder<IngredientType<?>, IngredientType<BoundFluidSourceIngredient>> BOUND_FLUID_SOURCE =
        TYPES.register("bound_fluid_source", () -> new IngredientType<>(BoundFluidSourceIngredient.CODEC));

    private ModIngredientTypes() {}

    public static void register(IEventBus bus) {
        TYPES.register(bus);
    }
}
