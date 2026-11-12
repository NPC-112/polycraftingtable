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

final class CraftingBatchPlannerTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftTestBootstrap.initialize();
    }

    @Test
    void plansAllCraftsWithoutChangingLiveInputs() {
        List<ItemStack> grid = twoIngredientGrid(3);
        List<ItemStack> inventory = emptyStacks(36);

        var result = CraftingBatchPlanner.plan(
                grid, inventory, 64, new ItemStack(Items.STICK, 2), operations(
                        ignored -> new ItemStack(Items.STICK, 2), ignored -> List.of()
                )
        );

        assertTrue(result.succeeded());
        assertEquals(3, result.plan().crafts().size());
        assertEquals(6, result.plan().inventory().get(0).getCount());
        assertTrue(result.plan().grid().stream().allMatch(ItemStack::isEmpty));
        assertEquals(3, grid.get(0).getCount());
        assertTrue(inventory.stream().allMatch(ItemStack::isEmpty));
    }

    @Test
    void stopsBeforeAnyCraftWhoseOutputCannotFit() {
        List<ItemStack> grid = twoIngredientGrid(2);
        List<ItemStack> inventory = filledInventory();

        var result = CraftingBatchPlanner.plan(
                grid, inventory, 64, new ItemStack(Items.GLASS), operations(
                        ignored -> new ItemStack(Items.GLASS), ignored -> List.of()
                )
        );

        assertEquals(CraftingBatchPlanner.Failure.NO_INVENTORY_SPACE, result.failure());
        assertFalse(result.succeeded());
        assertEquals(2, grid.get(0).getCount());
        assertEquals(64, inventory.get(0).getCount());
    }

    @Test
    void craftsOnlyAsManyAsTheInventoryCanHold() {
        List<ItemStack> grid = twoIngredientGrid(3);
        List<ItemStack> inventory = filledInventory();
        inventory.set(0, ItemStack.EMPTY);

        var result = CraftingBatchPlanner.plan(
                grid, inventory, 64, new ItemStack(Items.GLASS), operations(
                        ignored -> new ItemStack(Items.GLASS), ignored -> List.of()
                )
        );

        assertTrue(result.succeeded());
        assertEquals(1, result.plan().crafts().size());
        assertTrue(result.plan().inventory().get(0).is(Items.GLASS));
        assertEquals(2, result.plan().grid().get(0).getCount());
        assertEquals(3, grid.get(0).getCount());
    }

    @Test
    void requiresSpaceForRemaindersAsWellAsTheOutput() {
        List<ItemStack> grid = twoIngredientGrid(1);
        List<ItemStack> inventory = filledInventory();
        inventory.set(0, ItemStack.EMPTY);

        var result = CraftingBatchPlanner.plan(
                grid,
                inventory,
                64,
                new ItemStack(Items.GLASS),
                operations(ignored -> new ItemStack(Items.GLASS),
                        ignored -> List.of(new ItemStack(Items.STICK)))
        );

        assertEquals(CraftingBatchPlanner.Failure.NO_INVENTORY_SPACE, result.failure());
        assertTrue(inventory.get(0).isEmpty());
        assertEquals(1, grid.get(0).getCount());
    }

    @Test
    void leavesNonStackableResultsToTheSingleCraftPath() {
        var result = CraftingBatchPlanner.plan(
                twoIngredientGrid(2),
                emptyStacks(36),
                64,
                new ItemStack(Items.IRON_PICKAXE),
                operations(ignored -> new ItemStack(Items.IRON_PICKAXE), ignored -> List.of())
        );

        assertEquals(CraftingBatchPlanner.Failure.NON_STACKABLE, result.failure());
    }

    @Test
    void stopsBatchWhenLaterCraftsWouldProduceDifferentResults() {
        List<ItemStack> grid = twoIngredientGrid(2);

        var result = CraftingBatchPlanner.plan(
                grid,
                emptyStacks(36),
                64,
                new ItemStack(Items.STICK),
                operations(
                        ingredients -> new ItemStack(
                                ingredients.get(0).getCount() == 2 ? Items.STICK : Items.GLASS
                        ),
                        ignored -> List.of()
                )
        );

        assertTrue(result.succeeded());
        assertEquals(1, result.plan().crafts().size());
        assertTrue(result.plan().crafts().get(0).output().is(Items.STICK));
        assertEquals(1, result.plan().grid().get(0).getCount());
    }

    private static CraftingBatchPlanner.RecipeOperations operations(
            java.util.function.Function<List<ItemStack>, ItemStack> output,
            java.util.function.Function<List<ItemStack>, List<ItemStack>> remainders
    ) {
        return new CraftingBatchPlanner.RecipeOperations() {
            @Override
            public boolean matches(List<ItemStack> grid) {
                return !grid.get(0).isEmpty() && !grid.get(1).isEmpty();
            }

            @Override
            public ItemStack assemble(List<ItemStack> grid) {
                return output.apply(grid);
            }

            @Override
            public List<ItemStack> remainingItems(List<ItemStack> grid) {
                return remainders.apply(grid);
            }
        };
    }

    private static List<ItemStack> twoIngredientGrid(int count) {
        List<ItemStack> grid = emptyStacks(9);
        grid.set(0, new ItemStack(Items.OAK_PLANKS, count));
        grid.set(1, new ItemStack(Items.OAK_PLANKS, count));
        return grid;
    }

    private static List<ItemStack> filledInventory() {
        List<ItemStack> inventory = emptyStacks(36);
        for (int slot = 0; slot < inventory.size(); slot++) {
            inventory.set(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        return inventory;
    }

    private static List<ItemStack> emptyStacks(int size) {
        return new ArrayList<>(Collections.nCopies(size, ItemStack.EMPTY));
    }
}
