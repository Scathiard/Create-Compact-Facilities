package dev.scathiard.cannedfacilities.test;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import dev.scathiard.cannedfacilities.Charge;
import dev.scathiard.cannedfacilities.FluidSourceEntity;
import dev.scathiard.cannedfacilities.CannedFacilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
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

@EventBusSubscriber(modid = "canned_facilities_test", value = Dist.CLIENT)
public final class ClientSmoke {
    private static final String WORLD = "canned-facilities-" + System.currentTimeMillis();
    private static final String PHOTO = WORLD + ".png";
    private static final String INVENTORY = WORLD + "-inventory.png";
    private static final BlockPos EMPTY = new BlockPos(-2, 81, 0);
    private static final BlockPos HALF = EMPTY.east(2);
    private static final BlockPos LAVA = HALF.east(2);
    private static final BlockPos WATER = LAVA.east(2);
    private static int phase, elapsed;
    private static CompletableFuture<?> work;

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("canned_facilities.clientSmoke"))
            event.enqueueWork(() -> NeoForge.EVENT_BUS.addListener(ClientSmoke::tick));
    }

    private static void next() {
        phase++;
        elapsed = 0;
        LogUtils.getLogger().info("Canned Facilities client check entered phase {}", phase);
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
        if (elapsed > 1500) throw new IllegalStateException("Canned Facilities client check timed out in phase " + phase);
        if (work != null) {
            if (!work.isDone()) return;
            work.join();
            work = null;
        }
        switch (phase) {
            case 0 -> {
                if (mc.screen == null || mc.getOverlay() != null) return;
                mc.options.pauseOnLostFocus = false;
                if (!mc.getLanguageManager().getSelected().equals("zh_cn")) {
                    mc.options.languageCode = "zh_cn";
                    mc.getLanguageManager().setSelected("zh_cn");
                    work = mc.reloadResourcePacks();
                    return;
                }
                mc.options.fov().set(48);
                mc.options.guiScale().set(2);
                mc.resizeDisplay();
                next();
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                    new WorldOptions(7319L, false, false), registry -> registry.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            case 1 -> {
                if (!inWorld(mc)) return;
                work = onServer(mc, ClientSmoke::prepare);
                next();
            }
            case 2 -> {
                if (elapsed < 40 || !synced(mc)) return;
                checkModels(mc);
                next();
                mc.level.disconnect();
                mc.disconnect(new TitleScreen());
            }
            case 3 -> {
                if (mc.level != null || mc.getSingleplayerServer() != null || elapsed < 10) return;
                next();
                mc.createWorldOpenFlows().openWorld(WORLD, () -> { throw new IllegalStateException("Failed to reopen saved world"); });
            }
            case 4 -> {
                if (!inWorld(mc) || !synced(mc) || elapsed < 30) return;
                checkModels(mc);
                work = onServer(mc, player -> {
                    for (Direction face : Direction.values()) {
                        var port = player.serverLevel().getCapability(Capabilities.FluidHandler.BLOCK, WATER, face);
                        require(port != null && port.drain(1000, IFluidHandler.FluidAction.EXECUTE).is(Fluids.WATER), "Saved water source lost an outlet");
                    }
                    var item = player.getInventory().getItem(1).get(CannedFacilities.CHARGE);
                    require(item != null && item.fills() == 5000 && item.target() == 10000, "Player inventory lost saved progress");
                    var waterPart = player.getInventory().getItem(4).get(CannedFacilities.CHARGE);
                    require(waterPart != null && waterPart.fluid().getFluid() == Fluids.WATER && waterPart.fills() == 2
                        && waterPart.target() == 3, "Saved water workpiece lost its 2/3 progress");
                });
                mc.options.hideGui = true;
                next();
            }
            case 5 -> {
                if (elapsed < 40) return;
                next();
                Screenshot.grab(mc.gameDirectory, PHOTO, mc.getMainRenderTarget(),
                    message -> LogUtils.getLogger().info("Canned Facilities gallery: {}", message.getString()));
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
                mc.setScreen(new InventoryScreen(mc.player));
                next();
            }
            case 8 -> {
                if (!(mc.screen instanceof InventoryScreen) || elapsed < 15) return;
                Screenshot.grab(mc.gameDirectory, INVENTORY, mc.getMainRenderTarget(),
                    message -> LogUtils.getLogger().info("Canned Facilities inventory: {}", message.getString()));
                next();
            }
            case 9 -> {
                if (elapsed < 20 || !Files.isRegularFile(mc.gameDirectory.toPath().resolve("screenshots").resolve(INVENTORY))) return;
                LogUtils.getLogger().info("CANNED_FACILITIES_CLIENT_PASSED saved-world={} gallery={} inventory={}", WORLD, PHOTO, INVENTORY);
                next();
                mc.stop();
            }
        }
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

    private static void prepare(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.setDayTime(6000);
        for (int x = -5; x <= 8; x++)
            for (int z = -3; z <= 9; z++)
                level.setBlockAndUpdate(new BlockPos(x, 80, z), Blocks.SMOOTH_STONE.defaultBlockState());
        for (int y = 81; y <= 82; y++)
            level.setBlockAndUpdate(new BlockPos(4, y, 6), Blocks.SMOOTH_STONE.defaultBlockState());
        var blank = CannedFacilities.BLANK.toStack();
        var half = Charge.of(new FluidStack(Fluids.LAVA, 1000), 5000, 10000).toItem();
        var lava = Charge.of(new FluidStack(Fluids.LAVA, 1000), 10000, 10000).toItem();
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
        level.setBlockAndUpdate(pos, CannedFacilities.SOURCE.get().defaultBlockState());
        level.getBlockEntity(pos).applyComponentsFromItemStack(item);
    }

    private static boolean synced(Minecraft mc) {
        if (mc.level == null || !(mc.level.getBlockEntity(LAVA) instanceof FluidSourceEntity lava)
                || !(mc.level.getBlockEntity(WATER) instanceof FluidSourceEntity water)
                || !(mc.level.getBlockEntity(HALF) instanceof DepotBlockEntity depot)) return false;
        Charge half = depot.getHeldItem().get(CannedFacilities.CHARGE);
        return lava.charge() != null && lava.charge().fluid().getFluid() == Fluids.LAVA
            && water.charge() != null && water.charge().fluid().getFluid() == Fluids.WATER && water.charge().complete() && water.charge().target() == 3
            && half != null && half.fills() == 5000 && half.target() == 10000;
    }

    private static void checkModels(Minecraft mc) {
        require(CannedFacilities.BLANK.toStack().getHoverName().getString().equals("罐装流体源毛坯"), "Wrong blank name");
        require(CannedFacilities.UNFINISHED.toStack().getHoverName().getString().equals("未完成的罐装流体源"), "Wrong workpiece name");
        require(CannedFacilities.SOURCE_ITEM.toStack().getHoverName().getString().equals("罐装流体源"), "Wrong source name");
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
