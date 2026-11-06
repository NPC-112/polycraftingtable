package dev.dragongamer112.polycraftingtable.mixin;

import eu.pb4.polydex.api.v1.recipe.AbstractRecipePolydexPage;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractRecipePolydexPage.class)
public interface PolydexRecipePageAccessor {
    @Accessor("recipe")
    Recipe<?> polycraftingtable$getRecipe();
}
