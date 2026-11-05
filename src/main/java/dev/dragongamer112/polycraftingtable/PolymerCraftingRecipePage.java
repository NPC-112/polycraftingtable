package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.AbstractRecipePolydexPage;
import eu.pb4.polydex.api.v1.recipe.PageBuilder;
import eu.pb4.polydex.api.v1.recipe.PolydexCategory;
import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polymer.core.api.item.PolymerItem;
import dev.dragongamer112.polycraftingtable.mixin.RecipeManagerAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class PolymerCraftingRecipePage extends AbstractRecipePolydexPage<CraftingRecipe> {
    static final PolydexCategory CATEGORY = PolydexCategory.of(
            Identifier.fromNamespaceAndPath(PolyCraftingTableMod.MOD_ID, "polymer_crafting")
    );

    private final Identifier pageId;

    private PolymerCraftingRecipePage(RecipeHolder<CraftingRecipe> recipe) {
        super(recipe);
        this.pageId = Identifier.fromNamespaceAndPath(
                PolyCraftingTableMod.MOD_ID,
                "recipe/" + recipe.id().identifier().getNamespace() + "/" + recipe.id().identifier().getPath()
        );
    }

    static void register() {
        PolydexPage.register((server, pageConsumer) -> {
            var recipes = ((RecipeManagerAccessor) server.getRecipeManager())
                    .polycraftingtable$getRecipes()
                    .byType(RecipeType.CRAFTING);
            for (RecipeHolder<CraftingRecipe> recipe : recipes) {
                CraftingInput emptyInput = CraftingInput.of(3, 3, emptyGrid());
                ItemStack output = recipe.value().assemble(emptyInput);
                if (isPolymerOutput(output)) {
                    pageConsumer.accept(new PolymerCraftingRecipePage(recipe));
                }
            }
        });
    }

    private static List<ItemStack> emptyGrid() {
        List<ItemStack> stacks = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            stacks.add(ItemStack.EMPTY);
        }
        return stacks;
    }

    private static boolean isPolymerOutput(ItemStack output) {
        return !output.isEmpty() && output.getItem() instanceof PolymerItem;
    }

    @Override
    public Identifier identifier() {
        return this.pageId;
    }

    @Override
    public ItemStack typeIcon(ServerPlayer player) {
        return Items.CRAFTING_TABLE.getDefaultInstance();
    }

    @Override
    public List<PolydexCategory> categories() {
        return List.of(CATEGORY);
    }

    @Override
    public void createPage(
            @Nullable PolydexEntry entry,
            ServerPlayer player,
            PageBuilder builder
    ) {
        var ingredients = this.recipe.placementInfo().ingredients();
        for (int slot = 0; slot < 9; slot++) {
            int x = slot % 3 + 2;
            int y = slot / 3 + 1;
            SlotDisplay display = slot < ingredients.size()
                    ? ingredients.get(slot).display()
                    : SlotDisplay.Empty.INSTANCE;
            builder.setIngredient(x, y, display);
        }

        builder.setOutput(6, 2, this.getOutput(entry, player.level().getServer()));
    }

    @Override
    public ItemStack getOutput(@Nullable PolydexEntry entry, MinecraftServer server) {
        return this.recipe.assemble(CraftingInput.of(3, 3, emptyGrid()));
    }

    @Override
    public boolean syncWithClient(ServerPlayer player) {
        return false;
    }
}
