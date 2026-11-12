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

final class RecipeAutofillTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
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

    @Test
    void reusesGridVariantRatherThanRequiringAnExtraInventorySlot() {
        List<ItemStack> inventory = emptyStacks(36);
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 64));
        List<ItemStack> grid = emptyStacks(9);
        grid.set(0, new ItemStack(Items.GLASS, 2));
        var flexible = List.of(
                new RecipeAutofill.PlacementRequirement(0,
                        stack -> stack.is(Items.OAK_PLANKS) || stack.is(Items.GLASS)),
                new RecipeAutofill.PlacementRequirement(1,
                        stack -> stack.is(Items.OAK_PLANKS) || stack.is(Items.GLASS)));

        var result = RecipeAutofill.planRequirements(flexible, inventory, grid);

        assertTrue(result.succeeded());
        assertTrue(result.plan().grid().get(0).is(Items.GLASS));
        assertTrue(result.plan().grid().get(1).is(Items.GLASS));
        assertEquals(64, result.plan().inventory().get(0).getCount());
    }

    @Test
    void shiftAutofillEvenlyFillsRepeatedIngredientsToMaximumCraftCount() {
        List<ItemStack> inventory = emptyStacks(36);
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 10));

        var result = RecipeAutofill.planMaximumRequirements(
                twoPlankRequirements(), inventory, emptyStacks(9));

        assertTrue(result.succeeded());
        assertEquals(5, result.plan().crafts());
        assertEquals(5, result.plan().grid().get(0).getCount());
        assertEquals(5, result.plan().grid().get(1).getCount());
        assertTrue(result.plan().inventory().get(0).isEmpty());
        assertEquals(10, inventory.get(0).getCount());
    }

    @Test
    void shiftAutofillUsesTheTightestRepeatedIngredient() {
        List<ItemStack> inventory = emptyStacks(36);
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 10));
        inventory.set(1, new ItemStack(Items.GLASS, 4));
        var requirements = List.of(
                new RecipeAutofill.PlacementRequirement(0, stack -> stack.is(Items.OAK_PLANKS)),
                new RecipeAutofill.PlacementRequirement(1, stack -> stack.is(Items.OAK_PLANKS)),
                new RecipeAutofill.PlacementRequirement(2, stack -> stack.is(Items.GLASS)));

        var result = RecipeAutofill.planMaximumRequirements(
                requirements, inventory, emptyStacks(9));

        assertTrue(result.succeeded());
        assertEquals(4, result.plan().crafts());
        assertEquals(4, result.plan().grid().get(0).getCount());
        assertEquals(4, result.plan().grid().get(1).getCount());
        assertEquals(4, result.plan().grid().get(2).getCount());
        assertEquals(2, result.plan().inventory().get(0).getCount());
    }

    @Test
    void shiftAutofillDoesNotMixDifferentIngredientVariantsInOneSlot() {
        List<ItemStack> inventory = emptyStacks(36);
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 6));
        inventory.set(1, new ItemStack(Items.GLASS, 4));
        var flexible = List.of(
                new RecipeAutofill.PlacementRequirement(0,
                        stack -> stack.is(Items.OAK_PLANKS) || stack.is(Items.GLASS)),
                new RecipeAutofill.PlacementRequirement(1,
                        stack -> stack.is(Items.OAK_PLANKS) || stack.is(Items.GLASS)));

        var result = RecipeAutofill.planMaximumRequirements(
                flexible, inventory, emptyStacks(9));

        assertTrue(result.succeeded());
        assertEquals(4, result.plan().crafts());
        assertEquals(4, result.plan().grid().get(0).getCount());
        assertEquals(4, result.plan().grid().get(1).getCount());
        assertFalse(ItemStack.isSameItemSameComponents(
                result.plan().grid().get(0), result.plan().grid().get(1)));
    }

    @Test
    void shiftAutofillLeavesGridAndInventoryUnchangedWhenLeftoversCannotFit() {
        List<ItemStack> inventory = emptyStacks(36);
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        List<ItemStack> grid = emptyStacks(9);
        grid.set(0, new ItemStack(Items.OAK_PLANKS, 10));
        grid.set(1, new ItemStack(Items.GLASS, 1));

        var result = RecipeAutofill.planMaximumRequirements(
                twoPlankRequirements(), inventory, grid);

        assertEquals(RecipeAutofill.Failure.NO_INVENTORY_SPACE, result.failure());
        assertEquals(64, inventory.get(0).getCount());
        assertEquals(10, grid.get(0).getCount());
        assertEquals(1, grid.get(1).getCount());
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
