package dev.scathiard.compactfacilities.registry;

import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceCharge;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceFilling;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

public final class ModCreativeContents {
    private ModCreativeContents() {}

    public static void populate(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.INGREDIENTS) return;
        event.accept(ModItems.FLUID_SOURCE_BLANK.get());
        for (var fluid : List.of(Fluids.WATER, Fluids.LAVA)) {
            event.accept(FluidSourceCharge.of(new FluidStack(fluid, FluidSourceFilling.DOSE), 1, 1).toItem());
        }
    }
}
