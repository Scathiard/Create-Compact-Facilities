package dev.scathiard.cannedfacilities;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

import java.util.List;

public record Charge(SimpleFluidContent fluid, int fills, int target) {
    public static final Codec<Charge> CODEC = RecordCodecBuilder.<Charge>create(instance -> instance.group(
        SimpleFluidContent.CODEC.fieldOf("fluid").forGetter(Charge::fluid),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("fills").forGetter(Charge::fills),
        Codec.intRange(1, Integer.MAX_VALUE).fieldOf("target").forGetter(Charge::target)
    ).apply(instance, Charge::new)).validate(charge -> charge.valid()
        ? DataResult.success(charge) : DataResult.error(() -> "Invalid canned fluid source progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, Charge> STREAM_CODEC = StreamCodec.composite(
        SimpleFluidContent.STREAM_CODEC, Charge::fluid,
        ByteBufCodecs.VAR_INT, Charge::fills, ByteBufCodecs.VAR_INT, Charge::target, Charge::new);

    public boolean valid() {
        return !fluid.isEmpty() && fills > 0 && fills <= target;
    }

    public boolean complete() {
        return valid() && fills == target;
    }

    public static Charge of(FluidStack fluid, int fills, int target) {
        return new Charge(SimpleFluidContent.copyOf(fluid.copyWithAmount(SourceFilling.DOSE)), fills, target);
    }

    public ItemStack toItem() {
        var stack = new ItemStack(complete() ? CannedFacilities.SOURCE_ITEM.get() : CannedFacilities.UNFINISHED.get());
        stack.set(CannedFacilities.CHARGE, this);
        return stack;
    }

    static void describe(ItemStack stack, List<Component> lines) {
        Charge charge = stack.get(CannedFacilities.CHARGE);
        if (charge == null || !charge.valid()) {
            lines.add(Component.translatable("tooltip.canned_facilities.unbound"));
            return;
        }
        lines.add(Component.translatable("tooltip.canned_facilities.fluid", charge.fluid().copy().getHoverName()));
        if (!charge.complete()) {
            lines.add(Component.translatable("tooltip.canned_facilities.progress", charge.fills(), charge.target()));
        }
    }
}
