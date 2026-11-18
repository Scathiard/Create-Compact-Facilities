package dev.scathiard.portablesupply;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

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
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof FluidSourceEntity source && source.charge() != null)
            return source.charge().toItem();
        return new ItemStack(this);
    }
}
