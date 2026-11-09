package dev.dragongamer112.polycraftingtable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.Level;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RecipeBookFilter {
    private static final Set<Identifier> WARNED_DISPLAYS = ConcurrentHashMap.newKeySet();

    private RecipeBookFilter() {
    }

    public static boolean shouldIncludeCraftingDisplay(Identifier recipeId, RecipeDisplay display, Level level) {
        var policy = CraftingRestrictions.policy();
        if (policy.explicitlyRestricts(recipeId.toString())) {
            return false;
        }
        try {
            var outputs = display.result().resolveForStacks(SlotDisplayContext.fromLevel(level));
            if (outputs.isEmpty()) {
                warnOnce(recipeId, "no declared output could be resolved");
            }
            return policy.allowsDisplay(recipeId.toString(),
                    outputs.stream().map(CraftingRestrictions::itemId).toList());
        } catch (RuntimeException exception) {
            warnOnce(recipeId, "output display resolution failed: " + exception.getMessage());
            return false;
        }
    }

    private static void warnOnce(Identifier recipeId, String reason) {
        if (WARNED_DISPLAYS.add(recipeId)) {
            PolyCraftingTableMod.LOGGER.warn("Hiding vanilla recipe-book entry {} because {}. "
                    + "Valid-input crafting, the custom table and Polydex are not removed.", recipeId, reason);
        }
    }

    static void clearWarnings() {
        WARNED_DISPLAYS.clear();
    }
}
