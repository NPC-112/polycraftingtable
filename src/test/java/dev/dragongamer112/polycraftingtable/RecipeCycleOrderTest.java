package dev.dragongamer112.polycraftingtable;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class RecipeCycleOrderTest {
    private static final Identifier FIRST = id("first");
    private static final Identifier SECOND = id("second");
    private static final Identifier THIRD = id("third");

    @Test
    void startsWithRememberedRecipeOnFirstVisit() {
        assertEquals(List.of(SECOND, THIRD, FIRST),
                RecipeCycleOrder.order(List.of(FIRST, SECOND, THIRD), SECOND, false));
    }

    @Test
    void advancesAndWrapsAfterLastSuccessfulRecipe() {
        assertEquals(List.of(FIRST, SECOND, THIRD),
                RecipeCycleOrder.order(List.of(FIRST, SECOND, THIRD), THIRD, true));
    }

    @Test
    void ignoresARememberedRecipeThatIsNoLongerVisible() {
        assertEquals(List.of(FIRST, SECOND, THIRD),
                RecipeCycleOrder.order(List.of(FIRST, SECOND, THIRD), id("removed"), true));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("test", path);
    }
}
