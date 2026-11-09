package dev.dragongamer112.polycraftingtable.mixin;

import dev.dragongamer112.polycraftingtable.VanillaCraftingLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @Inject(
            method = "getPotentialResults(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void polycraftingtable$restrictedResults(
            ServerLevel level, CraftingInput input,
            CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir
    ) {
        // The vanilla cache is keyed by input stacks, not world conditions.
        // Re-evaluate dynamic matching/output and ordered alternatives on use.
        cir.setReturnValue(VanillaCraftingLookup.matchingRecipe(level.recipeAccess(), input, level));
    }
}
