package dev.scathiard.compactfacilities.test;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.fluids.pump.PumpBlock;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.FluidManipulationBehaviour.BottomlessFluidMode;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.config.ServerConfig;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceCharge;
import dev.scathiard.compactfacilities.registry.ModBlocks;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Mod("compact_facilities_test")
@GameTestHolder("compact_facilities_test")
public final class SourceTests {
    public SourceTests(IEventBus bus) {
        FluidSourceArchitectureTests.register(bus);
    }

    @GameTestGenerator
    public static List<TestFunction> tests() {
        var suite = new ArrayList<>(List.of(test("default", "craft_and_ten_thousand", SourceTests::fullCost),
            test("default", "three_water_buckets_on_depot", SourceTests::waterOnDepot),
            test("default", "six_outlets_and_survival_drops", SourceTests::outlets),
            test("default", "depot_transfer_and_belt_spout", SourceTests::transport),
            test("default", "continuous_depot_and_last_bucket", SourceTests::continuousDepot),
            test("default", "powered_pump_to_tank", SourceTests::pump),
            test("default", "bound_source_recipes", FluidSourceArchitectureTests::recipeSync),
            test("default", "charge_codec_recovery", FluidSourceArchitectureTests::codecRecovery),
            test("settings", "pack_rules_and_continuation", SourceTests::settings)));
        if (Boolean.getBoolean("compact_facilities.fullCostTest")) {
            suite.add(new TestFunction("production", "compact_facilities_test.ten_thousand_depot_operations", "compact_facilities_test:empty",
                350000, 0, true, SourceTests::fullDepot));
        }
        return suite;
    }

    private static TestFunction test(String batch, String name, Consumer<GameTestHelper> run) {
        return new TestFunction(batch, "compact_facilities_test." + name, "compact_facilities_test:empty", 300, 0, true, run);
    }

    private static ItemStack process(GameTestHelper h, ItemStack input, Fluid fluid) {
        FluidStack dose = new FluidStack(fluid, 1000);
        int n = FillingBySpout.getRequiredAmountForItem(h.getLevel(), input, dose);
        h.assertTrue(n == 1000 && FillingBySpout.canItemBeFilled(h.getLevel(), input), "Spout must quote exactly one bucket");
        ItemStack output = FillingBySpout.fillItem(h.getLevel(), n, input, dose);
        h.assertTrue(dose.isEmpty() && !output.isEmpty(), "Successful fill must debit its bucket and produce one item");
        return output;
    }

    private static ItemStack roundTrip(GameTestHelper h, ItemStack item) {
        return ItemStack.parse(h.getLevel().registryAccess(), item.save(h.getLevel().registryAccess())).orElseThrow();
    }

