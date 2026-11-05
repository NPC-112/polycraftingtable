package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.AbstractRecipePolydexPage;
import eu.pb4.polydex.api.v1.recipe.PageBuilder;
import eu.pb4.polydex.api.v1.recipe.PolydexCategory;
import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polydex.api.v1.recipe.PolydexPage;
import dev.dragongamer112.polycraftingtable.mixin.RecipeManagerAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.List;

final class PolymerCraftingRecipePage extends AbstractRecipePolydexPage<CraftingRecipe> {
    static final PolydexCategory CATEGORY = PolydexCategory.of(
            Identifier.fromNamespaceAndPath(PolyCraftingTableMod.MOD_ID, "polymer_crafting")
    );

    private final Identifier pageId;
    private final RecipeDisplay display;

    private PolymerCraftingRecipePage(RecipeHolder<CraftingRecipe> recipe, RecipeDisplay display, int index) {
        super(recipe);
        this.display = display;
        this.pageId = Identifier.fromNamespaceAndPath(
                PolyCraftingTableMod.MOD_ID,
                "recipe/" + recipe.id().identifier().getNamespace() + "/" + recipe.id().identifier().getPath() + "/" + index
        );
    }

    static void register() {
        PolydexPage.register((server, pageConsumer) -> {
            var recipes = ((RecipeManagerAccessor) server.getRecipeManager())
                    .polycraftingtable$getRecipes()
                    .byType(RecipeType.CRAFTING);
            int pages = 0;
            for (RecipeHolder<CraftingRecipe> recipe : recipes) {
                var displays = recipe.value().display();
                for (int index = 0; index < displays.size(); index++) {
                    var display = displays.get(index);
                    if (PolymerRecipes.isPolymerDisplay(display, server.overworld())) {
                        pageConsumer.accept(new PolymerCraftingRecipePage(recipe, display, index));
                        pages++;
                    }
                }
            }
            PolyCraftingTableMod.LOGGER.info(
                    "Registered {} Polymer crafting Polydex pages from {} crafting recipes.",
                    pages, recipes.size()
            );
        });
    }

    @Override
    public Identifier identifier() {
        return this.pageId;
    }

    @Override
    public ItemStack typeIcon(ServerPlayer player) {
        return PolyCraftingTableMod.TABLE_ITEM.getDefaultInstance();
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
        for (int slot = 0; slot < 9; slot++) {
            int x = slot % 3 + 2;
            int y = slot / 3 + 1;
            builder.setIngredient(x, y, ingredientAt(slot % 3, slot / 3));
        }

        builder.setOutput(6, 2, this.getOutput(entry, player.level().getServer()));
    }

    private SlotDisplay ingredientAt(int x, int y) {
        if (this.display instanceof ShapedCraftingRecipeDisplay shaped) {
            if (x < shaped.width() && y < shaped.height()) {
                return shaped.ingredients().get(x + y * shaped.width());
            }
        } else if (this.display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            int slot = x + y * 3;
            if (slot < shapeless.ingredients().size()) {
                return shapeless.ingredients().get(slot);
            }
        } else {
            var ingredients = this.recipe.placementInfo().ingredients();
            int slot = x + y * 3;
            if (slot < ingredients.size()) {
                return ingredients.get(slot).display();
            }
        }
        return SlotDisplay.Empty.INSTANCE;
    }

    @Override
    public ItemStack getOutput(@Nullable PolydexEntry entry, MinecraftServer server) {
        return PolymerRecipes.displayedOutput(this.display, server.overworld());
    }

    @Override
    public boolean syncWithClient(ServerPlayer player) {
        return false;
    }
}
