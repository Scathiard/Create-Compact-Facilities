package dev.scathiard.compactfacilities.content.fluidsource;

import dev.scathiard.compactfacilities.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class FluidSourceWorkpieceItem extends Item {
    public FluidSourceWorkpieceItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        if (stack.is(ModItems.FLUID_SOURCE_BLANK.get())) lines.add(Component.translatable("tooltip.compact_facilities.blank"));
        else FluidSourceCharge.describe(stack, lines);
    }
}
