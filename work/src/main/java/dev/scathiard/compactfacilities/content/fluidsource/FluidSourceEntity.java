package dev.scathiard.compactfacilities.content.fluidsource;

import com.mojang.logging.LogUtils;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;
import dev.scathiard.compactfacilities.registry.ModBlockEntities;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;

public final class FluidSourceEntity extends BlockEntity implements IHaveGoggleInformation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CHARGE_KEY = "Charge";
    private final Outlet outlet = new Outlet();
    @Nullable private FluidSourceCharge charge;
    // Keep the original disk field even when its components cannot be decoded in this pack.
    @Nullable private Tag storedCharge;
    @Nullable private FluidSourceCharge pendingCharge;

    public FluidSourceEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_SOURCE.get(), pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ModBlockEntities.FLUID_SOURCE.get(),
            (source, side) -> source.outlet);
    }

    public @Nullable FluidSourceCharge charge() { return charge; }
    public IFluidHandler outlet() { return outlet; }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        CreateLang.builder().add(Component.translatable("goggles.compact_facilities.fluid_source.header")).forGoggles(tooltip);
        if (charge == null) {
            CreateLang.builder().add(Component.translatable("tooltip.compact_facilities.unbound"))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            return true;
        }

        Component name = charge.fluid().copy().getHoverName();
        if (FluidSourceFilling.allowed(charge.fluid().getFluid())) {
            CreateLang.builder().add(Component.translatable("goggles.compact_facilities.fluid_source.fluid", name))
                .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        } else {
            CreateLang.builder().add(Component.translatable("tooltip.compact_facilities.fluid", name))
                .style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            CreateLang.builder().add(Component.translatable("goggles.compact_facilities.fluid_source.disabled"))
                .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
        }
        return true;
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        charge = null;
        setCharge(input.get(ModDataComponents.FLUID_SOURCE_CHARGE.get()));
    }

    boolean setCharge(@Nullable FluidSourceCharge incoming) {
        pendingCharge = null;
        if (incoming == null) {
            charge = null;
            storedCharge = null;
        } else if (incoming.complete()) {
            pendingCharge = incoming;
            if (level != null) encodePending(level.registryAccess());
        } else {
            report("apply", "incomplete fluid source");
        }
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return charge == incoming;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        encodePending(level.registryAccess());
    }

    private void encodePending(HolderLookup.Provider registries) {
        var incoming = pendingCharge;
        if (incoming == null) return;
        pendingCharge = null;
        try {
            var encoded = FluidSourceCharge.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), incoming);
            encoded.ifSuccess(value -> {
                storedCharge = value;
                charge = incoming;
            }).ifError(error -> report("encode", error.message()));
        } catch (RuntimeException error) {
            report("encode", error.toString());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (charge != null) builder.set(ModDataComponents.FLUID_SOURCE_CHARGE.get(), charge);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        encodePending(registries);
        if (storedCharge != null) tag.put(CHARGE_KEY, storedCharge.copy());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = null;
        pendingCharge = null;
        Tag raw = tag.get(CHARGE_KEY);
        storedCharge = raw == null ? null : raw.copy();
        if (raw == null) return;
        try {
            var decoded = FluidSourceCharge.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), raw);
            decoded.ifSuccess(value -> {
                if (value.complete()) charge = value;
                else report("decode", "incomplete fluid source");
            }).ifError(error -> report("decode", error.message()));
        } catch (RuntimeException error) {
            report("decode", error.toString());
        }
    }

    private void report(String operation, String message) {
        LOGGER.warn("{} at {} could not {} field {}. {}", ModBlockEntities.FLUID_SOURCE.getId(), worldPosition,
            operation, CHARGE_KEY, message);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var update = saveWithoutMetadata(registries);
        if (charge == null) {
            update.remove(CHARGE_KEY);
            // NeoForge skips empty block-entity packets. Clear a previously active client too.
            update.putBoolean("Inactive", true);
        }
        return update;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private final class Outlet implements IFluidHandler {
        @Override public int getTanks() { return 1; }
        @Override public int getTankCapacity(int tank) { return tank == 0 ? Integer.MAX_VALUE : 0; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank == 0 ? drain(Integer.MAX_VALUE, FluidAction.SIMULATE) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (charge == null || !charge.fluid().isSameFluidSameComponents(resource)) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int amount, FluidAction action) {
            if (isRemoved() || amount <= 0 || charge == null || !FluidSourceFilling.allowed(charge.fluid().getFluid())) return FluidStack.EMPTY;
            var result = charge.fluid().copy();
            result.setAmount(amount);
            return result;
        }
    }
}
