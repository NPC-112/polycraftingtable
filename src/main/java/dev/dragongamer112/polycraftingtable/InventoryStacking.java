package dev.dragongamer112.polycraftingtable;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Shared inventory insertion rules for crafting plans. */
final class InventoryStacking {
    static final int DEFAULT_PLAYER_STACK_LIMIT = 64;

    private InventoryStacking() {
    }

    /**
     * Inserts into a simulated inventory, returning false when some items do
     * not fit. The caller owns the simulation and can discard it on failure.
     */
    static boolean insert(List<ItemStack> inventory, ItemStack stack, int inventoryStackLimit) {
        if (stack.isEmpty()) {
            return true;
        }
        if (inventoryStackLimit < 1) {
            return false;
        }

        ItemStack remaining = stack.copy();
        int incomingLimit = Math.min(remaining.getMaxStackSize(), inventoryStackLimit);
        if (incomingLimit > 1) {
            for (ItemStack existing : inventory) {
                if (existing.isEmpty()) {
                    continue;
                }
                int existingLimit = Math.min(existing.getMaxStackSize(), inventoryStackLimit);
                if (ItemStack.isSameItemSameComponents(existing, remaining)
                        && existing.getCount() < existingLimit) {
                    int moved = Math.min(remaining.getCount(), existingLimit - existing.getCount());
                    existing.grow(moved);
                    remaining.shrink(moved);
                    if (remaining.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
            if (!inventory.get(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), incomingLimit);
            ItemStack inserted = remaining.copy();
            inserted.setCount(moved);
            inventory.set(slot, inserted);
            remaining.shrink(moved);
        }
        return remaining.isEmpty();
    }
}
