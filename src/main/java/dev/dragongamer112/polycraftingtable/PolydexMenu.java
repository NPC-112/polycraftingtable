package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polydex.api.v1.recipe.PolydexPage;
import eu.pb4.polydex.api.v1.recipe.PolydexPageUtils;
import eu.pb4.polydex.impl.PlayerInterface;
import eu.pb4.polydex.impl.book.ui.MainIndexGui;
import eu.pb4.polydex.impl.book.view.crafting.AbstractCraftingRecipePage;
import dev.dragongamer112.polycraftingtable.mixin.PolydexRecipePageAccessor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        pruneLastFilledRecipes(player, recentIds);

        List<RecentRecipe> recentRecipes = new ArrayList<>(Math.min(limit, recentIds.size()));
        RecipeManager recipeManager = player.level().getServer().getRecipeManager();
        Map<Recipe<?>, Identifier> recipeIds = new IdentityHashMap<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            recipeIds.put(holder.value(), holder.id().identifier());
        }
        for (Identifier id : recentIds) {
            PolydexEntry entry = PolydexPageUtils.getEntry(id);
            if (entry == null) {
                continue;
            }

            List<RecipeCandidate> recipes = recipesForEntry(entry, player, recipeIds);
            if (recipes.isEmpty()) {
                continue;
            }

            ItemStack icon = entry.stack().toItemStack(player);
            if (icon.isEmpty()) {
                continue;
            }
            recentRecipes.add(new RecentRecipe(id, entry, recipes, icon.copy()));
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

    static Identifier lastFilledRecipeId(ServerPlayer player, Identifier entryId) {
        String id = player.getAttachedOrElse(PolyCraftingTableMod.LAST_FILLED_RECIPE_IDS, Map.of())
                .get(entryId.toString());
        return id == null ? null : Identifier.tryParse(id);
    }

    static void rememberFilledRecipe(ServerPlayer player, Identifier entryId, Identifier recipeId) {
        Map<String, String> saved = player.getAttachedOrElse(PolyCraftingTableMod.LAST_FILLED_RECIPE_IDS, Map.of());
        List<Identifier> recentIds = player.getAttachedOrElse(PolyCraftingTableMod.RECENT_ITEM_IDS, List.of());
        LinkedHashMap<String, String> updated = new LinkedHashMap<>();
        boolean entryIncluded = false;
        for (Identifier recentId : recentIds) {
            String key = recentId.toString();
            if (recentId.equals(entryId)) {
                updated.put(key, recipeId.toString());
                entryIncluded = true;
            } else if (saved.containsKey(key)) {
                updated.put(key, saved.get(key));
            }
        }
        if (!entryIncluded) {
            updated.put(entryId.toString(), recipeId.toString());
        }
        player.setAttached(PolyCraftingTableMod.LAST_FILLED_RECIPE_IDS, Map.copyOf(updated));
    }

    private static void pruneLastFilledRecipes(ServerPlayer player, List<Identifier> recentIds) {
        Map<String, String> saved = player.getAttachedOrElse(PolyCraftingTableMod.LAST_FILLED_RECIPE_IDS, Map.of());
        LinkedHashMap<String, String> retained = new LinkedHashMap<>();
        for (Identifier id : recentIds) {
            String key = id.toString();
            String value = saved.get(key);
            if (value != null) {
                retained.put(key, value);
            }
        }
        if (!retained.equals(saved)) {
            player.setAttached(PolyCraftingTableMod.LAST_FILLED_RECIPE_IDS, Map.copyOf(retained));
        }
    }

    private static List<RecipeCandidate> recipesForEntry(
            PolydexEntry entry,
            ServerPlayer player,
            Map<Recipe<?>, Identifier> recipeIds
    ) {
        LinkedHashMap<Identifier, RecipeCandidate> candidates = new LinkedHashMap<>();
        for (PolydexPage page : entry.getVisiblePages(player)) {
            RecipeCandidate candidate = recipeForPage(page, recipeIds);
            if (candidate != null && RecipeAutofill.supports(candidate.recipe())) {
                candidates.putIfAbsent(candidate.id(), candidate);
            }
        }
        return List.copyOf(candidates.values());
    }

    private static void appendRecentIds(
            List<Identifier> destination,
            Set<Identifier> seen,
            List<Identifier> source
    ) {
        for (Identifier id : source) {
            if (destination.size() >= MAX_PERSISTED_RECENT_ITEMS) {
                return;
            }
            if (id != null && seen.add(id)) {
                destination.add(id);
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
        return PolydexPageUtils.openRecipeListUi(player, entry, null);
    }

    private static RecipeCandidate recipeForPage(PolydexPage page, Map<Recipe<?>, Identifier> recipeIds) {
        if (!(page instanceof AbstractCraftingRecipePage<?>)
                || !(page instanceof PolydexRecipePageAccessor accessor)) {
            return null;
        }
        Recipe<?> recipe = accessor.polycraftingtable$getRecipe();
        Identifier id = recipeIds.get(recipe);
        return recipe instanceof CraftingRecipe craftingRecipe && id != null
                ? new RecipeCandidate(id, craftingRecipe)
                : null;
    }

    record RecipeCandidate(Identifier id, CraftingRecipe recipe) {
    }

    record RecentRecipe(
            Identifier entryId,
            PolydexEntry entry,
            List<RecipeCandidate> recipes,
            ItemStack icon
    ) {
    }
}
