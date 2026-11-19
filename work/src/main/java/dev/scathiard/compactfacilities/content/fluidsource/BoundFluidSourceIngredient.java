package dev.scathiard.compactfacilities.content.fluidsource;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModIngredientTypes;
import dev.scathiard.compactfacilities.registry.ModItems;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.stream.Stream;

public record BoundFluidSourceIngredient(Holder<Fluid> fluid) implements ICustomIngredient {
    public static final MapCodec<BoundFluidSourceIngredient> CODEC = BuiltInRegistries.FLUID.holderByNameCodec()
        .validate(holder -> holder.value() == Fluids.EMPTY ? DataResult.error(() -> "Expected a non-empty fluid") : DataResult.success(holder))
        .fieldOf("fluid").xmap(BoundFluidSourceIngredient::new, BoundFluidSourceIngredient::fluid);

    @Override
    public boolean test(ItemStack stack) {
        if (!stack.is(ModItems.FLUID_SOURCE.get())) return false;
        FluidSourceCharge charge = stack.get(ModDataComponents.FLUID_SOURCE_CHARGE);
        return charge != null && charge.complete() && charge.fluid().is(fluid);
    }

    @Override
    public Stream<ItemStack> getItems() {
        return Stream.of(FluidSourceCharge.of(new FluidStack(fluid, FluidSourceFilling.DOSE), 1, 1).toItem());
    }

    @Override
    public boolean isSimple() { return false; }

    @Override
    public IngredientType<?> getType() { return ModIngredientTypes.BOUND_FLUID_SOURCE.get(); }
}
