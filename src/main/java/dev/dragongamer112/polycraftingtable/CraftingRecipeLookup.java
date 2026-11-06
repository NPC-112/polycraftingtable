package dev.dragongamer112.polycraftingtable;

import dev.dragongamer112.polycraftingtable.mixin.RecipeManagerAccessor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

final class CraftingRecipeLookup {
    private CraftingRecipeLookup() {
    }

    static Optional<RecipeHolder<CraftingRecipe>> matchingRecipe(
            RecipeManager manager, CraftingInput input, Level level
    ) {
        // Read the complete registry directly: ordinary lookup intentionally hides
        // Polymer results. Keep registry order for the same first-match priority.
        var recipes = ((RecipeManagerAccessor) manager)
                .polycraftingtable$getRecipes()
                .byType(RecipeType.CRAFTING);
        return recipes.stream()
                .filter(holder -> holder.value().matches(input, level))
                .findFirst();
    }
}
