package dev.dragongamer112.polycraftingtable;

import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class CraftingRecipeGameTests {
    @GameTest
    public void shapedRecipeReturnsExpectedOutput(GameTestHelper helper) {
        ItemStack empty = ItemStack.EMPTY;
        CraftingInput input = grid(
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), empty, new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS)
        );
        ItemStack result = matchingRecipe(helper, input).value().assemble(input);

        assertStack(helper, result, Items.CHEST, 1, "shaped chest recipe");
        helper.assertTrue(
                ItemStack.isSameItemSameComponents(result, new ItemStack(Items.CHEST)),
                "The shaped recipe should return a plain chest without unexpected components."
        );
        helper.succeed();
    }

    @GameTest
    public void shapelessRecipeReturnsExpectedOutput(GameTestHelper helper) {
        CraftingInput input = grid(
                new ItemStack(Items.OAK_LOG), ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        ItemStack result = matchingRecipe(helper, input).value().assemble(input);

        assertStack(helper, result, Items.OAK_PLANKS, 4, "shapeless planks recipe");
        helper.assertTrue(
                ItemStack.isSameItemSameComponents(result, new ItemStack(Items.OAK_PLANKS, 4)),
                "The shapeless recipe should return four plain oak planks."
        );
        helper.succeed();
    }

    @GameTest
    public void toolRepairUpdatesDamageComponent(GameTestHelper helper) {
        ItemStack firstPickaxe = damagedWoodenPickaxe(40);
        ItemStack secondPickaxe = damagedWoodenPickaxe(40);
        CraftingInput input = grid(
                firstPickaxe, secondPickaxe, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        ItemStack result = matchingRecipe(helper, input).value().assemble(input);
        int expectedDamage = Math.max(
                0,
                firstPickaxe.get(DataComponents.DAMAGE)
                        + secondPickaxe.get(DataComponents.DAMAGE)
                        - firstPickaxe.getMaxDamage()
                        - firstPickaxe.getMaxDamage() / 20
        );

        assertStack(helper, result, Items.WOODEN_PICKAXE, 1, "tool repair recipe");
        helper.assertTrue(
                result.getOrDefault(DataComponents.DAMAGE, 0) == expectedDamage,
                "The repaired pickaxe should have damage " + expectedDamage
                        + ", got " + result.getOrDefault(DataComponents.DAMAGE, 0) + "."
        );
        helper.succeed();
    }

    @GameTest
    public void mapCloningCopiesMapIdAndExpectedCount(GameTestHelper helper) {
        ItemStack filledMap = new ItemStack(Items.FILLED_MAP);
        MapId mapId = new MapId(347);
        filledMap.set(DataComponents.MAP_ID, mapId);
        CraftingInput input = grid(
                filledMap, new ItemStack(Items.MAP), new ItemStack(Items.MAP),
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        ItemStack result = matchingRecipe(helper, input).value().assemble(input);

        assertStack(helper, result, Items.FILLED_MAP, 3, "map cloning recipe");
        helper.assertTrue(
                mapId.equals(result.get(DataComponents.MAP_ID)),
                "Cloned maps should retain the source map ID."
        );
        helper.succeed();
    }

    @GameTest
    public void fireworksRecipeSetsFlightComponent(GameTestHelper helper) {
        CraftingInput input = grid(
                new ItemStack(Items.PAPER), new ItemStack(Items.GUNPOWDER), ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        ItemStack result = matchingRecipe(helper, input).value().assemble(input);

        assertStack(helper, result, Items.FIREWORK_ROCKET, 3, "fireworks recipe");
        helper.assertTrue(
                new Fireworks(1, List.of()).equals(result.get(DataComponents.FIREWORKS)),
                "Unadorned fireworks should have flight duration 1 and no explosions."
        );
        helper.succeed();
    }

    @GameTest
    public void cakeRecipeReturnsAllBucketRemainders(GameTestHelper helper) {
        CraftingInput input = grid(
                new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.SUGAR), new ItemStack(Items.EGG), new ItemStack(Items.SUGAR),
                new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT)
        );
        CraftingRecipe recipe = matchingRecipe(helper, input).value();
        ItemStack result = recipe.assemble(input);
        List<ItemStack> remainders = recipe.getRemainingItems(input);
        long bucketCount = remainders.stream().filter(stack -> stack.is(Items.BUCKET)).count();
        long otherRemainderCount = remainders.stream().filter(stack -> !stack.isEmpty() && !stack.is(Items.BUCKET)).count();

        assertStack(helper, result, Items.CAKE, 1, "cake recipe");
        helper.assertTrue(bucketCount == 3, "The cake recipe should return three empty buckets.");
        helper.assertTrue(otherRemainderCount == 0, "The cake recipe should not return unexpected remainders.");
        helper.succeed();
    }

    @GameTest
    public void shapedAutofillUsesDeclaredSlotsAndInventoryAlternatives(GameTestHelper helper) {
        CraftingInput chestInput = grid(
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS)
        );
        CraftingRecipe recipe = matchingRecipe(helper, chestInput).value();
        helper.assertTrue(RecipeAutofill.supports(recipe), "Shaped recipes should support safe autofill.");
        List<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_PLANKS, 8));

        RecipeAutofill.Plan plan = RecipeAutofill.plan(recipe, inventory).orElseThrow();
        helper.assertTrue(plan.filled() == 8, "The shaped recipe should fill all eight declared ingredient slots.");
        helper.assertTrue(plan.required() == 8, "The shaped recipe should require eight ingredients.");
        helper.assertTrue(plan.grid().get(4).isEmpty(), "The shaped recipe's center slot should remain empty.");
        helper.assertTrue(plan.grid().get(0).is(Items.OAK_PLANKS), "The shaped pattern should start at the top left.");
        helper.assertTrue(
                plan.consumedByInventorySlot().get(0) == 8,
                "Autofill should plan to use eight planks from the source stack."
        );
        helper.succeed();
    }

    @GameTest
    public void shapelessAutofillLeavesUnavailableIngredientsEmpty(GameTestHelper helper) {
        CraftingInput planksInput = grid(
                new ItemStack(Items.OAK_LOG), ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        CraftingRecipe recipe = matchingRecipe(helper, planksInput).value();
        helper.assertTrue(RecipeAutofill.supports(recipe), "Shapeless recipes should support safe autofill.");
        List<ItemStack> inventory = emptyInventory();
        inventory.set(0, new ItemStack(Items.OAK_LOG));

        RecipeAutofill.Plan plan = RecipeAutofill.plan(recipe, inventory).orElseThrow();
        helper.assertTrue(plan.filled() == 1, "The shapeless recipe should fill its one available ingredient.");
        helper.assertTrue(plan.grid().get(0).is(Items.OAK_LOG), "The shapeless ingredient should be placed in the grid.");
        helper.assertTrue(plan.grid().get(1).isEmpty(), "Unused grid positions should remain empty.");
        helper.assertTrue(
                plan.consumedByInventorySlot().get(0) == 1,
                "Autofill should plan to move one log only after placing it."
        );
        helper.succeed();
    }

    @GameTest
    public void specialRecipeAutofillIsRejectedWithoutGuessing(GameTestHelper helper) {
        CraftingInput repairInput = grid(
                damagedWoodenPickaxe(40), damagedWoodenPickaxe(40), ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY
        );
        CraftingRecipe recipe = matchingRecipe(helper, repairInput).value();
        helper.assertTrue(!RecipeAutofill.supports(recipe), "Special recipes should be skipped in the recent bar.");
        List<ItemStack> inventory = emptyInventory();
        inventory.set(0, damagedWoodenPickaxe(40));
        inventory.set(1, damagedWoodenPickaxe(40));

        helper.assertTrue(
                RecipeAutofill.plan(recipe, inventory).isEmpty(),
                "A special recipe should not produce a guessed autofill plan."
        );
        helper.assertTrue(
                inventory.get(0).getCount() == 1 && inventory.get(1).getCount() == 1,
                "Rejecting a special recipe plan must leave its source items unchanged."
        );
        helper.succeed();
    }

    @GameTest
    public void vanillaLookupAndRecipeBookFilteringStaySelective(GameTestHelper helper) {
        CraftingInput input = grid(
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), ItemStack.EMPTY, new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS)
        );
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Optional<RecipeHolder<CraftingRecipe>> vanillaMatch =
                manager.getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
        helper.assertTrue(vanillaMatch.isPresent(), "Ordinary shaped recipes should still match in vanilla crafting.");

        ItemStack ordinaryOutput = new ItemStack(Items.CHEST);
        helper.assertTrue(
                RecipeBookFilter.shouldIncludeCraftingDisplay(PolymerItemUtils.isPolymerServerItem(ordinaryOutput, null)),
                "An ordinary crafting display should remain in the vanilla recipe book."
        );

        ItemStack polymerOutput = new ItemStack(PolyCraftingTableMod.TABLE_ITEM);
        helper.assertTrue(
                PolymerItemUtils.isPolymerServerItem(polymerOutput, null),
                "The registered custom table item should be recognized by Polymer as a server-side item."
        );
        helper.assertTrue(
                !RecipeBookFilter.shouldIncludeCraftingDisplay(PolymerItemUtils.isPolymerServerItem(polymerOutput, null)),
                "A Polymer-backed crafting display should be excluded from the vanilla recipe book."
        );
        helper.succeed();
    }

    @GameTest
    public void recentItemHistoryMergesAndStaysBounded(GameTestHelper helper) {
        Identifier first = Identifier.fromNamespaceAndPath("test", "first");
        Identifier second = Identifier.fromNamespaceAndPath("test", "second");
        Identifier older = Identifier.fromNamespaceAndPath("test", "older");
        List<Identifier> merged = PolydexMenu.mergeRecentIds(
                List.of(first, second, first),
                List.of(second, older)
        );
        helper.assertTrue(
                merged.equals(List.of(first, second, older)),
                "Current Polydex history should come first, with saved IDs appended once."
        );

        List<Identifier> manyRecentIds = new java.util.ArrayList<>();
        for (int index = 0; index < PolydexMenu.MAX_PERSISTED_RECENT_ITEMS + 10; index++) {
            manyRecentIds.add(Identifier.fromNamespaceAndPath("test", "item_" + index));
        }
        List<Identifier> bounded = PolydexMenu.mergeRecentIds(manyRecentIds, List.of(older));
        helper.assertTrue(
                bounded.size() == PolydexMenu.MAX_PERSISTED_RECENT_ITEMS,
                "Saved recent history should never exceed its configured size limit."
        );
        helper.assertTrue(
                bounded.get(bounded.size() - 1).equals(manyRecentIds.get(PolydexMenu.MAX_PERSISTED_RECENT_ITEMS - 1)),
                "The size limit should retain the most recent Polydex IDs first."
        );
        helper.succeed();
    }

    private static ItemStack damagedWoodenPickaxe(int damage) {
        ItemStack pickaxe = new ItemStack(Items.WOODEN_PICKAXE);
        pickaxe.set(DataComponents.DAMAGE, damage);
        return pickaxe;
    }

    private static CraftingInput grid(ItemStack... stacks) {
        return CraftingInput.of(3, 3, Arrays.asList(stacks));
    }

    private static List<ItemStack> emptyInventory() {
        return new java.util.ArrayList<>(java.util.Collections.nCopies(36, ItemStack.EMPTY));
    }

    private static RecipeHolder<CraftingRecipe> matchingRecipe(GameTestHelper helper, CraftingInput input) {
        Level level = helper.getLevel();
        Optional<RecipeHolder<CraftingRecipe>> match = CraftingRecipeLookup.matchingRecipe(
                level.getServer().getRecipeManager(),
                input,
                level
        );
        helper.assertTrue(match.isPresent(), "Expected a matching crafting recipe for the test input.");
        return match.orElseThrow();
    }

    private static void assertStack(
            GameTestHelper helper, ItemStack stack, Item expectedItem, int expectedCount, String recipeName
    ) {
        helper.assertTrue(stack.is(expectedItem), "The " + recipeName + " should return " + expectedItem + ".");
        helper.assertTrue(
                stack.getCount() == expectedCount,
                "The " + recipeName + " should return " + expectedCount + " items, got " + stack.getCount() + "."
        );
    }
}
