package dev.scathiard.compactfacilities.test;

import com.mojang.logging.LogUtils;
import dev.scathiard.compactfacilities.registry.ModItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

@JeiPlugin
public final class JeiPresentationCheck implements IModPlugin {
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() { return ResourceLocation.fromNamespaceAndPath("compact_facilities_test", "presentation"); }

    @Override
    public void onRuntimeAvailable(IJeiRuntime available) { runtime = available; }

    @Override
    public void onRuntimeUnavailable() { runtime = null; }

    static boolean ready() { return runtime != null; }

    static void check(Minecraft mc) {
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, ModItems.FLUID_SOURCE_BLANK.toStack());
        String rule = Component.translatable("tooltip.compact_facilities.jei.fluid_source_rule").getString();
        var recipes = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION).limitFocus(List.of(focus)).get();
        FluidSourcePresentationChecks.require(recipes.anyMatch(recipe -> recipe.getDescription().stream().map(FormattedText::getString)
            .anyMatch(rule::equals)), "JEI has no filling information page for the empty source");
        checkSlot(mc, "mechanical_crafting", "bound_water", Fluids.WATER);
        checkSlot(mc, "deploying", "bound_lava", Fluids.LAVA);
        LogUtils.getLogger().info("COMPACT_FACILITIES_JEI information page and both Create recipe slots passed");
    }

    private static void checkSlot(Minecraft mc, String category, String testRecipe, FlowingFluid fluid) {
        var recipe = mc.level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("compact_facilities_test", testRecipe)).orElseThrow();
        var manager = runtime.getRecipeManager();
        var type = manager.getRecipeType(ResourceLocation.fromNamespaceAndPath("create", category)).orElseThrow();
        var layout = layoutFor(type, recipe);
        String hint = Component.translatable("tooltip.compact_facilities.ingredient.bound_fluid_source", new FluidStack(fluid, 1000).getHoverName()).getString();
        boolean present = layout.getRecipeSlotsView().getSlotViews(RecipeIngredientRole.INPUT).stream().flatMap(slot -> slot.getItemStacks())
            .filter(stack -> stack.is(ModItems.FLUID_SOURCE.get())).anyMatch(stack -> stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL)
                .stream().anyMatch(line -> line.getString().equals(hint)));
        FluidSourcePresentationChecks.require(present, "Missing bound-fluid hint on JEI recipe " + testRecipe);
    }

    private static <T> IRecipeLayoutDrawable<T> layoutFor(RecipeType<T> type, RecipeHolder<?> recipe) {
        var manager = runtime.getRecipeManager();
        return manager.createRecipeLayoutDrawable(manager.getRecipeCategory(type), type.getRecipeClass().cast(recipe),
            runtime.getJeiHelpers().getFocusFactory().createFocusGroup(List.of())).orElseThrow();
    }
}
