package dev.scathiard.compactfacilities.test;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.scathiard.compactfacilities.CompactFacilities;
import dev.scathiard.compactfacilities.content.fluidsource.FluidSourceEntity;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.fml.ModList;
import net.neoforged.fml.i18n.FMLTranslations;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

final class FluidSourcePresentationChecks {
    static void check(Minecraft mc, String language, BlockPos waterPos, BlockPos lavaPos) {
        boolean zh = language.equals("zh_cn");
        var inventory = mc.player.getInventory();
        String blankName = zh ? "空的紧凑流体源" : "Empty Compact Fluid Source";
        String workpiece = zh ? "未完成的紧凑流体源" : "Incomplete Compact Fluid Source";
        String source = zh ? "紧凑流体源" : "Compact Fluid Source";
        String unbound = zh ? "无流体" : "No fluid";
        String water = zh ? "水" : "Water";
        String lava = zh ? "熔岩" : "Lava";
        String fluidPrefix = zh ? "流体：" : "Fluid: ";
        String filledPrefix = zh ? "已注入" : "Filled with ";

        tooltip(mc, inventory.getItem(0), blankName, zh ? "还是空的，想注入点什么吗？" : "Still empty. Care to pour something in?");
        tooltip(mc, inventory.getItem(1), workpiece, fluidPrefix + lava, "5000 / 10000");
        tooltip(mc, inventory.getItem(4), workpiece, fluidPrefix + water, "2 / 3");
        var lavaLines = tooltip(mc, inventory.getItem(2), source, filledPrefix + lava,
            zh ? "有志者事竟成" : "Where there's a will, there's a way.");
        var waterLines = tooltip(mc, inventory.getItem(3), source, filledPrefix + water,
            zh ? "这也太轻松了吧？" : "That was easy, right?");
        for (var lines : List.of(lavaLines, waterLines)) {
            require(TextColor.fromLegacyFormat(ChatFormatting.GRAY).equals(lines.get(2).getStyle().getColor()), "Missing gray flavor style");
        }
        require(inventory.getItem(3).getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().isEmpty(), "Recipe-only hint present on an ordinary source");
        tooltip(mc, ModItems.INCOMPLETE_FLUID_SOURCE.toStack(), workpiece, unbound);
        tooltip(mc, ModItems.FLUID_SOURCE.toStack(), source, unbound);
        var invalid = ModItems.FLUID_SOURCE.toStack();
        invalid.set(ModDataComponents.FLUID_SOURCE_CHARGE, inventory.getItem(4).get(ModDataComponents.FLUID_SOURCE_CHARGE));
        tooltip(mc, invalid, source, unbound);

        String header = zh ? "流体源状态：" : "Fluid Source:";
        String bottomless = zh ? "无限供应：" : "Bottomless: ";
        var placedWater = (FluidSourceEntity) mc.level.getBlockEntity(waterPos);
        var placedLava = (FluidSourceEntity) mc.level.getBlockEntity(lavaPos);
        goggles(placedWater, header, bottomless + water);
        goggles(placedLava, header, bottomless + lava);
        var threshold = AllConfigs.server().fluids.hosePulleyBlockThreshold;
        int original = threshold.get();
        try {
            threshold.set(-1);
            goggles(placedWater, header, fluidPrefix + water, zh ? "已被服务器／整合包规则禁用" : "Disabled by server configuration");
        } finally {
            threshold.set(original);
        }
        goggles(placedWater, header, bottomless + water);
        checkModScreen(mc, zh);
        LogUtils.getLogger().info("COMPACT_FACILITIES_PRESENTATION tooltips, goggles, mod metadata, pack and config passed in {}", language);
    }

    private static List<Component> tooltip(Minecraft mc, ItemStack stack, String... expected) {
        var lines = stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
        var text = lines.stream().map(Component::getString).toList();
        require(text.size() >= expected.length && text.subList(0, expected.length).equals(List.of(expected)),
            "Tooltip mismatch. Expected " + List.of(expected) + ", got " + text);
        return lines;
    }

    static void goggles(FluidSourceEntity source, String... expected) {
        var lines = new ArrayList<Component>();
        require(source.addToGoggleTooltip(lines, false), "Missing Create goggles overlay");
        var text = lines.stream().map(line -> line.getString().strip()).toList();
        require(text.equals(List.of(expected)), "Goggles mismatch. Expected " + List.of(expected) + ", got " + text);
    }

    private static void checkModScreen(Minecraft mc, boolean zh) {
        var container = ModList.get().getModContainerById(CompactFacilities.ID).orElseThrow();
        var info = container.getModInfo();
        String description = FMLTranslations.getPattern("fml.menu.mods.info.description.compact_facilities", info::getDescription);
        require(info.getDisplayName().equals("Create: Compact Facilities") && description.contains(zh ? "一分都不会少" : "without discounting a single part"),
            "Wrong mod list description: " + description);
        var parent = mc.screen;
        var screen = IConfigScreenFactory.getForMod(info).orElseThrow().createScreen(container, parent);
        try {
            mc.setScreen(screen);
            require(mc.screen.getTitle().getString().equals(zh ? "紧凑设施服务端设置" : "Compact Facilities Server Config"), "Wrong visible config title");
            var field = controls(mc.screen).filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> box.getMessage().getString().equals(zh ? "流体源注液次数" : "Fluid Source Spout Fills"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Fill count field missing from config screen"));
            var hint = new StringBuilder();
            for (var line : field.getTooltip().toCharSequence(mc)) {
                line.accept((index, style, codePoint) -> { hint.appendCodePoint(codePoint); return true; });
            }
            require(hint.toString().contains("1,000") && hint.toString().contains(zh ? "软管滑轮" : "threshold"), "Wrong visible config tooltip: " + hint);
        } finally {
            mc.setScreen(parent);
        }
        var pack = info.getOwningFile().getFile().findResource("pack.mcmeta");
        try (var reader = Files.newBufferedReader(pack)) {
            var section = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pack");
            String descriptionText = PackMetadataSection.TYPE.fromJson(section).description().getString();
            require(descriptionText.equals(zh ? "机械动力：紧凑设施内置资源" : "Create: Compact Facilities resources"), "Wrong pack description");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Stream<GuiEventListener> controls(GuiEventListener control) {
        if (control instanceof ContainerEventHandler group) {
            return Stream.concat(Stream.of(control), group.children().stream().flatMap(FluidSourcePresentationChecks::controls));
        }
        return Stream.of(control);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
