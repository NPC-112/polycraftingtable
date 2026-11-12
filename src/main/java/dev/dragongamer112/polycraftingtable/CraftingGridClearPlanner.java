package dev.dragongamer112.polycraftingtable;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Plans returning crafting-grid contents while preserving anything that cannot fit. */
final class CraftingGridClearPlanner {
    private static final int GRID_SIZE = 9;
    private static final int INVENTORY_SIZE = 36;

    private CraftingGridClearPlanner() {
    }

    static PlanResult plan(List<ItemStack> grid, List<ItemStack> inventory, int inventoryStackLimit) {
        if (grid.size() != GRID_SIZE || inventory.size() < INVENTORY_SIZE || inventoryStackLimit < 1) {
            return new PlanResult(null, Failure.INVALID_INPUT);
        }

        List<ItemStack> updatedInventory = copyStacks(inventory);
        List<ItemStack> updatedGrid = new ArrayList<>(GRID_SIZE);
        boolean hasLeftovers = false;
        for (ItemStack stack : grid) {
            ItemStack remainder = InventoryStacking.insertPartial(
                    updatedInventory, stack, inventoryStackLimit
            );
            updatedGrid.add(remainder);
            hasLeftovers |= !remainder.isEmpty();
        }

        return new PlanResult(
                new Plan(List.copyOf(updatedGrid), List.copyOf(updatedInventory), hasLeftovers),
                Failure.NONE
        );
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copies.add(stack.copy());
        }
        return copies;
    }

    enum Failure {
        NONE,
        INVALID_INPUT
    }

    record PlanResult(Plan plan, Failure failure) {
        boolean succeeded() {
            return this.plan != null && this.failure == Failure.NONE;
        }
    }

    record Plan(List<ItemStack> grid, List<ItemStack> inventory, boolean hasLeftovers) {
    }
}
