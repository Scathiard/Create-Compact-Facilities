package dev.scathiard.compactfacilities.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.scathiard.compactfacilities.Charge;
import dev.scathiard.compactfacilities.FluidSourceEntity;
import dev.scathiard.compactfacilities.CompactFacilities;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.model.data.ModelData;

@EventBusSubscriber(modid = CompactFacilities.ID, value = Dist.CLIENT)
public final class SourceRendering {
    private SourceRendering() {}

    @SubscribeEvent
    public static void blockRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(CompactFacilities.SOURCE_ENTITY.get(), context -> new PlacedRenderer());
    }

    @SubscribeEvent
    public static void itemRenderers(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            private SourceItemRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new SourceItemRenderer(Minecraft.getInstance());
                return renderer;
            }
        }, CompactFacilities.UNFINISHED.get(), CompactFacilities.SOURCE_ITEM.get());
    }

    private static void fluid(Charge charge, PoseStack pose, MultiBufferSource buffers, int light) {
        if (charge == null || !charge.valid()) return;
        float inset = 1 / 16f;
        float max = 1 - inset;
        float surface = inset + (max - inset) * ((float) charge.fills() / charge.target());
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(charge.fluid().copy(),
            inset, inset, inset, max, surface, max, buffers, pose, light, true, true);
    }

    private static final class PlacedRenderer implements BlockEntityRenderer<FluidSourceEntity> {
        @Override
        public void render(FluidSourceEntity entity, float tick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            fluid(entity.charge(), pose, buffers, light);
        }
    }

    private static final class SourceItemRenderer extends BlockEntityWithoutLevelRenderer {
        SourceItemRenderer(Minecraft client) { super(client.getBlockEntityRenderDispatcher(), client.getEntityModels()); }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers,
                                 int light, int overlay) {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(CompactFacilities.SOURCE.get().defaultBlockState(),
                pose, buffers, light, overlay, ModelData.EMPTY, null);
            fluid(stack.get(CompactFacilities.CHARGE), pose, buffers, light);
        }
    }
}
