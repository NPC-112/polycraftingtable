package dev.dragongamer112.polycraftingtable;

import java.util.Optional;
import java.util.function.Predicate;

/** Preserves registry order and never evaluates an output before its inputs match. */
public final class RecipeSelection {
    private RecipeSelection() {
    }

    public static <T> Optional<T> firstAllowed(
            Iterable<T> orderedRecipes, Predicate<T> matches, Predicate<T> allowed
    ) {
        for (T recipe : orderedRecipes) {
            if (matches.test(recipe) && allowed.test(recipe)) {
                return Optional.of(recipe);
            }
        }
        return Optional.empty();
    }
}
