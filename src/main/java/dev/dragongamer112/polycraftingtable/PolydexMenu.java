package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polydex.api.v1.recipe.PolydexPage;
import eu.pb4.polydex.api.v1.recipe.PolydexPageUtils;
import eu.pb4.polydex.impl.PlayerInterface;
import eu.pb4.polydex.impl.book.ui.MainIndexGui;
import eu.pb4.polydex.impl.book.ui.PageViewerGui;
import eu.pb4.polydex.impl.book.view.crafting.AbstractCraftingRecipePage;
import dev.dragongamer112.polycraftingtable.mixin.PolydexRecipePageAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.CraftingRecipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only access to Polydex recipe entries and its per-player recent-item list. */
public final class PolydexMenu {
    private PolydexMenu() {
    }

    static boolean isReady() {
        return PolydexPageUtils.isReady();
    }

    static boolean openMainMenu(ServerPlayer player) {
        if (!isReady()) {
            return false;
        }

        return new MainIndexGui(player, -1).open();
    }

    static List<RecentRecipe> recentCraftingRecipes(ServerPlayer player, int limit) {
        if (!isReady() || limit <= 0) {
            return List.of();
        }

        List<Identifier> recentIds = new ArrayList<>(
                ((PlayerInterface) player.connection).polydex_lastViewed()
        );
        List<RecentRecipe> recentRecipes = new ArrayList<>(Math.min(limit, recentIds.size()));
        for (Identifier id : recentIds) {
            PolydexEntry entry = PolydexPageUtils.getEntry(id);
            if (entry == null) {
                continue;
            }

            CraftingRecipe recipe = entry.getVisiblePages(player).stream()
                    .map(PolydexMenu::recipeForPage)
                    .filter(Objects::nonNull)
                    .filter(RecipeAutofill::supports)
                    .findFirst()
                    .orElse(null);
            if (recipe == null) {
                continue;
            }

            ItemStack icon = entry.stack().toItemStack(player);
            if (icon.isEmpty()) {
                continue;
            }
            recentRecipes.add(new RecentRecipe(entry, recipe, icon.copy()));
            if (recentRecipes.size() == limit) {
                break;
            }
        }

        return List.copyOf(recentRecipes);
    }

    static boolean canOpenEntryPage(ServerPlayer player, PolydexEntry entry) {
        return isReady() && !entry.getVisiblePages(player).isEmpty();
    }

    static boolean openEntryPage(ServerPlayer player, PolydexEntry entry) {
        if (!canOpenEntryPage(player, entry)) {
            return false;
        }
        PageViewerGui.openEntry(player, entry, false, null);
        return true;
    }

    private static CraftingRecipe recipeForPage(PolydexPage page) {
        if (!(page instanceof AbstractCraftingRecipePage<?>)
                || !(page instanceof PolydexRecipePageAccessor accessor)) {
            return null;
        }
        Recipe<?> recipe = accessor.polycraftingtable$getRecipe();
        return recipe instanceof CraftingRecipe craftingRecipe ? craftingRecipe : null;
    }

    record RecentRecipe(PolydexEntry entry, CraftingRecipe recipe, ItemStack icon) {
    }
}
