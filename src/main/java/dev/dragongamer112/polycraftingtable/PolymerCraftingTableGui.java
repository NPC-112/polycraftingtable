package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PageTextures;
import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polydex.impl.book.ui.ExtendedGui;
import eu.pb4.polydex.impl.book.ui.GuiUtils;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.elements.SimpleGuiElement;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
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
    private static final int FIRST_RECENT_SLOT = 45;
    private static final int RECENT_SLOT_COUNT = 7;
    private static final int SPACER_SLOT = 52;
    private static final int POLYDEX_SLOT = 53;

    private final BlockPos pos;
    private final Map<Identifier, Identifier> sessionLastFilledRecipes = new HashMap<>();
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
        this.setTitle(ExtendedGui.formatTexturedText(
                player,
                PageTextures.CRAFTING,
                CraftingTableGuiTextures.RECENT_SPACER,
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
        this.setSlot(POLYDEX_SLOT, new GuiElementBuilder(Items.KNOWLEDGE_BOOK)
                .setName(Component.literal("Open Polydex"))
                .setCallback(() -> PolyCraftingTableMod.openPolydexMenu(this.player, this.pos))
                .build());

        // The title overlay covers the recessed chest slot; the element itself
        // stays empty and inert, with no item or tooltip, even without the pack.
        this.setSlot(SPACER_SLOT, SimpleGuiElement.EMPTY);
        List<PolydexMenu.RecentRecipe> recentRecipes =
                PolydexMenu.recentCraftingRecipes(this.player, RECENT_SLOT_COUNT);
        for (int index = 0; index < recentRecipes.size(); index++) {
            PolydexMenu.RecentRecipe recent = recentRecipes.get(index);
            this.setSlot(FIRST_RECENT_SLOT + index, new GuiElementBuilder(recent.icon().copy())
                    .setName(recent.icon().getHoverName())
                    .setCallback((slot, clickType, action, gui) -> {
                        if (action != ContainerInput.PICKUP) {
                            return;
                        }
                        if (clickType.isRight) {
                            this.openRecentEntryInPolydex(recent.entry());
                        } else {
                            this.autofillFromInventory(recent);
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

    BlockPos tablePos() {
        return this.pos;
    }

    private void autofillFromInventory(PolydexMenu.RecentRecipe recent) {
        List<ItemStack> inventoryBefore = this.inventorySnapshot();
        List<ItemStack> gridBefore = this.inputSnapshot();
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

        PolydexMenu.RecipeCandidate selected = null;
        RecipeAutofill.Plan selectedPlan = null;
        boolean inventorySpaceBlocked = false;
        for (Identifier id : orderedIds) {
            PolydexMenu.RecipeCandidate candidate = candidatesById.get(id);
            if (candidate == null) {
                continue;
            }

            RecipeAutofill.PlanResult attempt = RecipeAutofill.plan(
                    candidate.recipe(),
                    inventoryBefore,
                    gridBefore
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

        this.sessionLastFilledRecipes.put(recent.entryId(), selected.id());
        PolydexMenu.rememberFilledRecipe(this.player, recent.entryId(), selected.id());
        this.updateResult();
        this.player.sendOverlayMessage(Component.literal("Filled recipe " + selected.id() + "."));
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
        ItemStack output = this.matchingRecipe(craftingInput(inputSnapshot))
                .map(holder -> holder.value().assemble(craftingInput(inputSnapshot)))
                .orElse(ItemStack.EMPTY);
        if (output.isEmpty()) {
            this.setSlot(RESULT_SLOT, this.filler());
            return;
        }

        this.setSlot(RESULT_SLOT, new GuiElementBuilder(output.copy())
                .setCallback((index, clickType, action, gui) -> {
                    if (action == ContainerInput.PICKUP || action == ContainerInput.QUICK_MOVE) {
                        this.craftOne(this.player);
                    }
                })
                .build());
    }

    private void craftOne(ServerPlayer player) {
        List<ItemStack> usedItems = this.inputSnapshot();

        Optional<RecipeHolder<CraftingRecipe>> match = this.matchingRecipe(craftingInput(usedItems));
        if (match.isEmpty()) {
            this.updateResult();
            return;
        }

        RecipeHolder<CraftingRecipe> recipe = match.get();
        ItemStack output = recipe.value().assemble(craftingInput(usedItems));
        if (output.isEmpty()) {
            this.updateResult();
            return;
        }

        List<ItemStack> remainders = recipe.value().getRemainingItems(craftingInput(usedItems)).stream()
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
        var inventory = this.player.getInventory();
        List<ItemStack> simulatedInventory = new ArrayList<>(36);
        for (int slot = 0; slot < 36; slot++) {
            simulatedInventory.add(inventory.getItem(slot).copy());
        }

        List<ItemStack> results = new ArrayList<>(remainders.size() + 1);
        results.add(output);
        results.addAll(remainders);

        for (ItemStack result : results) {
            if (result.isEmpty()) {
                continue;
            }

            ItemStack remaining = result.copy();
            int slotLimit = Math.min(remaining.getMaxStackSize(), inventory.getMaxStackSize());
            if (remaining.isStackable()) {
                for (ItemStack existing : simulatedInventory) {
                    if (!existing.isEmpty()
                            && ItemStack.isSameItemSameComponents(existing, remaining)
                            && existing.getCount() < slotLimit) {
                        int moved = Math.min(remaining.getCount(), slotLimit - existing.getCount());
                        existing.grow(moved);
                        remaining.shrink(moved);
                        if (remaining.isEmpty()) {
                            break;
                        }
                    }
                }
            }

            for (int slot = 0; slot < simulatedInventory.size() && !remaining.isEmpty(); slot++) {
                if (simulatedInventory.get(slot).isEmpty()) {
                    int moved = Math.min(remaining.getCount(), slotLimit);
                    ItemStack inserted = remaining.copy();
                    inserted.setCount(moved);
                    simulatedInventory.set(slot, inserted);
                    remaining.shrink(moved);
                }
            }

            if (!remaining.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean onClick(int index, ClickType clickType, ContainerInput action, GuiElement element) {
        return index == RESULT_SLOT
                || index == POLYDEX_SLOT
                || index == SPACER_SLOT
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
