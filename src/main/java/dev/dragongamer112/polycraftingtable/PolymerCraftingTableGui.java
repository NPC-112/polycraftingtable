package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PageTextures;
import eu.pb4.polydex.impl.book.ui.ExtendedGui;
import eu.pb4.polydex.impl.book.ui.GuiUtils;
import eu.pb4.sgui.api.ClickType;
import eu.pb4.sgui.api.elements.GuiElement;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
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
import java.util.List;
import java.util.Optional;

final class PolymerCraftingTableGui extends SimpleGui {
    private static final int[] INPUT_SLOTS = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int RESULT_SLOT = 24;
    private static final int ARROW_SLOT = 23;
    private static final int POLYDEX_SLOT = 53;

    private final BlockPos pos;
    private final SimpleContainer input = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            PolymerCraftingTableGui.this.updateResult();
        }
    };

    PolymerCraftingTableGui(ServerPlayer player, BlockPos pos) {
        super(MenuType.GENERIC_9x6, player, false);
        this.pos = pos;
        this.setTitle(ExtendedGui.formatTexturedText(
                player,
                PageTextures.CRAFTING,
                null,
                Component.literal("Polymer Crafting Table")
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
                .setCallback(() -> {
                    this.close();
                    PolyCraftingTableMod.openPolydexMenu(this.player);
                })
                .build());

        for (int inputIndex = 0; inputIndex < INPUT_SLOTS.length; inputIndex++) {
            int index = inputIndex;
            this.setSlot(INPUT_SLOTS[index], new Slot(this.input, index, 0, 0));
        }

        this.updateResult();

        this.open();
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
        return index == RESULT_SLOT;
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
    public void onPlayerClose(boolean success) {
        List<ItemStack> leftovers = this.input.getItems().stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        this.input.clearContent();
        for (ItemStack stack : leftovers) {
            this.player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
        super.onPlayerClose(success);
    }
}
