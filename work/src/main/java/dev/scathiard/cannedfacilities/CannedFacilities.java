package dev.scathiard.cannedfacilities;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.component.DataComponentType;

import java.util.List;

@Mod(CannedFacilities.ID)
public final class CannedFacilities {
    public static final String ID = "canned_facilities";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Charge>> CHARGE =
        COMPONENTS.registerComponentType("charge", b -> b.persistent(Charge.CODEC).networkSynchronized(Charge.STREAM_CODEC));
    public static final DeferredBlock<FluidSourceBlock> SOURCE = BLOCKS.register("canned_fluid_source",
        () -> new FluidSourceBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.COPPER).noOcclusion()));
    public static final DeferredItem<Item> BLANK = ITEMS.register("canned_fluid_source_blank",
        () -> new Workpiece(new Item.Properties()));
    public static final DeferredItem<Item> UNFINISHED = ITEMS.register("incomplete_canned_fluid_source",
        () -> new Workpiece(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<BlockItem> SOURCE_ITEM = ITEMS.register("canned_fluid_source",
        () -> new BlockItem(SOURCE.get(), new Item.Properties()) {
            @Override
            public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
                Charge.describe(stack, lines);
            }
        });
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FluidSourceEntity>> SOURCE_ENTITY =
        ENTITIES.register("canned_fluid_source", () -> BlockEntityType.Builder.of(FluidSourceEntity::new, SOURCE.get()).build(null));

    public static final ModConfigSpec.IntValue REQUIRED_FILLS;
    private static final ModConfigSpec CONFIG;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        REQUIRED_FILLS = builder.comment("Spout operations for new non-water sources. 0 follows Create's hose pulley threshold.",
                "Water takes 3 operations. Each operation consumes 1000 mB.")
            .defineInRange("requiredFills", 0, 0, Integer.MAX_VALUE);
        CONFIG = builder.build();
    }

    public CannedFacilities(IEventBus bus, ModContainer mod) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        COMPONENTS.register(bus);
        ENTITIES.register(bus);
        mod.registerConfig(ModConfig.Type.SERVER, CONFIG);
        bus.addListener(CannedFacilities::capabilities);
        bus.addListener(CannedFacilities::creativeItems);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SOURCE_ENTITY.get(), (source, side) -> source.outlet());
    }

    private static void creativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.INGREDIENTS) return;
        event.accept(BLANK.get());
        for (var fluid : List.of(Fluids.WATER, Fluids.LAVA)) {
            event.accept(Charge.of(new FluidStack(fluid, 1000), 1, 1).toItem());
        }
    }

    private static final class Workpiece extends Item {
        Workpiece(Properties properties) { super(properties); }

        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
            if (stack.is(BLANK.get())) lines.add(Component.translatable("tooltip.canned_facilities.blank"));
            else Charge.describe(stack, lines);
        }
    }
}
