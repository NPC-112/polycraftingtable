package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PageTextures;
import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polydex.impl.book.ui.ExtendedGui;
import eu.pb4.polydex.impl.book.ui.GuiUtils;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.polymer.resourcepack.extras.api.ResourcePackExtras;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class PolymerCraftingTableGui extends SimpleGui {
    private static final int[] INPUT_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int RESULT_SLOT = 24;
    private static final int ARROW_SLOT = 23;
    private static final int CLEAR_GRID_SLOT = RESULT_SLOT + 2;
    private static final int POLYDEX_SLOT = 7;
    private static final int INSTANT_CRAFTING_SLOT = 8;
    private static final int FIRST_RECENT_SLOT = 45;
    private static final int RECENT_SLOT_COUNT = 9;

    private final BlockPos pos;
    private final Map<Identifier, Identifier> sessionLastFilledRecipes = new HashMap<>();
    private boolean instantCraftingEnabled;
    private boolean suppressResultUpdate;
    private boolean returningInputs;
    private final SimpleContainer input = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (!PolymerCraftingTableGui.this.suppressResultUpdate) {
                PolymerCraftingTableGui.this.updateResult();
            }
        }
    };

    PolymerCraftingTableGui(ServerPlayer player, BlockPos pos) {
        super(MenuType.GENERIC_9x6, player, false);
        PolydexReturnSession.cancel(player);
        this.pos = pos.immutable();
        this.instantCraftingEnabled = player.getAttachedOrElse(
                PolyCraftingTableMod.INSTANT_CRAFTING_ENABLED,
                false
        );
        this.setTitle(ExtendedGui.formatTexturedText(
                player,
                PageTextures.CRAFTING,
                null,
                PolyCraftingTableMod.TABLE_NAME
        ));

        GuiElementBuilder filler = GuiUtils.hasTexture(player)
                ? GuiUtils.EMPTY_STACK
                : GuiUtils.FILLER;
        for (int slot = 0; slot < 54; slot++) {
            this.setSlot(slot, filler.build());
        }

        if (!GuiUtils.hasTexture(player)) {
            this.setSlot(ARROW_SLOT, new GuiElementBuilder(Items.ARROW)
                    .setName(Component.literal("Crafting"))
                    .hideDefaultTooltip()
                    .build());
        }
        this.setSlot(POLYDEX_SLOT, this.buttonBuilder(Items.KNOWLEDGE_BOOK, "polydex_button")
                .setName(Component.literal("Open Polydex"))
                .hideDefaultTooltip()
                .setCallback(() -> PolyCraftingTableMod.openPolydexMenu(this.player, this.pos))
                .build());
        this.updateInstantCraftingButton();
        this.setSlot(CLEAR_GRID_SLOT, this.buttonBuilder(Items.BUCKET, "clear_button")
                .setName(Component.literal("Clear Crafting Grid"))
                .hideDefaultTooltip()
                .setCallback((index, clickType, action, gui) -> {
                    if (action == ContainerInput.PICKUP || action == ContainerInput.QUICK_MOVE) {
                        GuiUtils.playClickSound(this.player);
                        this.clearCraftingGrid();
                    }
                })
                .build());
        List<PolydexMenu.RecentRecipe> recentRecipes =
                PolydexMenu.recentCraftingRecipes(this.player, RECENT_SLOT_COUNT);
        for (int index = 0; index < recentRecipes.size(); index++) {
            PolydexMenu.RecentRecipe recent = recentRecipes.get(index);
            this.setSlot(FIRST_RECENT_SLOT + index, new GuiElementBuilder(recent.icon().copy())
                    .setName(recent.icon().getHoverName())
                    .setCallback((slot, clickType, action, gui) -> {
                        if (action != ContainerInput.PICKUP
                                && action != ContainerInput.QUICK_MOVE) {
                            return;
                        }
                        if (clickType.isRight && !clickType.shift) {
                            this.openRecentEntryInPolydex(recent.entry());
                        } else if (clickType.shift) {
                            this.autofillFromInventory(recent, true);
                        } else if (this.instantCraftingEnabled) {
                            this.instantCraftFromInventory(recent);
                        } else {
                            this.autofillFromInventory(recent, false);
                        }
                    })
                    .build());
        }

        for (int inputIndex = 0; inputIndex < INPUT_SLOTS.length; inputIndex++) {
            int index = inputIndex;
            this.setSlot(INPUT_SLOTS[index], new Slot(this.input, index, 0, 0));
        }

        this.updateResult();
        this.open();
    }

    private static Identifier buttonModel(String name) {
        return ResourcePackExtras.bridgeModel(Identifier.fromNamespaceAndPath(
                PolyCraftingTableMod.MOD_ID,
                "sgui/elements/" + name
        ));
    }

    private GuiElementBuilder buttonBuilder(Item item, String model) {
        GuiElementBuilder builder = new GuiElementBuilder(item);
        if (GuiUtils.hasTexture(this.player)) {
            builder.model(buttonModel(model));
        }
        return builder;
    }

    private void updateInstantCraftingButton() {
        Item icon = this.instantCraftingEnabled ? Items.SLIME_BALL : Items.MAGMA_CREAM;
        String model = this.instantCraftingEnabled ? "instant_craft_on" : "instant_craft_off";
        String state = this.instantCraftingEnabled ? "On" : "Off";
        this.setSlot(INSTANT_CRAFTING_SLOT, this.buttonBuilder(icon, model)
                .setName(Component.literal("Instant Crafting: " + state))
                .hideDefaultTooltip()
                .setCallback((index, clickType, action, gui) -> {
                    if (action != ContainerInput.PICKUP && action != ContainerInput.QUICK_MOVE) {
                        return;
                    }
                    this.instantCraftingEnabled = !this.instantCraftingEnabled;
                    this.player.setAttached(
                            PolyCraftingTableMod.INSTANT_CRAFTING_ENABLED,
                            this.instantCraftingEnabled
                    );
                    this.updateInstantCraftingButton();
                    GuiUtils.playClickSound(this.player);
                })
                .build());
    }

    BlockPos tablePos() {
        return this.pos;
    }

    private void autofillFromInventory(PolydexMenu.RecentRecipe recent, boolean fillMultiple) {
        List<ItemStack> inventoryBefore = this.inventorySnapshot();
        List<ItemStack> gridBefore = this.inputSnapshot();

        PolydexMenu.RecipeCandidate selected = null;
        RecipeAutofill.Plan selectedPlan = null;
        boolean inventorySpaceBlocked = false;
        for (PolydexMenu.RecipeCandidate candidate : this.orderedCandidates(recent)) {
            RecipeAutofill.PlanResult attempt = fillMultiple
                    ? RecipeAutofill.planMaximum(
                            candidate.recipe(), inventoryBefore, gridBefore,
                            this.player.getInventory().getMaxStackSize()
                    )
                    : RecipeAutofill.plan(
                            candidate.recipe(), inventoryBefore, gridBefore,
                            this.player.getInventory().getMaxStackSize()
                    );
            if (attempt.succeeded()
                    && candidate.recipe().matches(craftingInput(attempt.plan().grid()), this.player.level())) {
                selected = candidate;
                selectedPlan = attempt.plan();
                break;
            }
            if (attempt.failure() == RecipeAutofill.Failure.NO_INVENTORY_SPACE) {
                inventorySpaceBlocked = true;
            }
        }

        if (selected == null || selectedPlan == null) {
            this.player.sendOverlayMessage(Component.literal(inventorySpaceBlocked
                    ? "Make room in your inventory before switching recipes."
                    : "No complete recipe matches the grid and inventory."));
            return;
        }

        var inventory = this.player.getInventory();
        if (!this.inventoryMatches(inventoryBefore) || !this.inputMatches(gridBefore)) {
            this.updateResult();
            this.player.sendOverlayMessage(Component.literal(
                    "Your grid or inventory changed. Try autofilling again."
            ));
            return;
        }

        boolean wasSuppressed = this.suppressResultUpdate;
        boolean committed = true;
        this.suppressResultUpdate = true;
        try {
            for (int slot = 0; slot < 9; slot++) {
                this.input.setItem(slot, selectedPlan.grid().get(slot).copy());
            }
            for (int slot = 0; slot < 36; slot++) {
                inventory.setItem(slot, selectedPlan.inventory().get(slot).copy());
            }
            if (!this.inputMatches(selectedPlan.grid()) || !this.inventoryMatches(selectedPlan.inventory())) {
                throw new IllegalStateException("Crafting input replacement did not match its plan.");
            }
        } catch (RuntimeException exception) {
            for (int slot = 0; slot < 9; slot++) {
                this.input.setItem(slot, gridBefore.get(slot).copy());
            }
            for (int slot = 0; slot < 36; slot++) {
                inventory.setItem(slot, inventoryBefore.get(slot).copy());
            }
            committed = false;
        } finally {
            this.suppressResultUpdate = wasSuppressed;
        }

        if (!committed) {
            this.updateResult();
            this.player.sendOverlayMessage(Component.literal(
                    "The recipe could not be placed safely. No ingredients were consumed."
            ));
            return;
        }

        this.rememberRecipeChoice(recent, selected);
        this.updateResult();
        this.player.sendOverlayMessage(Component.literal(
                fillMultiple
                        ? "Filled recipe " + selected.id() + " for " + selectedPlan.crafts() + " crafts."
                        : "Filled recipe " + selected.id() + "."
        ));
    }

    private List<PolydexMenu.RecipeCandidate> orderedCandidates(PolydexMenu.RecentRecipe recent) {
        Identifier previousRecipeId = this.sessionLastFilledRecipes.get(recent.entryId());
        boolean advanceAfterPrevious = previousRecipeId != null;
        if (previousRecipeId == null) {
            previousRecipeId = PolydexMenu.lastFilledRecipeId(this.player, recent.entryId());
        }

        List<Identifier> candidateIds = recent.recipes().stream()
                .map(PolydexMenu.RecipeCandidate::id)
                .toList();
        List<Identifier> orderedIds = RecipeCycleOrder.order(
                candidateIds,
                previousRecipeId,
                advanceAfterPrevious
        );
        Map<Identifier, PolydexMenu.RecipeCandidate> candidatesById = new HashMap<>();
        for (PolydexMenu.RecipeCandidate candidate : recent.recipes()) {
            candidatesById.put(candidate.id(), candidate);
        }

        List<PolydexMenu.RecipeCandidate> ordered = new ArrayList<>(orderedIds.size());
        for (Identifier id : orderedIds) {
            PolydexMenu.RecipeCandidate candidate = candidatesById.get(id);
            if (candidate != null) {
                ordered.add(candidate);
            }
        }
        return ordered;
    }

    private void rememberRecipeChoice(
            PolydexMenu.RecentRecipe recent,
            PolydexMenu.RecipeCandidate selected
    ) {
        this.sessionLastFilledRecipes.put(recent.entryId(), selected.id());
        PolydexMenu.rememberFilledRecipe(this.player, recent.entryId(), selected.id());
    }

    private void instantCraftFromInventory(PolydexMenu.RecentRecipe recent) {
        List<ItemStack> inventoryBefore = this.inventorySnapshot();
        List<ItemStack> gridBefore = this.inputSnapshot();
        List<ItemStack> emptyGrid = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            emptyGrid.add(ItemStack.EMPTY);
        }

        int inventoryStackLimit = this.player.getInventory().getMaxStackSize();
        PolydexMenu.RecipeCandidate selected = null;
        List<ItemStack> selectedIngredients = null;
        List<ItemStack> selectedInventory = null;
        ItemStack selectedOutput = ItemStack.EMPTY;
        boolean inventorySpaceBlocked = false;

        for (PolydexMenu.RecipeCandidate candidate : this.orderedCandidates(recent)) {
            RecipeAutofill.PlanResult attempt = RecipeAutofill.plan(
                    candidate.recipe(), inventoryBefore, emptyGrid, inventoryStackLimit
            );
            if (!attempt.succeeded()) {
                inventorySpaceBlocked |= attempt.failure() == RecipeAutofill.Failure.NO_INVENTORY_SPACE;
                continue;
            }

            List<ItemStack> ingredients = attempt.plan().grid();
            CraftingInput recipeInput = craftingInput(ingredients);
            if (!candidate.recipe().matches(recipeInput, this.player.level())) {
                continue;
            }

            ItemStack output = candidate.recipe().assemble(recipeInput);
            if (output.isEmpty()) {
                continue;
            }

            List<ItemStack> plannedInventory = copyStacks(attempt.plan().inventory());
            if (!InventoryStacking.insert(plannedInventory, output, inventoryStackLimit)) {
                inventorySpaceBlocked = true;
                continue;
            }

            List<ItemStack> remainders = candidate.recipe().getRemainingItems(recipeInput).stream()
                    .map(ItemStack::copy)
                    .toList();
            boolean allRemaindersFit = true;
            for (ItemStack remainder : remainders) {
                if (!InventoryStacking.insert(plannedInventory, remainder, inventoryStackLimit)) {
                    allRemaindersFit = false;
                    break;
                }
            }
            if (!allRemaindersFit) {
                inventorySpaceBlocked = true;
                continue;
            }

            selected = candidate;
            selectedIngredients = ingredients;
            selectedInventory = plannedInventory;
            selectedOutput = output.copy();
            break;
        }

        if (selected == null || selectedIngredients == null || selectedInventory == null) {
            this.player.sendOverlayMessage(Component.literal(inventorySpaceBlocked
                    ? "Make room in your inventory to craft this."
                    : "No complete recipe matches your inventory."));
            return;
        }

        if (!this.inventoryMatches(inventoryBefore) || !this.inputMatches(gridBefore)) {
            this.player.sendOverlayMessage(Component.literal(
                    "Your grid or inventory changed. Try crafting again."
            ));
            return;
        }
        if (!this.replaceInventory(inventoryBefore, selectedInventory)) {
            this.player.sendOverlayMessage(Component.literal(
                    "The item could not be crafted safely. No ingredients were consumed."
            ));
            return;
        }

        this.rememberRecipeChoice(recent, selected);
        this.player.level().playSound(
                null,
                this.pos,
                SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );
        this.updateResult();

        ItemStack craftedResult = selectedOutput.copy();
        craftedResult.onCraftedBy(this.player, craftedResult.getCount());
        RecipeHolder<CraftingRecipe> recipe = new RecipeHolder<>(
                ResourceKey.create(Registries.RECIPE, selected.id()),
                selected.recipe()
        );
        this.player.triggerRecipeCrafted(recipe, selectedIngredients);
        if (!selected.recipe().isSpecial()) {
            this.player.awardRecipes(List.of(recipe));
        }
    }

    private boolean replaceInventory(List<ItemStack> expected, List<ItemStack> replacement) {
        if (!this.inventoryMatches(expected)) {
            return false;
        }

        try {
            for (int slot = 0; slot < 36; slot++) {
                this.player.getInventory().setItem(slot, replacement.get(slot).copy());
            }
            if (!this.inventoryMatches(replacement)) {
                throw new IllegalStateException("Inventory replacement did not match its plan.");
            }
            return true;
        } catch (RuntimeException exception) {
            for (int slot = 0; slot < 36; slot++) {
                this.player.getInventory().setItem(slot, expected.get(slot).copy());
            }
            return false;
        }
    }

    private void clearCraftingGrid() {
        List<ItemStack> inventoryBefore = this.inventorySnapshot();
        List<ItemStack> gridBefore = this.inputSnapshot();
        if (gridBefore.stream().allMatch(ItemStack::isEmpty)) {
            return;
        }

        CraftingGridClearPlanner.PlanResult attempt = CraftingGridClearPlanner.plan(
                gridBefore, inventoryBefore, this.player.getInventory().getMaxStackSize()
        );
        if (!attempt.succeeded()) {
            this.player.sendOverlayMessage(Component.literal(
                    "The grid could not be cleared safely. No items were moved."
            ));
            return;
        }
        List<ItemStack> plannedInventory = attempt.plan().inventory();
        List<ItemStack> plannedGrid = attempt.plan().grid();

        if (!this.inventoryMatches(inventoryBefore) || !this.inputMatches(gridBefore)) {
            this.player.sendOverlayMessage(Component.literal(
                    "Your grid or inventory changed. Try clearing again."
            ));
            return;
        }

        boolean wasSuppressed = this.suppressResultUpdate;
        boolean committed = true;
        this.suppressResultUpdate = true;
        try {
            for (int slot = 0; slot < 36; slot++) {
                this.player.getInventory().setItem(slot, plannedInventory.get(slot).copy());
            }
            for (int slot = 0; slot < 9; slot++) {
                this.input.setItem(slot, plannedGrid.get(slot).copy());
            }
            if (!this.inventoryMatches(plannedInventory) || !this.inputMatches(plannedGrid)) {
                throw new IllegalStateException("Crafting grid clear did not match its plan.");
            }
        } catch (RuntimeException exception) {
            for (int slot = 0; slot < 36; slot++) {
                this.player.getInventory().setItem(slot, inventoryBefore.get(slot).copy());
            }
            for (int slot = 0; slot < 9; slot++) {
                this.input.setItem(slot, gridBefore.get(slot).copy());
            }
            committed = false;
        } finally {
            this.suppressResultUpdate = wasSuppressed;
        }

        if (!committed) {
            this.player.sendOverlayMessage(Component.literal(
                    "The grid could not be cleared safely. No items were moved."
            ));
            return;
        }

        this.updateResult();
        if (attempt.plan().hasLeftovers()) {
            this.player.sendOverlayMessage(Component.literal(
                    "Inventory full; some items remain in the crafting grid."
            ));
        }
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copies = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copies.add(stack.copy());
        }
        return copies;
    }

    private void openRecentEntryInPolydex(PolydexEntry entry) {
        if (!PolydexMenu.canOpenEntryPage(this.player, entry)) {
            this.player.sendOverlayMessage(Component.literal(
                    "This item no longer has a Polydex recipe page available."
            ));
            return;
        }

        PolydexReturnSession.openEntry(this.player, this.pos, entry);
    }

    private boolean inventoryMatches(List<ItemStack> expected) {
        for (int slot = 0; slot < expected.size(); slot++) {
            ItemStack actual = this.player.getInventory().getItem(slot);
            ItemStack original = expected.get(slot);
            if (actual.getCount() != original.getCount()
                    || !ItemStack.isSameItemSameComponents(actual, original)) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> inventorySnapshot() {
        List<ItemStack> stacks = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) {
            stacks.add(this.player.getInventory().getItem(slot).copy());
        }
        return stacks;
    }

    private GuiElement filler() {
        return GuiUtils.hasTexture(this.player)
                ? GuiUtils.EMPTY_STACK.build()
                : GuiUtils.FILLER.build();
    }

    private List<ItemStack> inputSnapshot() {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            stacks.add(this.input.getItem(slot).copy());
        }
        return stacks;
    }

    private Optional<RecipeHolder<CraftingRecipe>> matchingRecipe(CraftingInput input) {
        return CraftingRecipeLookup.matchingRecipe(
                this.player.level().getServer().getRecipeManager(),
                input,
                this.player.level()
        );
    }

    private void updateResult() {
        List<ItemStack> inputSnapshot = this.inputSnapshot();
        CraftingInput currentInput = craftingInput(inputSnapshot);
        ItemStack output = this.matchingRecipe(currentInput)
                .map(holder -> holder.value().assemble(currentInput))
                .orElse(ItemStack.EMPTY);
        if (output.isEmpty()) {
            this.setSlot(RESULT_SLOT, this.filler());
            return;
        }

        this.setSlot(RESULT_SLOT, new GuiElementBuilder(output.copy())
                .setCallback((index, clickType, action, gui) -> {
                    if (action == ContainerInput.QUICK_MOVE && clickType.shift) {
                        this.craftMany(this.player);
                    } else if (action == ContainerInput.PICKUP || action == ContainerInput.QUICK_MOVE) {
                        this.craftOne(this.player);
                    }
                })
                .build());
    }

    private void craftMany(ServerPlayer player) {
        List<ItemStack> gridBefore = this.inputSnapshot();
        Optional<RecipeHolder<CraftingRecipe>> match =
                this.matchingRecipe(craftingInput(gridBefore));
        if (match.isEmpty()) {
            this.updateResult();
            return;
        }

        RecipeHolder<CraftingRecipe> recipe = match.get();
        CraftingInput initialInput = craftingInput(gridBefore);
        ItemStack firstOutput = recipe.value().assemble(initialInput);
        List<ItemStack> inventoryBefore = this.inventorySnapshot();
        CraftingBatchPlanner.PlanResult attempt = CraftingBatchPlanner.plan(
                gridBefore,
                inventoryBefore,
                player.getInventory().getMaxStackSize(),
                firstOutput,
                new CraftingBatchPlanner.RecipeOperations() {
                    @Override
                    public boolean matches(List<ItemStack> grid) {
                        return recipe.value().matches(craftingInput(grid), player.level());
                    }

                    @Override
                    public ItemStack assemble(List<ItemStack> grid) {
                        return recipe.value().assemble(craftingInput(grid));
                    }

                    @Override
                    public List<ItemStack> remainingItems(List<ItemStack> grid) {
                        return recipe.value().getRemainingItems(craftingInput(grid));
                    }
                }
        );
        if (attempt.failure() == CraftingBatchPlanner.Failure.NON_STACKABLE) {
            this.craftOne(player);
            return;
        }
        if (!attempt.succeeded()) {
            this.updateResult();
            if (attempt.failure() == CraftingBatchPlanner.Failure.NO_INVENTORY_SPACE) {
                player.sendOverlayMessage(Component.literal("Make room in your inventory to craft this."));
            }
            return;
        }

        CraftingBatchPlanner.Plan plan = attempt.plan();
        if (!this.inputMatches(gridBefore) || !this.inventoryMatches(inventoryBefore)) {
            this.updateResult();
            player.sendOverlayMessage(Component.literal(
                    "Your grid or inventory changed. Try crafting again."
            ));
            return;
        }

        boolean wasSuppressed = this.suppressResultUpdate;
        boolean committed = true;
        this.suppressResultUpdate = true;
        try {
            for (int slot = 0; slot < 9; slot++) {
                this.input.setItem(slot, plan.grid().get(slot).copy());
            }
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, plan.inventory().get(slot).copy());
            }
            if (!this.inputMatches(plan.grid()) || !this.inventoryMatches(plan.inventory())) {
                throw new IllegalStateException("Shift-craft replacement did not match its plan.");
            }
        } catch (RuntimeException exception) {
            for (int slot = 0; slot < 9; slot++) {
                this.input.setItem(slot, gridBefore.get(slot).copy());
            }
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, inventoryBefore.get(slot).copy());
            }
            committed = false;
        } finally {
            this.suppressResultUpdate = wasSuppressed;
        }

        if (!committed) {
            this.updateResult();
            player.sendOverlayMessage(Component.literal(
                    "The items could not be moved safely. No ingredients were consumed."
            ));
            return;
        }

        player.level().playSound(
                null,
                this.pos,
                SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );
        this.updateResult();

        for (CraftingBatchPlanner.CraftRecord craft : plan.crafts()) {
            ItemStack craftedResult = craft.output().copy();
            craftedResult.onCraftedBy(player, craftedResult.getCount());
            player.triggerRecipeCrafted(recipe, craft.ingredients());
            if (!recipe.value().isSpecial()) {
                player.awardRecipes(List.of(recipe));
            }
        }
    }

    private void craftOne(ServerPlayer player) {
        List<ItemStack> usedItems = this.inputSnapshot();
        CraftingInput currentInput = craftingInput(usedItems);

        Optional<RecipeHolder<CraftingRecipe>> match = this.matchingRecipe(currentInput);
        if (match.isEmpty()) {
            this.updateResult();
            return;
        }

        RecipeHolder<CraftingRecipe> recipe = match.get();
        ItemStack output = recipe.value().assemble(currentInput);
        if (output.isEmpty()) {
            this.updateResult();
            return;
        }

        List<ItemStack> remainders = recipe.value().getRemainingItems(currentInput).stream()
                .map(ItemStack::copy)
                .toList();
        if (!this.inputMatches(usedItems)) {
            this.updateResult();
            return;
        }

        if (!this.canFitCraft(output, remainders)) {
            player.sendOverlayMessage(Component.literal("Make room in your inventory to craft this."));
            return;
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = this.input.getItem(slot);
            if (!stack.isEmpty()) {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    this.input.setItem(slot, ItemStack.EMPTY);
                } else {
                    this.input.setChanged();
                }
            }
        }

        ItemStack craftedResult = output.copy();
        player.getInventory().placeItemBackInInventory(output.copy(), Prediction.SERVER_ONLY);
        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                player.getInventory().placeItemBackInInventory(remainder.copy(), Prediction.SERVER_ONLY);
            }
        }

        player.level().playSound(
                null,
                this.pos,
                SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );
        this.updateResult();

        // Complete the craft only after all inputs and outputs are safely committed.
        // The detached stack prevents an item callback from changing the delivered result.
        craftedResult.onCraftedBy(player, craftedResult.getCount());
        player.triggerRecipeCrafted(recipe, usedItems);
        if (!recipe.value().isSpecial()) {
            // Keep recipe unlocks flowing through ServerRecipeBookMixin's outgoing filter.
            player.awardRecipes(List.of(recipe));
        }
    }

    private CraftingInput craftingInput(List<ItemStack> stacks) {
        return CraftingInput.of(3, 3, stacks.stream().map(ItemStack::copy).toList());
    }

    private boolean inputMatches(List<ItemStack> expected) {
        for (int slot = 0; slot < expected.size(); slot++) {
            ItemStack actual = this.input.getItem(slot);
            ItemStack original = expected.get(slot);
            if (actual.getCount() != original.getCount()
                    || !ItemStack.isSameItemSameComponents(actual, original)) {
                return false;
            }
        }
        return true;
    }

    private boolean canFitCraft(ItemStack output, List<ItemStack> remainders) {
        List<ItemStack> simulatedInventory = this.inventorySnapshot();
        int inventoryStackLimit = this.player.getInventory().getMaxStackSize();
        if (!InventoryStacking.insert(simulatedInventory, output, inventoryStackLimit)) {
            return false;
        }
        for (ItemStack remainder : remainders) {
            if (!InventoryStacking.insert(simulatedInventory, remainder, inventoryStackLimit)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean onClick(int index, ClickType clickType, ContainerInput action, GuiElement element) {
        return index == RESULT_SLOT
                || index == CLEAR_GRID_SLOT
                || index == POLYDEX_SLOT
                || index == INSTANT_CRAFTING_SLOT
                || (index >= FIRST_RECENT_SLOT && index < FIRST_RECENT_SLOT + RECENT_SLOT_COUNT);
    }

    @Override
    public void onTick() {
        if (this.player.position().distanceToSqr(Vec3.atCenterOf(this.pos)) > 18 * 18
                || !this.player.level().getBlockState(this.pos).is(PolyCraftingTableMod.TABLE)) {
            this.close();
            return;
        }
        super.onTick();
    }

    @Override
    public void onRemoved() {
        // SGUI calls this for every menu removal, including another mod opening
        // a screen. onPlayerClose only handles a client close-screen packet.
        if (this.returningInputs) {
            return;
        }

        this.returningInputs = true;
        boolean wasSuppressed = this.suppressResultUpdate;
        this.suppressResultUpdate = true;
        try {
            for (int slot = 0; slot < this.input.getContainerSize(); slot++) {
                ItemStack stack = this.input.getItem(slot);
                if (!stack.isEmpty()) {
                    // Vanilla returns what fits and drops any overflow. Clear
                    // only after delivery; empty slots make repeat calls safe.
                    this.player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
                    this.input.setItem(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            this.suppressResultUpdate = wasSuppressed;
            this.returningInputs = false;
        }
        super.onRemoved();
    }
}
