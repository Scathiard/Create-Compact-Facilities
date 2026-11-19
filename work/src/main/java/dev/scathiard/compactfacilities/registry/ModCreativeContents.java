package dev.scathiard.compactfacilities.registry;

import com.simibubi.create.AllCreativeModeTabs;
import dev.scathiard.compactfacilities.CompactFacilities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeContents {
    private static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CompactFacilities.ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.compact_facilities"))
            .icon(ModItems.FLUID_SOURCE::toStack)
            .withTabsBefore(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey(), AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.FLUID_SOURCE_BLANK.get());
                output.accept(ModItems.FLUID_SOURCE.get());
            })
            .build());

    private ModCreativeContents() {}

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
