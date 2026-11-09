package dev.dragongamer112.polycraftingtable.mixin;

import dev.dragongamer112.polycraftingtable.VanillaCraftingLookup;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Inject(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void polycraftingtable$filterVanillaLookup(
            RecipeType<T> type,
            I input,
            Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        filterResult(type, input, level, cir);
    }

    @Inject(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void polycraftingtable$filterHintedVanillaLookup(
            RecipeType<T> type,
            I input,
            Level level,
            ResourceKey<Recipe<?>> recipeHint,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        filterResult(type, input, level, cir);
    }

    @Inject(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void polycraftingtable$filterCachedVanillaLookup(
            RecipeType<T> type,
            I input,
            Level level,
            RecipeHolder<T> recipeHint,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        filterResult(type, input, level, cir);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <I extends RecipeInput, T extends Recipe<I>> void filterResult(
            RecipeType<T> type, I input, Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        if (type.equals(RecipeType.CRAFTING) && input instanceof CraftingInput craftingInput) {
            // All overloads use the same ordered selection; neither a stale hint
            // nor a restricted first match may bypass or conceal allowed recipes.
            cir.setReturnValue((Optional) VanillaCraftingLookup.matchingRecipe(
                    (RecipeManager) (Object) this, craftingInput, level));
        }
    }
}
