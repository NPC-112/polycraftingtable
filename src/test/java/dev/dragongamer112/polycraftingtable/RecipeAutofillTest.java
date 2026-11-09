package dev.dragongamer112.polycraftingtable;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RecipeAutofillTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void reusesGridIngredientsAndReturnsUnusedItemsToInventory() {
        List<ItemStack> inventory = emptyStacks(36);
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 1));
        List<ItemStack> grid = emptyStacks(9);
        grid.set(1, new ItemStack(Items.GLASS, 1));
        grid.set(8, new ItemStack(Items.OAK_PLANKS, 1));

        RecipeAutofill.PlanResult result = RecipeAutofill.planRequirements(
                twoPlankRequirements(), inventory, grid);

        assertTrue(result.succeeded());
        assertTrue(result.plan().grid().get(0).is(Items.OAK_PLANKS));
        assertTrue(result.plan().grid().get(1).is(Items.OAK_PLANKS));
        assertTrue(result.plan().inventory().stream().anyMatch(stack -> stack.is(Items.GLASS)));

        assertEquals(1, inventory.get(0).getCount());
        assertTrue(grid.get(1).is(Items.GLASS));
        assertTrue(grid.get(8).is(Items.OAK_PLANKS));
    }

    @Test
    void leavesBothSourcesUnchangedWhenGridLeftoversCannotFit() {
        List<ItemStack> inventory = emptyStacks(36);
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        List<ItemStack> grid = emptyStacks(9);
        grid.set(0, new ItemStack(Items.OAK_PLANKS, 1));
        grid.set(1, new ItemStack(Items.OAK_PLANKS, 1));
        grid.set(2, new ItemStack(Items.GLASS, 1));

        RecipeAutofill.PlanResult result = RecipeAutofill.planRequirements(
                twoPlankRequirements(), inventory, grid);

        assertEquals(RecipeAutofill.Failure.NO_INVENTORY_SPACE, result.failure());
        assertEquals(64, inventory.get(0).getCount());
        assertTrue(inventory.get(0).is(Items.COBBLESTONE));
        assertTrue(grid.get(0).is(Items.OAK_PLANKS));
        assertTrue(grid.get(2).is(Items.GLASS));
    }

    @Test
    void rejectsCandidatesThatCannotBeCompletelyFilled() {
        List<ItemStack> inventory = emptyStacks(36);
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 1));

        RecipeAutofill.PlanResult result = RecipeAutofill.planRequirements(
                twoPlankRequirements(), inventory, emptyStacks(9));

        assertEquals(RecipeAutofill.Failure.MISSING_INGREDIENTS, result.failure());
        assertFalse(result.succeeded());
    }

    private static List<RecipeAutofill.PlacementRequirement> twoPlankRequirements() {
        return List.of(
                new RecipeAutofill.PlacementRequirement(0, stack -> stack.is(Items.OAK_PLANKS)),
                new RecipeAutofill.PlacementRequirement(1, stack -> stack.is(Items.OAK_PLANKS))
        );
    }

    private static List<ItemStack> emptyStacks(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }
}
