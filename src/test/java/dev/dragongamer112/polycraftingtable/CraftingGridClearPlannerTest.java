package dev.dragongamer112.polycraftingtable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CraftingGridClearPlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void movesWhatFitsAndKeepsOverflowInTheGrid() {
        List<ItemStack> inventory = emptyStacks(36);
        for (int slot = 1; slot < inventory.size(); slot++) {
            inventory.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 62));
        List<ItemStack> grid = emptyStacks(9);
        grid.set(0, new ItemStack(Items.OAK_PLANKS, 5));
        grid.set(1, new ItemStack(Items.GLASS, 2));

        var result = CraftingGridClearPlanner.plan(grid, inventory, 64);

        assertTrue(result.succeeded());
        assertEquals(64, result.plan().inventory().get(0).getCount());
        assertEquals(3, result.plan().grid().get(0).getCount());
        assertEquals(2, result.plan().grid().get(1).getCount());
        assertTrue(result.plan().hasLeftovers());
        assertEquals(62, inventory.get(0).getCount());
        assertEquals(5, grid.get(0).getCount());
    }

    @Test
    void clearsAllGridItemsWhenInventoryHasRoom() {
        List<ItemStack> inventory = emptyStacks(36);
        List<ItemStack> grid = emptyStacks(9);
        grid.set(4, new ItemStack(Items.OAK_PLANKS, 12));

        var result = CraftingGridClearPlanner.plan(grid, inventory, 64);

        assertTrue(result.succeeded());
        assertFalse(result.plan().hasLeftovers());
        assertTrue(result.plan().grid().stream().allMatch(ItemStack::isEmpty));
        assertEquals(12, result.plan().inventory().get(0).getCount());
        assertEquals(12, grid.get(4).getCount());
    }

    private static List<ItemStack> emptyStacks(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }
}
