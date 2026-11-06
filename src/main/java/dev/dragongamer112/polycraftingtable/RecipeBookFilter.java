package dev.dragongamer112.polycraftingtable;

public final class RecipeBookFilter {
    private RecipeBookFilter() {
    }

    public static boolean shouldIncludeCraftingDisplay(boolean polymerBacked) {
        return !polymerBacked;
    }
}
