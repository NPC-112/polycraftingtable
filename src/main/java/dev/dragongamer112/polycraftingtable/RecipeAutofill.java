package dev.dragongamer112.polycraftingtable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Plans recipe placements without changing the player's inventory. */
final class RecipeAutofill {
    private static final int GRID_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;

    private RecipeAutofill() {
    }

    static boolean supports(CraftingRecipe recipe) {
        if (recipe == null || recipe.isSpecial()) {
            return false;
        }

        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            List<Optional<Ingredient>> ingredients = shaped.getIngredients();
            return width >= 1 && width <= 3
                    && height >= 1 && height <= 3
                    && ingredients.size() == width * height
                    && ingredients.stream().anyMatch(ingredient ->
                    ingredient.isPresent() && !ingredient.get().isEmpty());
        }

        if (recipe instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            return ingredients.size() <= GRID_SIZE
                    && ingredients.stream().anyMatch(ingredient -> !ingredient.isEmpty());
        }

        return false;
    }

    static Optional<Plan> plan(CraftingRecipe recipe, List<ItemStack> inventory) {
        if (!supports(recipe) || inventory.size() < INVENTORY_SIZE) {
            return Optional.empty();
        }

        List<PlacementRequirement> requirements = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            List<Optional<Ingredient>> ingredients = shaped.getIngredients();
            if (width < 1 || width > 3 || height < 1 || height > 3
                    || ingredients.size() != width * height) {
                return Optional.empty();
            }

            for (int index = 0; index < ingredients.size(); index++) {
                Optional<Ingredient> ingredient = ingredients.get(index);
                if (ingredient.isPresent() && !ingredient.get().isEmpty()) {
                    int gridSlot = (index / width) * 3 + index % width;
                    requirements.add(new PlacementRequirement(gridSlot, ingredient.get()));
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.placementInfo().ingredients();
            if (ingredients.size() > GRID_SIZE) {
                return Optional.empty();
            }
            for (int index = 0; index < ingredients.size(); index++) {
                Ingredient ingredient = ingredients.get(index);
                if (!ingredient.isEmpty()) {
                    requirements.add(new PlacementRequirement(index, ingredient));
                }
            }
        } else {
            // Special and custom recipe implementations may compute their inputs dynamically.
            return Optional.empty();
        }

        if (requirements.isEmpty()) {
            return Optional.empty();
        }

        List<InventoryUnit> units = new ArrayList<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int availableUnits = Math.min(stack.getCount(), requirements.size());
            for (int unit = 0; unit < availableUnits; unit++) {
                units.add(new InventoryUnit(slot, stack));
            }
        }

        List<List<Integer>> candidatesByRequirement = new ArrayList<>(requirements.size());
        for (PlacementRequirement requirement : requirements) {
            List<Integer> candidates = new ArrayList<>();
            for (int unit = 0; unit < units.size(); unit++) {
                if (requirement.ingredient().test(units.get(unit).stack())) {
                    candidates.add(unit);
                }
            }
            candidatesByRequirement.add(candidates);
        }

        int[] unitToRequirement = new int[units.size()];
        Arrays.fill(unitToRequirement, -1);
        List<Integer> matchingOrder = new ArrayList<>(requirements.size());
        for (int index = 0; index < requirements.size(); index++) {
            matchingOrder.add(index);
        }
        matchingOrder.sort(Comparator.comparingInt(index -> candidatesByRequirement.get(index).size()));

        for (int requirement : matchingOrder) {
            tryAssign(requirement, candidatesByRequirement, unitToRequirement, new boolean[units.size()]);
        }

        int[] requirementToUnit = new int[requirements.size()];
        Arrays.fill(requirementToUnit, -1);
        for (int unit = 0; unit < unitToRequirement.length; unit++) {
            int requirement = unitToRequirement[unit];
            if (requirement >= 0) {
                requirementToUnit[requirement] = unit;
            }
        }

        List<ItemStack> grid = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            grid.add(ItemStack.EMPTY);
        }
        List<Integer> consumedBySlot = new ArrayList<>(Collections.nCopies(INVENTORY_SIZE, 0));

        int filled = 0;
        for (int index = 0; index < requirements.size(); index++) {
            int unit = requirementToUnit[index];
            if (unit < 0) {
                continue;
            }
            InventoryUnit source = units.get(unit);
            ItemStack placed = inventory.get(source.slot()).copy();
            placed.setCount(1);
            grid.set(requirements.get(index).gridSlot(), placed);
            consumedBySlot.set(source.slot(), consumedBySlot.get(source.slot()) + 1);
            filled++;
        }

        return Optional.of(new Plan(grid, consumedBySlot, filled, requirements.size()));
    }

    private static boolean tryAssign(
            int requirement,
            List<List<Integer>> candidatesByRequirement,
            int[] unitToRequirement,
            boolean[] visitedUnits
    ) {
        for (int unit : candidatesByRequirement.get(requirement)) {
            if (visitedUnits[unit]) {
                continue;
            }
            visitedUnits[unit] = true;
            int displacedRequirement = unitToRequirement[unit];
            if (displacedRequirement < 0
                    || tryAssign(displacedRequirement, candidatesByRequirement, unitToRequirement, visitedUnits)) {
                unitToRequirement[unit] = requirement;
                return true;
            }
        }
        return false;
    }

    record Plan(List<ItemStack> grid, List<Integer> consumedByInventorySlot, int filled, int required) {
    }

    private record PlacementRequirement(int gridSlot, Ingredient ingredient) {
    }

    private record InventoryUnit(int slot, ItemStack stack) {
    }
}
