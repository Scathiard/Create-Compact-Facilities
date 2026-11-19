package dev.scathiard.compactfacilities.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
import com.simibubi.create.content.kinetics.crafter.MechanicalCraftingRecipe;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler.GroupedItems;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceCharge;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceEntity;
import dev.scathiard.compactfacilities.registry.ModBlocks;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

final class FluidSourceArchitectureTests {
    private static final String TEST_MOD = "compact_facilities_test";
    private static final ResourceLocation WATER_RECIPE = ResourceLocation.fromNamespaceAndPath(TEST_MOD, "bound_water");
    private static final ResourceLocation LAVA_RECIPE = ResourceLocation.fromNamespaceAndPath(TEST_MOD, "bound_lava");
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TEST_MOD);
    private static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> UNWRITABLE = COMPONENTS.registerComponentType(
        "unwritable", builder -> builder.persistent(Codec.STRING.validate(value -> DataResult.error(() -> "unwritable test fluid component")))
            .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    static void register(IEventBus bus) { COMPONENTS.register(bus); }

    static void recipeSync(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        checkRecipes(level, manager);
        var packet = new ClientboundUpdateRecipesPacket(List.of(manager.byKey(WATER_RECIPE).orElseThrow(), manager.byKey(LAVA_RECIPE).orElseThrow()));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
        try {
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer);
            var received = new RecipeManager(level.registryAccess());
            received.replaceRecipes(decoded.getRecipes());
            require(!buffer.isReadable(), "Incomplete recipe packet read");
            checkRecipes(level, received);
        } finally {
            buffer.release();
        }
        helper.succeed();
    }

    static void checkRecipes(Level level, RecipeManager manager) {
        var crafting = (MechanicalCraftingRecipe) manager.byKey(WATER_RECIPE).orElseThrow().value();
        var deploying = (DeployerApplicationRecipe) manager.byKey(LAVA_RECIPE).orElseThrow().value();
        checkDisplay(crafting.getIngredients().getFirst(), Fluids.WATER);
        checkDisplay(deploying.getRequiredHeldItem(), Fluids.LAVA);

        for (int target : new int[]{3, 10000, 1}) {
            checkMatches(level, crafting, deploying, charged(Fluids.WATER, target, target), true, false);
            checkMatches(level, crafting, deploying, charged(Fluids.LAVA, target, target), false, true);
        }
        checkMatches(level, crafting, deploying, ModItems.FLUID_SOURCE_BLANK.toStack(), false, false);
        checkMatches(level, crafting, deploying, ModItems.FLUID_SOURCE.toStack(), false, false);
        checkMatches(level, crafting, deploying, charged(Fluids.WATER, 2, 3), false, false);
        var falseFinished = ModItems.FLUID_SOURCE.toStack();
        falseFinished.set(ModDataComponents.FLUID_SOURCE_CHARGE, charged(Fluids.LAVA, 2, 3).get(ModDataComponents.FLUID_SOURCE_CHARGE));
        checkMatches(level, crafting, deploying, falseFinished, false, false);
        var falseWorkpiece = ModItems.INCOMPLETE_FLUID_SOURCE.toStack();
        falseWorkpiece.set(ModDataComponents.FLUID_SOURCE_CHARGE, charged(Fluids.WATER, 3, 3).get(ModDataComponents.FLUID_SOURCE_CHARGE));
        checkMatches(level, crafting, deploying, falseWorkpiece, false, false);
    }

    private static void checkDisplay(Ingredient ingredient, Fluid fluid) {
        var items = ingredient.getItems();
        require(!ingredient.isSimple() && items.length == 1 && ingredient.test(items[0])
            && items[0].get(ModDataComponents.FLUID_SOURCE_CHARGE).fluid().is(fluid), "Missing bound ingredient display for " + fluid);
    }

    private static void checkMatches(Level level, MechanicalCraftingRecipe crafting, DeployerApplicationRecipe deploying,
                                     ItemStack stack, boolean matchesWater, boolean matchesLava) {
        var items = new GroupedItems(stack);
        items.calcStats();
        var grid = MechanicalCraftingInput.of(items);
        require(crafting.matches(grid, level) == matchesWater, "Wrong water crafting match for " + stack);
        var slots = new ItemStackHandler(2);
        slots.setStackInSlot(0, new ItemStack(Items.PAPER));
        slots.setStackInSlot(1, stack);
        require(deploying.matches(new RecipeWrapper(slots), level) == matchesLava, "Wrong lava deploying match for " + stack);
    }

    private static ItemStack charged(Fluid fluid, int fills, int target) {
        return FluidSourceCharge.of(new FluidStack(fluid, 1000), fills, target).toItem();
    }

    static void codecRecovery(GameTestHelper h) {
        var level = h.getLevel();
        var registries = level.registryAccess();
        var pos = new BlockPos(2, 2, 2);
        SourceTests.placeItem(h, pos, charged(Fluids.WATER, 3, 3));
        var source = (FluidSourceEntity) level.getBlockEntity(h.absolutePos(pos));
        source.getPersistentData().putInt("testOtherField", 73);
        var healthy = source.saveCustomOnly(registries);
        var corrupt = healthy.copy();
        corrupt.putString("Charge", "unreadable test charge");
        source.loadCustomOnly(corrupt, registries);
        checkInactive(source);
        require(corrupt.equals(source.saveCustomOnly(registries)) && source.getPersistentData().getInt("testOtherField") == 73,
            "Damaged field or adjacent data did not survive saving");

        var unwritable = new FluidStack(Fluids.LAVA, 1000);
        unwritable.set(UNWRITABLE.get(), "reject");
        var badItem = FluidSourceCharge.of(unwritable, 1, 1).toItem();
        source.applyComponentsFromItemStack(badItem.copy());
        checkInactive(source);
        require(corrupt.equals(source.saveCustomOnly(registries)), "Failed encoding replaced original damaged field");

        source.loadCustomOnly(healthy, registries);
        require(source.outlet().drain(1000, FluidAction.EXECUTE).is(Fluids.WATER), "Healthy field did not load");
        source.applyComponentsFromItemStack(badItem.copy());
        checkInactive(source);
        var recovered = source.saveCustomOnly(registries);
        require(healthy.equals(recovered), "Failed encoding replaced last valid field");
        source.loadCustomOnly(recovered, registries);
        require(source.outlet().drain(1000, FluidAction.EXECUTE).is(Fluids.WATER), "Last valid field did not recover");

        var fresh = new FluidSourceEntity(BlockPos.ZERO, ModBlocks.FLUID_SOURCE.get().defaultBlockState());
        fresh.getPersistentData().putInt("testOtherField", 19);
        fresh.applyComponentsFromItemStack(badItem.copy());
        var firstSave = fresh.saveCustomOnly(registries);
        require(!firstSave.contains("Charge") && fresh.getPersistentData().getInt("testOtherField") == 19
            && !firstSave.isEmpty(), "First write failure discarded other data");
        fresh.setLevel(level);
        checkInactive(fresh);

        // A readable but unfinished field and a codec failure must both keep the exact original NBT.
        var incomplete = healthy.copy();
        incomplete.getCompound("Charge").putInt("fills", 1);
        source.loadCustomOnly(incomplete, registries);
        checkInactive(source);
        require(incomplete.equals(source.saveCustomOnly(registries)), "Incomplete raw data changed on save");
        h.succeed();
    }

    private static void checkInactive(FluidSourceEntity source) {
        var update = source.getUpdateTag(source.getLevel().registryAccess());
        require(source.charge() == null && source.outlet().drain(1000, FluidAction.SIMULATE).isEmpty()
            && source.outlet().drain(1000, FluidAction.EXECUTE).isEmpty()
            && !update.contains("Charge") && !update.isEmpty()
            && !source.collectComponents().has(ModDataComponents.FLUID_SOURCE_CHARGE.get()), "Inactive state leaked into output, drops or sync");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
