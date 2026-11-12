package dev.dragongamer112.polycraftingtable;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Builds an all-or-nothing batch-craft plan without changing live inventories. */
final class CraftingBatchPlanner {
    private static final int GRID_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;

    private CraftingBatchPlanner() {
    }

    /**
     * Plans from a grid already matched to the selected recipe; {@code firstOutput}
     * is that grid's assembled result.
     */
    static PlanResult plan(
            List<ItemStack> grid,
            List<ItemStack> inventory,
            int inventoryStackLimit,
            ItemStack firstOutput,
            RecipeOperations recipe
    ) {
        if (grid.size() != GRID_SIZE || inventory.size() < INVENTORY_SIZE
                || inventoryStackLimit < 1) {
            return new PlanResult(null, Failure.UNSUPPORTED);
        }

        List<ItemStack> plannedGrid = copyStacks(grid);
        int maximumCrafts = Integer.MAX_VALUE;
        for (ItemStack stack : plannedGrid) {
            if (!stack.isEmpty()) {
                maximumCrafts = Math.min(maximumCrafts, stack.getCount());
            }
        }
        if (maximumCrafts == Integer.MAX_VALUE || maximumCrafts < 1) {
            return new PlanResult(null, Failure.NO_MATCH);
        }
        if (firstOutput.isEmpty()) {
            return new PlanResult(null, Failure.EMPTY_OUTPUT);
        }
        if (firstOutput.getMaxStackSize() <= 1) {
            return new PlanResult(null, Failure.NON_STACKABLE);
        }

        List<ItemStack> plannedInventory = copyStacks(inventory);
        List<CraftRecord> crafts = new ArrayList<>(maximumCrafts);
        for (int craft = 0; craft < maximumCrafts; craft++) {
            List<ItemStack> ingredients = copyStacks(plannedGrid);
            if (craft > 0 && !recipe.matches(ingredients)) {
                break;
            }

            ItemStack output = craft == 0
                    ? firstOutput.copy()
                    : recipe.assemble(ingredients);
            if (output.isEmpty()
                    || output.getMaxStackSize() <= 1
                    || !ItemStack.isSameItemSameComponents(firstOutput, output)) {
                break;
            }

            List<ItemStack> nextInventory = copyStacks(plannedInventory);
            if (!InventoryStacking.insert(nextInventory, output, inventoryStackLimit)) {
                break;
            }
            List<ItemStack> remainders = recipe.remainingItems(ingredients).stream()
                    .map(ItemStack::copy)
                    .toList();
            boolean allRemaindersFit = true;
            for (ItemStack remainder : remainders) {
                if (!InventoryStacking.insert(nextInventory, remainder, inventoryStackLimit)) {
                    allRemaindersFit = false;
                    break;
                }
            }
            if (!allRemaindersFit) {
                break;
            }

            plannedInventory = nextInventory;
            crafts.add(new CraftRecord(output.copy(), ingredients));
            shrinkIngredients(plannedGrid);
        }

        if (crafts.isEmpty()) {
            return new PlanResult(null, Failure.NO_INVENTORY_SPACE);
        }
        return new PlanResult(
                new Plan(copyStacks(plannedGrid), copyStacks(plannedInventory), List.copyOf(crafts)),
                Failure.NONE
        );
    }

    private static void shrinkIngredients(List<ItemStack> grid) {
        for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack ingredient = grid.get(slot);
            if (ingredient.isEmpty()) {
                continue;
            }
            ingredient.shrink(1);
            if (ingredient.isEmpty()) {
                grid.set(slot, ItemStack.EMPTY);
            }
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copies.add(stack.copy());
        }
        return copies;
    }

    interface RecipeOperations {
        boolean matches(List<ItemStack> grid);

        ItemStack assemble(List<ItemStack> grid);

        List<ItemStack> remainingItems(List<ItemStack> grid);
    }

    enum Failure {
        NONE,
        UNSUPPORTED,
        NO_MATCH,
        EMPTY_OUTPUT,
        NON_STACKABLE,
        NO_INVENTORY_SPACE
    }

    record PlanResult(Plan plan, Failure failure) {
        boolean succeeded() {
            return this.plan != null && this.failure == Failure.NONE;
        }
    }

    record Plan(List<ItemStack> grid, List<ItemStack> inventory, List<CraftRecord> crafts) {
    }

    record CraftRecord(ItemStack output, List<ItemStack> ingredients) {
    }
}
