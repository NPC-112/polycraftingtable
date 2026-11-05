package dev.dragongamer112.polycraftingtable;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PolymerCraftingTableGui extends SimpleGui {
    private static final int[] INPUT_SLOTS = {0, 1, 2, 9, 10, 11, 18, 19, 20};
    private static final int RESULT_SLOT = 14;
    private static final int POLYDEX_SLOT = 8;

    private final BlockPos pos;
    private final ResultContainer result = new ResultContainer();
    private final SimpleContainer input = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            PolymerCraftingTableGui.this.updateResult();
        }
    };

    PolymerCraftingTableGui(ServerPlayer player, BlockPos pos) {
        super(MenuType.GENERIC_9x3, player, false);
        this.pos = pos;
        this.setTitle(Component.translatable("block.polycraftingtable.polymer_crafting_table"));

        for (int slot = 0; slot < 27; slot++) {
            this.setSlot(slot, filler());
        }

        this.setSlot(4, new GuiElementBuilder(Items.ARROW)
                .setName(Component.translatable("gui.polycraftingtable.crafting_arrow"))
                .hideDefaultTooltip()
                .build());
        this.setSlot(POLYDEX_SLOT, new GuiElementBuilder(Items.KNOWLEDGE_BOOK)
                .setName(Component.translatable("gui.polycraftingtable.polydex_button"))
                .setCallback(() -> PolyCraftingTableMod.openPolydexMenu(this.player))
                .build());

        for (int inputIndex = 0; inputIndex < INPUT_SLOTS.length; inputIndex++) {
            int index = inputIndex;
            this.setSlot(INPUT_SLOTS[index], new Slot(this.input, index, 0, 0));
        }

        this.setSlot(RESULT_SLOT, new Slot(this.result, 0, 0, 0) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return !PolymerCraftingTableGui.this.result.getItem(0).isEmpty();
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                PolymerCraftingTableGui.this.craftOne(player);
                super.onTake(player, stack);
            }
        });

        this.open();
    }

    private static GuiElementBuilder filler() {
        return new GuiElementBuilder(Items.GLASS_PANE)
                .setName(Component.empty())
                .hideDefaultTooltip();
    }

    private CraftingInput craftingInput() {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            stacks.add(this.input.getItem(slot));
        }
        return CraftingInput.of(3, 3, stacks);
    }

    private Optional<RecipeHolder<CraftingRecipe>> polymerRecipe(CraftingInput input) {
        var recipes = ((dev.dragongamer112.polycraftingtable.mixin.RecipeManagerAccessor)
                this.player.level().getServer().getRecipeManager())
                .polycraftingtable$getRecipes()
                .byType(RecipeType.CRAFTING);
        return recipes.stream()
                .filter(holder -> holder.value().matches(input, this.player.level()))
                .filter(holder -> PolymerRecipes.isPolymerOutput(holder.value().assemble(input)))
                .findFirst();
    }

    private void updateResult() {
        CraftingInput craftingInput = this.craftingInput();
        ItemStack output = this.polymerRecipe(craftingInput)
                .map(holder -> holder.value().assemble(craftingInput))
                .orElse(ItemStack.EMPTY);
        this.result.setItem(0, output);
    }

    private void craftOne(Player player) {
        CraftingInput craftingInput = this.craftingInput();
        Optional<RecipeHolder<CraftingRecipe>> match = this.polymerRecipe(craftingInput);
        if (match.isEmpty()) {
            this.updateResult();
            return;
        }

        List<ItemStack> remainders = match.get().value().getRemainingItems(craftingInput);
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

        for (ItemStack remainder : remainders) {
            if (!remainder.isEmpty()) {
                player.getInventory().placeItemBackInInventory(remainder, Prediction.SERVER_ONLY);
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
    }

    @Override
    public ItemStack quickMove(int index) {
        if (index != RESULT_SLOT) {
            return super.quickMove(index);
        }

        Slot resultSlot = this.wrappedMenu.getSlot(RESULT_SLOT);
        if (!resultSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack output = resultSlot.getItem().copy();
        if (!this.canFitOutput(output)) {
            return ItemStack.EMPTY;
        }

        resultSlot.onTake(this.player, output);
        this.player.getInventory().placeItemBackInInventory(output.copy(), Prediction.SERVER_ONLY);
        return output;
    }

    private boolean canFitOutput(ItemStack output) {
        int remaining = output.getCount();
        var inventory = this.player.getInventory();
        int slotLimit = Math.min(output.getMaxStackSize(), inventory.getMaxStackSize());

        for (int slot = 0; slot < 36 && remaining > 0; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                remaining -= slotLimit;
            } else if (ItemStack.isSameItemSameComponents(existing, output)) {
                remaining -= Math.max(0, slotLimit - existing.getCount());
            }
        }
        return remaining <= 0;
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
        for (ItemStack stack : this.input.getItems()) {
            if (!stack.isEmpty()) {
                this.player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
            }
        }
        this.input.clearContent();
        super.onPlayerClose(success);
    }
}
