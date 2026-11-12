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

final class InventoryStackingTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void neverExceedsTheInventoryLimitForEmptySlots() {
        List<ItemStack> inventory = emptyStacks(3);
        ItemStack incoming = new ItemStack(Items.OAK_PLANKS, 40);

        assertTrue(InventoryStacking.insert(inventory, incoming, 16));

        assertEquals(16, inventory.get(0).getCount());
        assertEquals(16, inventory.get(1).getCount());
        assertEquals(8, inventory.get(2).getCount());
        assertEquals(40, incoming.getCount());
    }

    @Test
    void existingStacksRespectTheInventoryLimit() {
        List<ItemStack> inventory = new ArrayList<>(List.of(
                new ItemStack(Items.OAK_PLANKS, 15)
        ));

        assertFalse(InventoryStacking.insert(
                inventory, new ItemStack(Items.OAK_PLANKS, 5), 16
        ));

        assertEquals(16, inventory.get(0).getCount());
    }

    @Test
    void partialInsertionReturnsItemsThatDoNotFit() {
        List<ItemStack> inventory = new ArrayList<>(List.of(
                new ItemStack(Items.OAK_PLANKS, 15),
                new ItemStack(Items.COBBLESTONE, 64)
        ));

        ItemStack remainder = InventoryStacking.insertPartial(
                inventory, new ItemStack(Items.OAK_PLANKS, 5), 16
        );

        assertEquals(16, inventory.get(0).getCount());
        assertEquals(4, remainder.getCount());
        assertTrue(remainder.is(Items.OAK_PLANKS));
    }

    @Test
    void partialInsertionUsesEveryAvailableEmptySlot() {
        List<ItemStack> inventory = emptyStacks(2);

        ItemStack remainder = InventoryStacking.insertPartial(
                inventory, new ItemStack(Items.OAK_PLANKS, 40), 16
        );

        assertEquals(16, inventory.get(0).getCount());
        assertEquals(16, inventory.get(1).getCount());
        assertEquals(8, remainder.getCount());
    }

    private static List<ItemStack> emptyStacks(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }
}
