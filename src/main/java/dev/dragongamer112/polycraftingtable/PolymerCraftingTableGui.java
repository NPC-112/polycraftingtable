package dev.dragongamer112.polycraftingtable;

import eu.pb4.polymer.core.api.item.PolymerItem;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlaceGhostRecipePacket;
import net.minecraft.util.Prediction;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PolymerCraftingTableGui extends SimpleGui {
    private final BlockPos pos;
    private final ResultContainer result = new ResultContainer();
    private final RecipeManager.CachedCheck<CraftingInput, CraftingRecipe> recipeCheck =
            RecipeManager.createCheck(RecipeType.CRAFTING);
    private final SimpleContainer input = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            PolymerCraftingTableGui.this.updateResult();
        }
    };

    PolymerCraftingTableGui(ServerPlayer player, BlockPos pos) {
        super(MenuType.CRAFTING, player, false);
        this.pos = pos;
        this.setTitle(Component.translatable("block.polycraftingtable.polymer_crafting_table"));
        this.setSlot(0, new Slot(this.result, 0, 0, 0) {
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

        for (int slot = 0; slot < 9; slot++) {
            this.setSlot(slot + 1, new Slot(this.input, slot, 0, 0));
        }

        this.open();
    }

    private CraftingInput craftingInput() {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            stacks.add(this.input.getItem(slot));
        }
        return CraftingInput.of(3, 3, stacks);
    }

    private Optional<RecipeHolder<CraftingRecipe>> polymerRecipe(CraftingInput input) {
        return this.recipeCheck.getRecipeFor(input, this.player.level())
                .filter(holder -> isPolymerResult(holder.value().assemble(input)));
    }

    private static boolean isPolymerResult(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PolymerItem;
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
            if (remainder.isEmpty()) {
                continue;
            }
            player.getInventory().placeItemBackInInventory(remainder, Prediction.SERVER_ONLY);
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
    public void onCraftRequest(RecipeDisplayId recipeId, boolean shift) {
        var display = this.player.level().getServer().getRecipeManager().getRecipeFromDisplay(recipeId);
        if (display == null || !(display.parent().value() instanceof CraftingRecipe craftingRecipe)) {
            return;
        }

        RecipeHolder<CraftingRecipe> selected = (RecipeHolder<CraftingRecipe>) display.parent();
        if (!isPolymerResult(craftingRecipe.assemble(this.craftingInput()))) {
            return;
        }

        RecipeBookMenu.PostPlaceAction action = ServerPlaceRecipe.placeRecipe(
                new ServerPlaceRecipe.CraftingMenuAccess<>() {
                    @Override
                    public void fillCraftSlotsStackedContents(StackedItemContents finder) {
                        for (int slot = 0; slot < 9; slot++) {
                            finder.accountStack(PolymerCraftingTableGui.this.input.getItem(slot));
                        }
                    }

                    @Override
                    public void clearCraftingContent() {
                    }

                    @Override
                    public boolean recipeMatches(RecipeHolder<CraftingRecipe> recipe) {
                        CraftingInput input = PolymerCraftingTableGui.this.craftingInput();
                        return isPolymerResult(recipe.value().assemble(input))
                                && recipe.value().matches(input, player.level());
                    }
                },
                3,
                3,
                this.wrappedMenu.slots.subList(1, 10),
                this.wrappedMenu.slots.subList(1, 10),
                this.player.getInventory(),
                selected,
                false,
                false
        );

        if (action == RecipeBookMenu.PostPlaceAction.PLACE_GHOST_RECIPE) {
            this.player.connection.send(
                    new ClientboundPlaceGhostRecipePacket(
                            this.player.containerMenu.containerId,
                            display.display().display()
                    )
            );
        }
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
            if (stack.isEmpty()) {
                continue;
            }
            this.player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
        }
        this.input.clearContent();
        super.onPlayerClose(success);
    }
}
