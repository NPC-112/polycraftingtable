package dev.dragongamer112.polycraftingtable;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Polymer detection for vanilla crafting lookup and recipe-book filtering. */
public final class PolymerRecipes {
    private PolymerRecipes() {
    }

    public static boolean isPolymerOutput(ItemStack stack) {
        return !stack.isEmpty() && PolymerItemUtils.isPolymerServerItem(stack, null);
    }

    public static List<ItemStack> displayedOutputs(RecipeDisplay display, Level level) {
        return display.result().resolveForStacks(SlotDisplayContext.fromLevel(level));
    }

    public static boolean isPolymerDisplay(RecipeDisplay display, Level level) {
        return displayedOutputs(display, level).stream().anyMatch(PolymerRecipes::isPolymerOutput);
    }

    public static ItemStack displayedOutput(RecipeDisplay display, Level level) {
        return displayedOutputs(display, level).stream()
                .filter(PolymerRecipes::isPolymerOutput)
                .findFirst()
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
    }
}
