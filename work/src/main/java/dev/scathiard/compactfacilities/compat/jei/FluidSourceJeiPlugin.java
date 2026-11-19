package dev.scathiard.compactfacilities.compat.jei;

import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

@JeiPlugin
public final class FluidSourceJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CompactFacilities.ID, "fluid_source");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(ModItems.FLUID_SOURCE_BLANK.toStack(),
            Component.translatable("tooltip.compact_facilities.jei.fluid_source_rule"));
    }
}
