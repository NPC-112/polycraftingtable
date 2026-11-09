package dev.dragongamer112.polycraftingtable;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Produces a stable candidate order around a remembered recipe. */
final class RecipeCycleOrder {
    private RecipeCycleOrder() {
    }

    static List<Identifier> order(List<Identifier> candidates, Identifier pivot, boolean afterPivot) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        int pivotIndex = candidates.indexOf(pivot);
        if (pivotIndex < 0) {
            return List.copyOf(candidates);
        }

        int firstIndex = Math.floorMod(pivotIndex + (afterPivot ? 1 : 0), candidates.size());
        List<Identifier> ordered = new ArrayList<>(candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            ordered.add(candidates.get((firstIndex + offset) % candidates.size()));
        }
        return List.copyOf(ordered);
    }
}
