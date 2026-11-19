package dev.scathiard.compactfacilities.content.fluidsource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import java.util.List;

public record FluidSourceCharge(SimpleFluidContent fluid, int fills, int target) {
    public static final Codec<FluidSourceCharge> CODEC = RecordCodecBuilder.<FluidSourceCharge>create(instance -> instance.group(
        SimpleFluidContent.CODEC.fieldOf("fluid").forGetter(FluidSourceCharge::fluid),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("fills").forGetter(FluidSourceCharge::fills),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("target").forGetter(FluidSourceCharge::target)
    ).apply(instance, FluidSourceCharge::new)).validate(charge -> charge.valid()
        ? DataResult.success(charge) : DataResult.error(() -> "Invalid compact fluid source progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidSourceCharge> STREAM_CODEC = StreamCodec.composite(
        SimpleFluidContent.STREAM_CODEC, FluidSourceCharge::fluid,
        ByteBufCodecs.VAR_INT, FluidSourceCharge::fills, ByteBufCodecs.VAR_INT, FluidSourceCharge::target, FluidSourceCharge::new);

    public boolean valid() {
        return !fluid.isEmpty() && fills > 0 && fills <= target;
    }

    public boolean complete() {
        return valid() && fills == target;
    }

    public static FluidSourceCharge of(FluidStack fluid, int fills, int target) {
        return new FluidSourceCharge(SimpleFluidContent.copyOf(fluid.copyWithAmount(FluidSourceFilling.DOSE)), fills, target);
    }

    public ItemStack toItem() {
        var stack = new ItemStack(complete() ? ModItems.FLUID_SOURCE.get() : ModItems.INCOMPLETE_FLUID_SOURCE.get());
        stack.set(ModDataComponents.FLUID_SOURCE_CHARGE, this);
        return stack;
    }

    static void describe(ItemStack stack, List<Component> lines) {
        FluidSourceCharge charge = stack.get(ModDataComponents.FLUID_SOURCE_CHARGE);
        if (charge == null || !charge.valid()) {
            lines.add(Component.translatable("tooltip.compact_facilities.unbound"));
            return;
        }
        lines.add(Component.translatable("tooltip.compact_facilities.fluid", charge.fluid().copy().getHoverName()));
        if (!charge.complete()) {
            lines.add(Component.translatable("tooltip.compact_facilities.progress", charge.fills(), charge.target()));
        }
    }
}
