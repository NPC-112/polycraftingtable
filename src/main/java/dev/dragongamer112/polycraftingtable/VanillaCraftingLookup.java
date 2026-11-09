package dev.dragongamer112.polycraftingtable;

import dev.dragongamer112.polycraftingtable.mixin.RecipeManagerAccessor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

/** Separate from the custom table's deliberately unrestricted lookup. */
public final class VanillaCraftingLookup {
    private VanillaCraftingLookup() {
    }

    public static Optional<RecipeHolder<CraftingRecipe>> matchingRecipe(
            RecipeManager manager, CraftingInput input, Level level
    ) {
        if (input.isEmpty()) {
            return Optional.empty();
        }
        var policy = CraftingRestrictions.policy();
        var recipes = ((RecipeManagerAccessor) manager).polycraftingtable$getRecipes().byType(RecipeType.CRAFTING);
        return RecipeSelection.firstAllowed(
                recipes,
                holder -> holder.value().matches(input, level),
                holder -> !policy.explicitlyRestricts(holder.id().identifier().toString())
                        && policy.allowsOutput(CraftingRestrictions.itemId(holder.value().assemble(input)))
        );
    }
}