    private static void fullCost(GameTestHelper h) {
        var level = h.getLevel();
        var file = ModList.get().getModFileById(CompactFacilities.ID).getFile().getFilePath().toString();
        if (Boolean.getBoolean("compact_facilities.verifyJar")) h.assertTrue(file.endsWith(".jar"), "Expected installed JAR, found " + file);
        var craft = CraftingInput.of(2, 1, List.of(AllBlocks.FLUID_TANK.asStack(), AllBlocks.HOSE_PULLEY.asStack()));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, craft, level).orElseThrow();
        ItemStack blank = recipe.value().assemble(craft, level.registryAccess());
        h.assertTrue(blank.is(ModItems.FLUID_SOURCE_BLANK.get()), "Survival recipe must make a blank");
        blank.setCount(2);
        ItemStack part = process(h, blank, Fluids.LAVA);
        h.assertTrue(blank.getCount() == 1 && blank.get(ModDataComponents.FLUID_SOURCE_CHARGE) == null, "Each cycle consumes only one workpiece");
        h.assertTrue(part.get(ModDataComponents.FLUID_SOURCE_CHARGE).target() == 10000, "Default Create threshold should be 10000");
        for (int i = 2; i <= 9999; i++) part = process(h, part, Fluids.LAVA);
        part = roundTrip(h, part);
        h.assertTrue(part.is(ModItems.INCOMPLETE_FLUID_SOURCE.get()) && part.get(ModDataComponents.FLUID_SOURCE_CHARGE).fills() == 9999, "Progress or binding lost");
        var wrong = new FluidStack(Fluids.WATER, 1000);
        h.assertTrue(FillingBySpout.getRequiredAmountForItem(level, part, wrong) == -1
            && FillingBySpout.fillItem(level, 1000, part, wrong).isEmpty() && wrong.getAmount() == 1000, "Wrong fluid must be untouched");
        var partial = new FluidStack(Fluids.LAVA, 999);
        h.assertTrue(FillingBySpout.fillItem(level, 1000, part, partial).isEmpty() && partial.getAmount() == 999, "Incomplete dose committed");
        var source = process(h, part, Fluids.LAVA);
        h.assertTrue(source.is(ModItems.FLUID_SOURCE.get()) && source.get(ModDataComponents.FLUID_SOURCE_CHARGE).complete(), "Final result lost");
        h.assertTrue(!FillingBySpout.canItemBeFilled(level, source), "Final source should not accept spouting");
        h.assertTrue(process(h, new ItemStack(Items.BUCKET), Fluids.LAVA).is(Items.LAVA_BUCKET), "Ordinary bucket regression");
        h.succeed();
    }

    private static void settings(GameTestHelper h) {
        var config = AllConfigs.server().fluids;
        var mode = config.bottomlessFluidMode.get();
        int threshold = config.hosePulleyBlockThreshold.get();
        int override = ServerConfig.REQUIRED_FILLS.get();
        try {
            var blank = new ItemStack(ModItems.FLUID_SOURCE_BLANK.get());
            Fluid honeyFluid = AllFluids.HONEY.getSource();
            var honey = new FluidStack(honeyFluid, 1000);
            h.assertTrue(FillingBySpout.getRequiredAmountForItem(h.getLevel(), blank, honey) == -1, "Default allow list leaked honey");
            config.bottomlessFluidMode.set(BottomlessFluidMode.ALLOW_ALL);
            config.hosePulleyBlockThreshold.set(3);
            ItemStack first = process(h, blank.copy(), honey.getFluid());
            h.assertTrue(first.get(ModDataComponents.FLUID_SOURCE_CHARGE).target() == 3, "Did not follow Create's threshold");
            ServerConfig.REQUIRED_FILLS.set(1);
            h.assertTrue(process(h, blank.copy(), Fluids.LAVA).is(ModItems.FLUID_SOURCE.get()), "Single-operation lava override failed");
            config.hosePulleyBlockThreshold.set(20000);
            var waterPart = process(h, blank.copy(), Fluids.WATER);
            h.assertTrue(waterPart.is(ModItems.INCOMPLETE_FLUID_SOURCE.get()) && waterPart.get(ModDataComponents.FLUID_SOURCE_CHARGE).target() == 3,
                "The general fill override or hose pulley threshold changed the water cost");
            first = process(h, roundTrip(h, first), honey.getFluid());
            h.assertTrue(first.get(ModDataComponents.FLUID_SOURCE_CHARGE).target() == 3 && first.get(ModDataComponents.FLUID_SOURCE_CHARGE).fills() == 2, "Existing contract changed");
            var completed = process(h, first, honey.getFluid());
            h.assertTrue(completed.is(ModItems.FLUID_SOURCE.get()), "Honey completion failed");
            BlockPos pos = new BlockPos(2, 2, 2);
            placeItem(h, pos, completed);
            var outlet = fluidAt(h, pos, Direction.NORTH);
            h.assertTrue(outlet.drain(1000, IFluidHandler.FluidAction.EXECUTE).is(honeyFluid), "Allowed honey source cannot output honey");
            Fluid potionFluid = AllFluids.POTION.getSource();
            h.assertTrue(FillingBySpout.getRequiredAmountForItem(h.getLevel(), blank, new FluidStack(potionFluid, 1000)) < 0, "Virtual fluid accepted");
            config.bottomlessFluidMode.set(BottomlessFluidMode.DENY_ALL);
            h.assertTrue(FillingBySpout.getRequiredAmountForItem(h.getLevel(), blank, honey) == -1, "Global deny ignored");
            var water = new FluidStack(Fluids.WATER, 1000);
            h.assertTrue(FillingBySpout.getRequiredAmountForItem(h.getLevel(), blank, water) == -1
                && FillingBySpout.fillItem(h.getLevel(), 1000, waterPart, water).isEmpty() && water.getAmount() == 1000
                && waterPart.get(ModDataComponents.FLUID_SOURCE_CHARGE).fills() == 1, "Water exception bypassed the fluid eligibility rules");
            h.assertTrue(outlet.drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "A banned fluid kept flowing");
            config.bottomlessFluidMode.set(BottomlessFluidMode.ALLOW_ALL);
            h.assertTrue(outlet.drain(1000, IFluidHandler.FluidAction.EXECUTE).is(honeyFluid), "Restoring eligibility lost the source binding");
            config.hosePulleyBlockThreshold.set(-1);
            h.assertTrue(FillingBySpout.getRequiredAmountForItem(h.getLevel(), blank, honey) == -1, "Disabled bottomless mode bypassed by override");
            h.assertTrue(FillingBySpout.getRequiredAmountForItem(h.getLevel(), blank, water) == -1, "Water bypassed disabled bottomless mode");
        } finally {
            config.bottomlessFluidMode.set(mode);
            config.hosePulleyBlockThreshold.set(threshold);
            ServerConfig.REQUIRED_FILLS.set(override);
        }
        h.succeed();
    }

    static void placeItem(GameTestHelper h, BlockPos pos, ItemStack stack) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var level = h.getLevel();
        var base = h.absolutePos(pos.below());
        h.setBlock(pos.below(), Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atCenterOf(base).add(0, .5, 0), Direction.UP, base, false);
        var result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        h.assertTrue(result.consumesAction() && level.getBlockState(h.absolutePos(pos)).is(ModBlocks.FLUID_SOURCE.get()), "Block item did not place");
    }

    private static IFluidHandler fluidAt(GameTestHelper h, BlockPos pos, Direction side) {
        var handler = h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(pos), side);
        h.assertTrue(handler != null, "Missing fluid port at " + pos + " on " + side);
        return handler;
    }

    private static void outlets(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        placeItem(h, pos, FluidSourceCharge.of(new FluidStack(Fluids.LAVA, 1000), 10000, 10000).toItem());
        for (Direction side : Direction.values()) {
            var port = fluidAt(h, pos, side);
            h.assertTrue(port.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "Input accepted");
            h.assertTrue(port.drain(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Wrong drain");
            for (var action : IFluidHandler.FluidAction.values()) {
                var drained = port.drain(12_000_000, action);
                h.assertTrue(drained.getFluid() == Fluids.LAVA && drained.getAmount() == 12_000_000, "Limited output on " + side);
            }
        }
        var level = h.getLevel();
        var absolute = h.absolutePos(pos);
        var entity = level.getBlockEntity(absolute);
        var snapshot = entity.saveWithFullMetadata(level.registryAccess());
        var state = level.getBlockState(absolute);
        var dropped = Block.getDrops(state, level, absolute, entity);
        h.assertTrue(dropped.size() == 1 && dropped.getFirst().get(ModDataComponents.FLUID_SOURCE_CHARGE).complete(), "Survival loot lost binding");
        h.setBlock(pos, Blocks.AIR);
        var loaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(absolute, state, snapshot, level.registryAccess());
        h.setBlock(pos, state);
        level.setBlockEntity(loaded);
        h.assertTrue(fluidAt(h, pos, Direction.DOWN).drain(1000, IFluidHandler.FluidAction.EXECUTE).is(Fluids.LAVA), "World NBT lost fluid");
        placeItem(h, pos.east(2), roundTrip(h, dropped.getFirst()));
        h.assertTrue(fluidAt(h, pos.east(2), Direction.UP).drain(1000, IFluidHandler.FluidAction.EXECUTE).is(Fluids.LAVA), "Replaced drop lost fluid");
        h.succeed();
    }

    private static void transport(GameTestHelper h) {
        BlockPos depot = new BlockPos(2, 2, 1);
        BlockPos beltStart = new BlockPos(1, 2, 4);
        BlockPos beltWork = beltStart.east(2);
        BlockPos beltEnd = beltStart.east(4);
        h.setBlock(depot, AllBlocks.DEPOT.get());
        h.setBlock(depot.above(2), AllBlocks.SPOUT.get());
        h.setBlock(beltStart, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
        h.setBlock(beltEnd, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.Z));
        BeltConnectorItem.createBelts(h.getLevel(), h.absolutePos(beltStart), h.absolutePos(beltEnd));
        h.setBlock(beltStart.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        h.setBlock(beltWork.above(2), AllBlocks.SPOUT.get());
        var first = fluidAt(h, depot.above(2), Direction.UP);
        var second = fluidAt(h, beltWork.above(2), Direction.UP);
        var depotEntity = (DepotBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(depot));
        depotEntity.setHeldItem(new ItemStack(ModItems.FLUID_SOURCE_BLANK.get()));
        first.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        h.runAtTickTime(48, () -> {
            var inventory = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(depot), Direction.UP);
            ItemStack part = extract(inventory, ModItems.INCOMPLETE_FLUID_SOURCE.get());
            h.assertTrue(!part.isEmpty() && part.get(ModDataComponents.FLUID_SOURCE_CHARGE).fills() == 1 && first.getFluidInTank(0).isEmpty(), "Depot spout did not perform exactly one operation");
            var belt = h.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(beltWork), Direction.UP);
            second.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
            h.assertTrue(belt != null && belt.insertItem(0, roundTrip(h, part), false).isEmpty(), "Could not insert returned part on belt");
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 48 && second.getFluidInTank(0).isEmpty(), "Belt has not consumed one bucket");
            var be = (com.simibubi.create.content.kinetics.belt.BeltBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(beltWork));
            boolean found = be.getInventory().getTransportedItems().stream().anyMatch(t -> t.stack.get(ModDataComponents.FLUID_SOURCE_CHARGE) != null
                && t.stack.get(ModDataComponents.FLUID_SOURCE_CHARGE).fills() == 2);
            h.assertTrue(found, "Belt did not resume the depot workpiece");
        });
    }

    private static ItemStack extract(IItemHandler handler, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < handler.getSlots(); slot++)
            if (handler.getStackInSlot(slot).is(item)) return handler.extractItem(slot, 1, false);
        return ItemStack.EMPTY;
    }

    private static void fullDepot(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, AllBlocks.DEPOT.get());
        h.setBlock(pos.above(2), AllBlocks.SPOUT.get());
        var depot = (DepotBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        var port = fluidAt(h, pos.above(2), Direction.UP);
        depot.setHeldItem(ModItems.FLUID_SOURCE_BLANK.toStack());
        var bucket = new FluidStack(Fluids.LAVA, 1000);
        int[] supplied = {0};
        int[] milestone = {2500};
        h.succeedWhen(() -> {
            if (supplied[0] < 10_000_000) supplied[0] += port.fill(bucket, IFluidHandler.FluidAction.EXECUTE);
            var held = depot.getHeldItem();
            FluidSourceCharge charge = held.get(ModDataComponents.FLUID_SOURCE_CHARGE);
            if (charge != null && charge.fills() >= milestone[0]) {
                LogUtils.getLogger().info("COMPACT_FACILITIES_LINE fills={} tick={} supplied_mB={}", charge.fills(), h.getTick(), supplied[0]);
                milestone[0] += 2500;
            }
            h.assertTrue(held.is(ModItems.FLUID_SOURCE.get()), "The production workpiece is still under the spout");
            h.assertTrue(held.getCount() == 1 && charge != null && charge.complete() && charge.target() == 10000
                    && charge.fluid().getFluid() == Fluids.LAVA && supplied[0] == 10_000_000 && port.getFluidInTank(0).isEmpty(),
                "Physical production did not account for exactly ten thousand buckets");
        });
    }

    private static void continuousDepot(GameTestHelper h) {
        BlockPos pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, AllBlocks.DEPOT.get());
        h.setBlock(pos.above(2), AllBlocks.SPOUT.get());
        var depot = (DepotBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        var port = fluidAt(h, pos.above(2), Direction.UP);
        depot.setHeldItem(roundTrip(h, FluidSourceCharge.of(new FluidStack(Fluids.WATER, 1000), 9997, 10000).toItem()));
        port.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE);
        h.runAtTickTime(40, () -> {
            h.assertTrue(depot.getHeldItem().get(ModDataComponents.FLUID_SOURCE_CHARGE).fills() == 9997
                && port.getFluidInTank(0).getAmount() == 1000, "Mismatched fluid changed the held workpiece or the spout");
            port.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        });
        // Simulate a continuously supplied inlet. The spout itself does every operation on the same depot.
        int[] supplied = {0};
        h.onEachTick(() -> {
            if (h.getTick() > 40 && supplied[0] < 4000)
                supplied[0] += port.fill(new FluidStack(Fluids.WATER, 4000 - supplied[0]), IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAtTickTime(200, () -> {
            var item = depot.getHeldItem();
            h.assertTrue(item.is(ModItems.FLUID_SOURCE.get()) && item.getCount() == 1
                && item.get(ModDataComponents.FLUID_SOURCE_CHARGE).complete() && item.get(ModDataComponents.FLUID_SOURCE_CHARGE).target() == 10000,
                "Stationary spout failed to finish the saved workpiece at its original target");
            h.assertTrue(supplied[0] == 4000 && port.getFluidInTank(0).getAmount() == 1000, "Final source consumed the spare bucket");
            h.succeed();
        });
    }

    private static void waterOnDepot(GameTestHelper h) {
        var pos = new BlockPos(2, 2, 2);
        h.setBlock(pos, AllBlocks.DEPOT.get());
        h.setBlock(pos.above(2), AllBlocks.SPOUT.get());
        var depot = (DepotBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(pos));
        var port = fluidAt(h, pos.above(2), Direction.UP);
        depot.setHeldItem(ModItems.FLUID_SOURCE_BLANK.toStack());
        port.fill(new FluidStack(Fluids.WATER, 999), IFluidHandler.FluidAction.EXECUTE);
        h.runAtTickTime(30, () -> {
            h.assertTrue(depot.getHeldItem().is(ModItems.FLUID_SOURCE_BLANK.get()) && port.getFluidInTank(0).getAmount() == 999,
                "A partial bucket started the water source");
            h.assertTrue(port.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.EXECUTE) == 1, "Could not top up the first bucket");
        });
        for (int n = 1; n <= 3; n++) {
            int fills = n;
            h.runAtTickTime(30 + 40L * fills, () -> {
                var held = depot.getHeldItem();
                var charge = held.get(ModDataComponents.FLUID_SOURCE_CHARGE);
                h.assertTrue(held.is(fills == 3 ? ModItems.FLUID_SOURCE.get() : ModItems.INCOMPLETE_FLUID_SOURCE.get())
                    && held.getCount() == 1 && charge != null && charge.fills() == fills && charge.target() == 3
                    && charge.fluid().getFluid() == Fluids.WATER && port.getFluidInTank(0).isEmpty(), "Incorrect water fill " + fills);
                if (fills == 2) depot.setHeldItem(roundTrip(h, held));
                h.assertTrue(port.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 1000, "Spout refill failed");
            });
        }
        h.runAtTickTime(200, () -> {
            int remaining = port.getFluidInTank(0).getAmount();
            h.assertTrue(depot.getHeldItem().is(ModItems.FLUID_SOURCE.get()) && remaining == 1000, "A finished source took a fourth bucket");
            var output = pos.east(2);
            placeItem(h, output, depot.getHeldItem().copy());
            for (Direction face : Direction.values()) {
                var drained = fluidAt(h, output, face).drain(1000, IFluidHandler.FluidAction.EXECUTE);
                h.assertTrue(drained.is(Fluids.WATER) && drained.getAmount() == 1000, "Missing water output on " + face);
            }
            LogUtils.getLogger().info("COMPACT_FACILITIES_WATER fills=3/3 consumed_mB={} spare_mB={}", 4000 - remaining, remaining);
            h.succeed();
        });
    }

    private static void pump(GameTestHelper h) {
        var pump = new BlockPos(3, 2, 3);
        placeItem(h, pump.west(), FluidSourceCharge.of(new FluidStack(Fluids.WATER, 1000), 1, 1).toItem());
        h.setBlock(pump.east(), AllBlocks.FLUID_TANK.get());
        h.setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState().setValue(PumpBlock.FACING, Direction.EAST));
        h.setBlock(pump.south(), AllBlocks.COGWHEEL.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        h.setBlock(pump.south().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        var target = fluidAt(h, pump.east(), Direction.WEST);
        h.runAtTickTime(100, () -> {
            h.assertTrue(target.getFluidInTank(0).is(Fluids.WATER) && target.getFluidInTank(0).getAmount() > 0, "Powered Create pump failed to move water into tank");
            h.setBlock(pump.south().west(), Blocks.AIR);
        });
        h.runAtTickTime(120, () -> {
            int amount = target.getFluidInTank(0).getAmount();
            target.drain(amount, IFluidHandler.FluidAction.EXECUTE);
        });
        h.runAtTickTime(145, () -> {
            h.assertTrue(target.getFluidInTank(0).isEmpty(), "Stopped pump still transferring water");
            h.succeed();
        });
    }
}
