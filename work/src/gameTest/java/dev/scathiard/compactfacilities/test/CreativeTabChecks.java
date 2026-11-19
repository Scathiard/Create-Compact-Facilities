package dev.scathiard.compactfacilities.test;

import com.mojang.logging.LogUtils;
import dev.scathiard.compactfacilities.registry.ModCreativeContents;
import dev.scathiard.compactfacilities.registry.ModDataComponents;
import dev.scathiard.compactfacilities.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static dev.scathiard.compactfacilities.test.FluidSourcePresentationChecks.require;

final class CreativeTabChecks {
    static void openAndCheck(Minecraft mc, String language) {
        var screen = new CreativeModeInventoryScreen(mc.player, mc.level.enabledFeatures(), mc.options.operatorItemsTab().get());
        mc.setScreen(screen);
        var tab = ModCreativeContents.MAIN.get();
        boolean chinese = language.equals("zh_cn");
        require(tab.getDisplayName().getString().equals(chinese ? "机械动力：紧凑设施" : "Create: Compact Facilities"), "Incorrect creative tab title");
        checkUnbound(tab.getIconItem());

        select(screen, tab);
        var items = List.copyOf(screen.getMenu().items);
        require(items.size() == 2 && items.getFirst().is(ModItems.FLUID_SOURCE_BLANK.get())
            && !items.getFirst().has(ModDataComponents.FLUID_SOURCE_CHARGE), "Expected the empty source first");
        checkUnbound(items.get(1));
        var ingredients = BuiltInRegistries.CREATIVE_MODE_TAB.get(CreativeModeTabs.INGREDIENTS);
        require(items.stream().noneMatch(ingredients::contains), "Sources duplicated in vanilla Ingredients");

        for (int i = 0; i < items.size(); i++) {
            clickSlot(screen, i);
            require(ItemStack.isSameItemSameComponents(screen.getMenu().getCarried(), items.get(i)), "Creative pickup differs from its tab entry");
            clickSlot(screen, items.size());
            require(screen.getMenu().getCarried().isEmpty(), "Expected an empty cursor");
        }
        select(screen, CreativeModeTabs.searchTab());
        String query = chinese ? "紧凑流体源" : "Compact Fluid Source";
        for (char letter : query.toCharArray()) screen.charTyped(letter, 0);
        var results = screen.getMenu().items;
        require(results.size() == items.size(), "Global search did not narrow down to the two sources");
        for (var item : items) {
            require(results.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, item)).count() == 1,
                "Expected one global search result for " + item);
        }
        select(screen, tab);
        LogUtils.getLogger().info("COMPACT_FACILITIES_CREATIVE_TAB title, icon, two uncharged entries, pickups and search passed in {}", language);
    }

    private static void checkUnbound(ItemStack stack) {
        require(stack.is(ModItems.FLUID_SOURCE.get()) && !stack.has(ModDataComponents.FLUID_SOURCE_CHARGE),
            "Expected an unbound finished source");
    }

    private static void select(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        var page = screen.getCurrentPage();
        while (!page.getVisibleTabs().contains(tab)) {
            var next = screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> button.getMessage().getString().equals(">")).findFirst().orElseThrow();
            next.onPress();
            require(page != screen.getCurrentPage(), "Creative tab missing from all pages");
            page = screen.getCurrentPage();
        }
        int x = screen.getGuiLeft() + page.getColumn(tab) * 27 + 14;
        int y = screen.getGuiTop() + (page.isTop(tab) ? -16 : screen.getYSize() + 16);
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    private static void clickSlot(CreativeModeInventoryScreen screen, int index) {
        var slot = screen.getMenu().getSlot(index);
        int x = screen.getGuiLeft() + slot.x + 8;
        int y = screen.getGuiTop() + slot.y + 8;
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }
}
