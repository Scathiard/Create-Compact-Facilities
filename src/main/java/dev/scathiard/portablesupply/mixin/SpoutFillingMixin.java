package dev.scathiard.portablesupply.mixin;

import com.simibubi.create.content.fluids.spout.FillingBySpout;
import dev.scathiard.portablesupply.SourceFilling;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = FillingBySpout.class, remap = false)
abstract class SpoutFillingMixin {
    @Inject(method = "canItemBeFilled", at = @At("HEAD"), cancellable = true)
    private static void portableSupply$accept(Level level, ItemStack stack, CallbackInfoReturnable<Boolean> result) {
        if (SourceFilling.isWorkpiece(stack)) result.setReturnValue(true);
    }

    @Inject(method = "getRequiredAmountForItem", at = @At("HEAD"), cancellable = true)
    private static void portableSupply$quote(Level level, ItemStack stack, FluidStack fluid, CallbackInfoReturnable<Integer> result) {
        if (SourceFilling.isWorkpiece(stack)) result.setReturnValue(SourceFilling.required(stack, fluid));
    }

    @Inject(method = "fillItem", at = @At("HEAD"), cancellable = true)
    private static void portableSupply$finish(Level level, int amount, ItemStack stack, FluidStack fluid,
                                             CallbackInfoReturnable<ItemStack> result) {
        if (SourceFilling.isWorkpiece(stack)) result.setReturnValue(SourceFilling.finish(stack, amount, fluid));
    }
}
