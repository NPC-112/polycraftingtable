package dev.dragongamer112.polycraftingtable.mixin;

import dev.dragongamer112.polycraftingtable.PolymerRecipes;
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
            at = @At("RETURN"),
            cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void polycraftingtable$filterVanillaLookup(
            RecipeType<T> type,
            I input,
            Level level,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        filterResult(input, cir);
    }

    @Inject(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceKey;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void polycraftingtable$filterHintedVanillaLookup(
            RecipeType<T> type,
            I input,
            Level level,
            ResourceKey<Recipe<?>> recipeHint,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        filterResult(input, cir);
    }

    @Inject(
            method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("RETURN"),
            cancellable = true
    )
    private <I extends RecipeInput, T extends Recipe<I>> void polycraftingtable$filterCachedVanillaLookup(
            RecipeType<T> type,
            I input,
            Level level,
            RecipeHolder<T> recipeHint,
            CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        filterResult(input, cir);
    }

    private static <I extends RecipeInput, T extends Recipe<I>> void filterResult(
            I input, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir
    ) {
        var result = cir.getReturnValue();
        if (result.isPresent()
                && result.get().value().getType().equals(RecipeType.CRAFTING)
                && PolymerRecipes.isPolymerOutput(result.get().value().assemble(input))) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
