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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only access to Polydex recipe entries and its per-player recent-item list. */
public final class PolydexMenu {
    static final int MAX_PERSISTED_RECENT_ITEMS = 64;

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

        List<Identifier> polydexRecentIds = new ArrayList<>(
                ((PlayerInterface) player.connection).polydex_lastViewed()
        );
        List<Identifier> savedRecentIds = player.getAttachedOrElse(
                PolyCraftingTableMod.RECENT_ITEM_IDS,
                List.of()
        );
        List<Identifier> recentIds = mergeRecentIds(polydexRecentIds, savedRecentIds);
        if (!recentIds.equals(savedRecentIds)) {
            player.setAttached(PolyCraftingTableMod.RECENT_ITEM_IDS, recentIds);
        }

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

    static List<Identifier> mergeRecentIds(
            List<Identifier> polydexRecentIds,
            List<Identifier> savedRecentIds
    ) {
        List<Identifier> merged = new ArrayList<>(MAX_PERSISTED_RECENT_ITEMS);
        Set<Identifier> seen = new HashSet<>(MAX_PERSISTED_RECENT_ITEMS);
        appendRecentIds(merged, seen, polydexRecentIds);
        appendRecentIds(merged, seen, savedRecentIds);
        return List.copyOf(merged);
    }

    private static void appendRecentIds(
            List<Identifier> destination,
            Set<Identifier> seen,
            List<Identifier> source
    ) {
        for (Identifier id : source) {
            if (id != null && seen.add(id)) {
                destination.add(id);
                if (destination.size() == MAX_PERSISTED_RECENT_ITEMS) {
                    return;
                }
            }
        }
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
