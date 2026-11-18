package dev.scathiard.portablesupply;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public final class FluidSourceEntity extends BlockEntity {
    @Nullable private Charge charge;
    private final Outlet outlet = new Outlet();

    public FluidSourceEntity(BlockPos pos, BlockState state) {
        super(PortableSupply.SOURCE_ENTITY.get(), pos, state);
    }

    public @Nullable Charge charge() { return charge; }
    public IFluidHandler outlet() { return outlet; }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Charge incoming = input.get(PortableSupply.CHARGE.get());
        charge = incoming != null && incoming.complete() ? incoming : null;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (charge != null) builder.set(PortableSupply.CHARGE.get(), charge);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (charge != null) tag.put("Charge", Charge.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), charge).getOrThrow());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        charge = tag.contains("Charge") ? Charge.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("Charge"))
            .resultOrPartial(LogUtils.getLogger()::warn).filter(Charge::complete).orElse(null) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

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
            if (isRemoved() || amount <= 0 || charge == null || !SourceFilling.allowed(charge.fluid().getFluid())) return FluidStack.EMPTY;
            var result = charge.fluid().copy();
            result.setAmount(amount);
            return result;
        }
    }
}
