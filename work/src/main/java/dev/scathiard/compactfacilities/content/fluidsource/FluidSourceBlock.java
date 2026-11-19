package dev.scathiard.compactfacilities.content.fluidsource;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.fluid.FluidHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.fluids.FluidStack;

public final class FluidSourceBlock extends BaseEntityBlock {
    private static final MapCodec<FluidSourceBlock> CODEC = simpleCodec(FluidSourceBlock::new);

    public FluidSourceBlock(Properties properties) { super(properties); }

    @Override
    protected MapCodec<FluidSourceBlock> codec() { return CODEC; }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new FluidSourceEntity(pos, state); }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.isCreative() || stack.isEmpty() || !(level.getBlockEntity(pos) instanceof FluidSourceEntity source))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        boolean pouring = GenericItemEmptying.canItemBeEmptied(level, stack);
        FluidStack fluid;
        if (pouring) {
            fluid = GenericItemEmptying.emptyItem(level, stack.copyWithCount(1), true).getFirst();
            if (fluid.isEmpty() || !FluidSourceFilling.allowed(fluid.getFluid()))
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else if (source.charge() != null && GenericItemFilling.canItemBeFilled(level, stack)) {
            fluid = source.charge().fluid().copy();
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            if (!source.setCharge(pouring ? FluidSourceCharge.of(fluid, 1, 1) : null)) return ItemInteractionResult.FAIL;
            var sound = pouring ? FluidHelper.getEmptySound(fluid) : FluidHelper.getFillSound(fluid);
            if (sound != null) level.playSound(null, pos, sound, SoundSource.BLOCKS, 1, 1);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof FluidSourceEntity source && source.charge() != null)
            return source.charge().toItem();
        return new ItemStack(this);
    }
}
