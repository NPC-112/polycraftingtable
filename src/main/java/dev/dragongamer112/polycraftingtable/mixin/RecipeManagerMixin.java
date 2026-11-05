package dev.dragongamer112.polycraftingtable.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import eu.pb4.polymer.core.api.item.PolymerItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
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
        filterResult(cir);
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
        filterResult(cir);
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
        filterResult(cir);
    }

    private static void filterResult(CallbackInfoReturnable<?> cir) {
        Object result = cir.getReturnValue();
        if (!(result instanceof Optional<?> optional)
                || optional.isEmpty()
                || !(optional.get() instanceof RecipeHolder<?> holder)
                || !(holder.value() instanceof CraftingRecipe recipe)) {
            return;
        }

        CraftingInput emptyInput = CraftingInput.of(3, 3, emptyGrid());
        ItemStack output = recipe.assemble(emptyInput);
        if (!output.isEmpty() && output.getItem() instanceof PolymerItem) {
            setEmpty(cir);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEmpty(CallbackInfoReturnable<?> cir) {
        ((CallbackInfoReturnable) cir).setReturnValue(Optional.empty());
    }

    private static List<ItemStack> emptyGrid() {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            stacks.add(ItemStack.EMPTY);
        }
        return stacks;
    }
}
