package dev.scathiard.compactfacilities.test;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.goggles.GoggleOverlayRenderer;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceCharge;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceEntity;
import dev.scathiard.compactfacilities.registry.ModBlocks;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@EventBusSubscriber(modid = "compact_facilities_test", value = Dist.CLIENT)
public final class ClientSmoke {
    private static final String LANGUAGE = System.getProperty("compact_facilities.smokeLanguage", "zh_cn");
    private static final String UPGRADE_WORLD = System.getProperty("compact_facilities.upgradeWorld", "");
    private static final String WORLD = UPGRADE_WORLD.isEmpty() ? "compact-facilities-" + System.currentTimeMillis() : UPGRADE_WORLD;
    private static final String PHOTO = WORLD + ".png";
    private static final String INVENTORY = WORLD + "-inventory.png";
    private static final String GOGGLES = WORLD + "-goggles.png";
    private static final BlockPos EMPTY = new BlockPos(-2, 81, 0);
    private static final BlockPos HALF = EMPTY.east(2);
    private static final BlockPos LAVA = HALF.east(2);
    private static final BlockPos WATER = LAVA.east(2);
    private static final BlockPos DAMAGED = WATER.south(2);
    private static final String DAMAGED_FIELD = "unreadable test charge";
    private static int phase, elapsed;
    private static CompletableFuture<?> work;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("compact_facilities.clientSmoke"))
            event.enqueueWork(() -> NeoForge.EVENT_BUS.addListener(ClientSmoke::tick));
    }

    private static void next() {
        phase++;
        elapsed = 0;
        LogUtils.getLogger().info("Compact Facilities client check entered phase {}", phase);
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (++elapsed == 400) {
            var level = mc.level;
            LogUtils.getLogger().info("Waiting in phase {} screen={} player={} lava={} water={} depot={}", phase, mc.screen,
                mc.player == null ? null : mc.player.position(), level == null ? null : level.getBlockEntity(LAVA),
                level == null ? null : level.getBlockEntity(WATER), level == null ? null : level.getBlockEntity(HALF));
            if (level != null) {
                for (BlockPos pos : new BlockPos[]{LAVA, WATER, HALF}) {
                    var be = level.getBlockEntity(pos);
                    if (be != null) LogUtils.getLogger().info("Fixture {} client NBT {}", pos, be.saveWithoutMetadata(level.registryAccess()));
                }
            }
        }
        if (elapsed > 1500) throw new IllegalStateException("Compact Facilities client check timed out in phase " + phase);
        if (work != null) {
            if (!work.isDone()) return;
            work.join();
            work = null;
        }
        switch (phase) {
            case 0 -> {
                if (mc.screen == null || mc.getOverlay() != null) return;
                mc.options.pauseOnLostFocus = false;
                if (!mc.getLanguageManager().getSelected().equals(LANGUAGE)) {
                    mc.options.languageCode = LANGUAGE;
                    mc.getLanguageManager().setSelected(LANGUAGE);
                    work = mc.reloadResourcePacks();
                    return;
                }
                mc.options.fov().set(48);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                next();
                if (!UPGRADE_WORLD.isEmpty()) {
                    reopen(mc);
                    return;
                }
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(7319L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            case 1 -> {
                if (!inWorld(mc)) return;
                work = onServer(mc, UPGRADE_WORLD.isEmpty() ? ClientSmoke::prepare : ClientSmoke::upgrade);
                next();
            }
            case 2 -> {
                if (elapsed < 40 || !synced(mc) || !recipeViewerReady()) return;
                checkModels(mc);
                FluidSourcePresentationChecks.check(mc, LANGUAGE, WATER, LAVA);
                if (Boolean.getBoolean("compact_facilities.verifyJei")) JeiPresentationCheck.check(mc);
                next();
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
            }
            case 3 -> {
                if (mc.level != null || mc.getSingleplayerServer() != null || elapsed < 10) return;
                next();
                reopen(mc);
            }
            case 4 -> {
                if (!inWorld(mc) || !synced(mc) || elapsed < 30) return;
                checkModels(mc);
                work = onServer(mc, player -> {
                    for (Direction face : Direction.values()) {
                        var port = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, WATER, face);
                        require(port != null && port.drain(1000, IFluidHandler.FluidAction.EXECUTE).is(Fluids.WATER), "Saved water source lost an outlet");
                    }
                    var item = player.getInventory().getItem(1).get(ModDataComponents.FLUID_SOURCE_CHARGE);
                    require(item != null && item.fills() == 5000 && item.target() == 10000, "Player inventory lost saved progress");
                    var waterPart = player.getInventory().getItem(4).get(ModDataComponents.FLUID_SOURCE_CHARGE);
                    require(waterPart != null && waterPart.fluid().getFluid() == Fluids.WATER && waterPart.fills() == 2
                        && waterPart.target() == 3, "Saved water workpiece lost its 2/3 progress");
                    if (!UPGRADE_WORLD.isEmpty()) {
                        checkOldTarget(player.getInventory().getItem(6), 2);
                        checkOldTarget(player.getInventory().getItem(7), 10000);
                    }
                });
                mc.options.hideGui = true;
                next();
            }
            case 5 -> {
                if (elapsed < 40) return;
                next();
                Screenshot.grab(mc.gameDirectory, PHOTO, mc.getMainRenderTarget(),
                    message -> LogUtils.getLogger().info("Compact Facilities gallery: {}", message.getString()));
            }
            case 6 -> {
                if (elapsed < 20 || !Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(PHOTO))) return;
                mc.options.hideGui = false;
                work = onServer(mc, player -> player.setGameMode(GameType.SURVIVAL));
                next();
            }
            case 7 -> {
                if (elapsed < 10 || mc.gameMode.getPlayerMode() != GameType.SURVIVAL) return;
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.setScreen(new InventoryScreen(mc.player) {
                    @Override
                    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
                        var waterSlot = getMenu().getSlot(39);
                        super.render(graphics, getGuiLeft() + waterSlot.x + 8, getGuiTop() + waterSlot.y + 8, tick);
                    }
                });
                next();
            }
            case 8 -> {
                if (!(mc.screen instanceof InventoryScreen) || elapsed < 15) return;
                Screenshot.grab(mc.gameDirectory, INVENTORY, mc.getMainRenderTarget(),
                    message -> LogUtils.getLogger().info("Compact Facilities inventory: {}", message.getString()));
                next();
            }
            case 9 -> {
                if (elapsed < 20 || !Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(INVENTORY))) return;
                mc.setScreen(null);
                work = onServer(mc, player -> place(player.serverLevel(), DAMAGED, player.getInventory().getItem(3)));
                next();
            }
            case 10 -> {
                if (!(mc.level.getBlockEntity(DAMAGED) instanceof FluidSourceEntity source) || source.charge() == null) return;
                work = onServer(mc, player -> {
                    var level = player.serverLevel();
                    var be = (FluidSourceEntity) level.getBlockEntity(DAMAGED);
                    be.getPersistentData().putInt("testOtherField", 73);
                    var tag = be.saveCustomOnly(level.registryAccess());
                    tag.putString("Charge", DAMAGED_FIELD);
                    be.loadCustomOnly(tag, level.registryAccess());
                    require(be.outlet().drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Damaged server source supplied fluid");
                    be.setChanged();
                    level.sendBlockUpdated(DAMAGED, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
                });
                next();
            }
            case 11 -> {
                if (!(mc.level.getBlockEntity(DAMAGED) instanceof FluidSourceEntity source) || source.charge() != null) return;
                next();
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
            }
            case 12 -> {
                if (mc.level != null || mc.getSingleplayerServer() != null || elapsed < 10) return;
                next();
                reopen(mc);
            }
            case 13 -> {
                if (!inWorld(mc) || !synced(mc) || !(mc.level.getBlockEntity(DAMAGED) instanceof FluidSourceEntity source)) return;
                require(source.charge() == null, "Damaged source rendered an active binding after reload");
                boolean zh = LANGUAGE.equals("zh_cn");
                FluidSourcePresentationChecks.goggles(source, zh ? "流体源状态：" : "Fluid Source:", zh ? "无流体" : "No fluid");
                work = onServer(mc, player -> {
                    var level = player.serverLevel();
                    var be = (FluidSourceEntity) level.getBlockEntity(DAMAGED);
                    require(DAMAGED_FIELD.equals(be.saveCustomOnly(level.registryAccess()).getString("Charge"))
                        && be.getPersistentData().getInt("testOtherField") == 73
                        && be.outlet().drain(1000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Corrupt save round trip failed");
                });
                next();
            }
            case 14 -> {
                work = onServer(mc, player -> {
                    player.setItemSlot(EquipmentSlot.HEAD, AllItems.GOGGLES.asStack());
                    player.teleportTo(player.serverLevel(), WATER.getX() + 3.5, WATER.getY(), WATER.getZ() + 0.5, 90, 21);
                });
                next();
            }
            case 15 -> {
                if (!GogglesItem.isWearingGoggles(mc.player) || !WATER.equals(GoggleOverlayRenderer.lastHovered)
                    || GoggleOverlayRenderer.hoverTicks < 30) return;
                Screenshot.grab(mc.gameDirectory, GOGGLES, mc.getMainRenderTarget(),
                    message -> LogUtils.getLogger().info("Compact Facilities goggles: {}", message.getString()));
                next();
            }
            case 16 -> {
                if (!Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(GOGGLES))) return;
                LogUtils.getLogger().info("COMPACT_FACILITIES_CLIENT_PASSED saved-world={} upgraded={} language={} jei={} goggles={} gallery={} inventory={}",
                    WORLD, !UPGRADE_WORLD.isEmpty(), LANGUAGE, Boolean.getBoolean("compact_facilities.verifyJei"), GOGGLES, PHOTO, INVENTORY);
                next();
                mc.stop();
            }
        }
    }

    private static boolean recipeViewerReady() {
        if (!Boolean.getBoolean("compact_facilities.verifyJei")) return true;
        require(ModList.get().isLoaded("jei"), "Optional JEI verification requested without JEI");
        return JeiPresentationCheck.ready();
    }

    private static void reopen(Minecraft mc) {
        mc.createWorldOpenFlows().openWorld(WORLD, () -> { throw new IllegalStateException("Failed to open saved world " + WORLD); });
    }

    private static boolean inWorld(Minecraft mc) {
        return mc.player != null && mc.screen == null && mc.getSingleplayerServer() != null && mc.player.tickCount >= 20;
    }

    private static CompletableFuture<Void> onServer(Minecraft mc, Consumer<ServerPlayer> action) {
        var server = mc.getSingleplayerServer();
        var id = mc.player.getUUID();
        return server.submit(() -> action.accept(server.getPlayerList().getPlayer(id)));
    }

    private static ItemStack spoutWater(ServerLevel level, ItemStack input) {
        var bucket = new FluidStack(Fluids.WATER, 1000);
        var output = FillingBySpout.fillItem(level, 1000, input, bucket);
        require(!output.isEmpty() && bucket.isEmpty(), "Water fill failed");
        return output;
    }

    private static void checkOldTarget(ItemStack stack, int fills) {
        var charge = stack.get(ModDataComponents.FLUID_SOURCE_CHARGE);
        require(charge != null && charge.fluid().is(Fluids.WATER) && charge.target() == 10000 && charge.fills() == fills
            && stack.is((fills == 10000 ? ModItems.FLUID_SOURCE : ModItems.INCOMPLETE_FLUID_SOURCE).get()), "Lost old water target " + fills);
    }

    private static void upgrade(ServerPlayer player) {
        var level = player.serverLevel();
        var inventory = player.getInventory();
        checkOldTarget(inventory.getItem(6), 1);
        checkOldTarget(inventory.getItem(7), 9999);
        for (int slot : new int[]{6, 7}) {
            inventory.setItem(slot, spoutWater(level, inventory.getItem(slot)));
        }
        checkOldTarget(inventory.getItem(6), 2);
        checkOldTarget(inventory.getItem(7), 10000);

        var old = (FluidSourceEntity) level.getBlockEntity(WATER);
        require(old != null && old.charge() != null && old.charge().target() == 3, "Missing old placed water source");
        player.setGameMode(GameType.SURVIVAL);
        require(player.gameMode.destroyBlock(WATER), "Could not mine the old source in survival");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(WATER), e -> e.getItem().is(ModItems.FLUID_SOURCE.get()));
        require(drops.size() == 1 && old.charge().equals(drops.getFirst().getItem().get(ModDataComponents.FLUID_SOURCE_CHARGE)),
            "Mining the old source lost its original component");
        var hand = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, drops.getFirst().getItem().copy());
        drops.getFirst().discard();
        var hit = new BlockHitResult(Vec3.atCenterOf(WATER.below()), Direction.UP, WATER.below(), false);
        require(player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(),
            "Could not place the old source drop");
        player.setItemInHand(InteractionHand.MAIN_HAND, hand);
        inventory.setChanged();
        player.containerMenu.broadcastFullState();
    }

    private static void prepare(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(6000);
        for (int x = -5; x <= 8; x++)
            for (int z = -3; z <= 9; z++)
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState());
        for (int y = 81; y <= 82; y++)
            level.setBlockAndUpdate(new BlockPos(4, y, 6), Blocks.SMOOTH_STONE.defaultBlockState());
        var blank = ModItems.FLUID_SOURCE_BLANK.toStack();
        var half = FluidSourceCharge.of(new FluidStack(Fluids.LAVA, 1000), 5000, 10000).toItem();
        var lava = FluidSourceCharge.of(new FluidStack(Fluids.LAVA, 1000), 10000, 10000).toItem();
        var firstWaterFill = spoutWater(level, blank.copy());
        var secondWaterFill = spoutWater(level, firstWaterFill);
        var water = spoutWater(level, secondWaterFill.copy());
        displayOnDepot(level, EMPTY, blank);
        displayOnDepot(level, HALF, half);
        place(level, LAVA, lava);
        place(level, WATER, water);
        player.getInventory().setItem(0, blank);
        player.getInventory().setItem(1, half);
        player.getInventory().setItem(2, lava);
        player.getInventory().setItem(3, water);
        player.getInventory().setItem(4, secondWaterFill);
        // Keep a saved start and final-bucket fixture for the next same-namespace upgrade check.
        player.getInventory().setItem(6, FluidSourceCharge.of(new FluidStack(Fluids.WATER, 1000), 1, 10000).toItem());
        player.getInventory().setItem(7, FluidSourceCharge.of(new FluidStack(Fluids.WATER, 1000), 9999, 10000).toItem());
        player.getInventory().selected = 5;
        player.containerMenu.broadcastFullState();
        player.teleportTo(level, 4.2, 83, 6.7, 156f, 24f);
    }

    private static void displayOnDepot(ServerLevel level, BlockPos pos, ItemStack stack) {
        level.setBlockAndUpdate(pos, AllBlocks.DEPOT.getDefaultState());
        var depot = (DepotBlockEntity) level.getBlockEntity(pos);
        depot.setHeldItem(stack.copy());
        depot.notifyUpdate();
    }

    private static void place(ServerLevel level, BlockPos pos, ItemStack item) {
        level.setBlockAndUpdate(pos, ModBlocks.FLUID_SOURCE.get().defaultBlockState());
        level.getBlockEntity(pos).applyComponentsFromItemStack(item);
    }

    private static boolean synced(Minecraft mc) {
        if (mc.level == null || !(mc.level.getBlockEntity(LAVA) instanceof FluidSourceEntity lava)
                || !(mc.level.getBlockEntity(WATER) instanceof FluidSourceEntity water)
                || !(mc.level.getBlockEntity(HALF) instanceof DepotBlockEntity depot)) return false;
        FluidSourceCharge half = depot.getHeldItem().get(ModDataComponents.FLUID_SOURCE_CHARGE);
        return lava.charge() != null && lava.charge().fluid().getFluid() == Fluids.LAVA
            && water.charge() != null && water.charge().fluid().getFluid() == Fluids.WATER && water.charge().complete() && water.charge().target() == 3
            && half != null && half.fills() == 5000 && half.target() == 10000;
    }

    private static void checkModels(Minecraft mc) {
        FluidSourceArchitectureTests.checkRecipes(mc.level, mc.level.getRecipeManager());
        for (int slot = 1; slot <= 4; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            require(mc.getItemRenderer().getModel(stack, mc.level, mc.player, 0).isCustomRenderer(),
                "Missing fluid item renderer in slot " + slot);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
