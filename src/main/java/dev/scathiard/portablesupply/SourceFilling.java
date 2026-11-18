package dev.scathiard.portablesupply;

import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

public final class SourceFilling {
    public static final int DOSE = FluidType.BUCKET_VOLUME;

    private SourceFilling() {}

    public static boolean isWorkpiece(ItemStack stack) {
        return stack.is(PortableSupply.BLANK.get()) || stack.is(PortableSupply.UNFINISHED.get());
    }

    public static boolean allowed(Fluid fluid) {
        var settings = AllConfigs.server().fluids;
        if (settings.hosePulleyBlockThreshold.get() < 0 || !settings.bottomlessFluidMode.get().test(fluid)) return false;
        var placed = fluid.defaultFluidState().createLegacyBlock().getFluidState();
        return placed.isSource() && fluid.isSame(placed.getType());
    }

    private static int target() {
        int override = PortableSupply.REQUIRED_FILLS.get();
        return override > 0 ? override : Math.max(1, AllConfigs.server().fluids.hosePulleyBlockThreshold.get());
    }

    public static int required(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty() || !allowed(fluid.getFluid())) return -1;
        Charge charge = stack.get(PortableSupply.CHARGE);
        if (stack.is(PortableSupply.BLANK.get())) return charge == null ? DOSE : -1;
        return stack.is(PortableSupply.UNFINISHED.get()) && charge != null && charge.valid()
            && !charge.complete() && charge.fluid().isSameFluidSameComponents(fluid) ? DOSE : -1;
    }

    public static ItemStack finish(ItemStack stack, int requested, FluidStack fluid) {
        if (requested != DOSE || fluid.getAmount() < DOSE || required(stack, fluid) != DOSE) return ItemStack.EMPTY;
        Charge previous = stack.get(PortableSupply.CHARGE);
        Charge next = previous == null ? Charge.of(fluid, 1, target())
            : new Charge(previous.fluid(), previous.fills() + 1, previous.target());
        ItemStack result = next.toItem();
        stack.shrink(1);
        fluid.shrink(DOSE);
        return result;
    }
}
