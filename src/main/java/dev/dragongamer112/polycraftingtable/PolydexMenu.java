package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexPage;
import eu.pb4.polydex.api.v1.recipe.PolydexPageUtils;
import eu.pb4.polydex.impl.book.ui.ExtendedGui;
import eu.pb4.polydex.impl.book.ui.MainIndexGui;
import eu.pb4.polydex.impl.book.view.crafting.AbstractCraftingRecipePage;
import eu.pb4.sgui.api.containerwrappers.SlotBasedWrapperMenu;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps Polydex's implementation-level integrations and table-origin state together. */
final class PolydexMenu {
    private static final Map<UUID, TableOrigin> TABLE_ORIGINS = new HashMap<>();

    private PolydexMenu() {
    }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> TABLE_ORIGINS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            return player == null
                    || !isValidOrigin(player, entry.getValue())
                    || !isPolydexMenu(player);
        }));
    }

    static boolean openMainMenu(ServerPlayer player, BlockPos pos) {
        TABLE_ORIGINS.remove(player.getUUID());
        TableOrigin origin = new TableOrigin(pos.immutable(), player.level().dimension());
        if (!PolydexPageUtils.isReady() || !isValidOrigin(player, origin)) {
            return false;
        }

        TABLE_ORIGINS.put(player.getUUID(), origin);
        boolean opened = new MainIndexGui(player, -1).open();
        if (!opened) {
            TABLE_ORIGINS.remove(player.getUUID());
        }
        return opened;
    }

    static boolean hasValidTableOrigin(ServerPlayer player) {
        TableOrigin origin = TABLE_ORIGINS.get(player.getUUID());
        if (origin == null || !isValidOrigin(player, origin) || !isPolydexMenu(player)) {
            TABLE_ORIGINS.remove(player.getUUID());
            return false;
        }
        return true;
    }

    static CraftingRecipe recipeForPage(PolydexPage page) {
        if (!(page instanceof AbstractCraftingRecipePage<?>)
                || !(page instanceof PolydexRecipePageAccessor accessor)) {
            return null;
        }
        Recipe<?> recipe = accessor.polycraftingtable$getRecipe();
        return recipe instanceof CraftingRecipe craftingRecipe ? craftingRecipe : null;
    }

    static void returnToTable(ServerPlayer player, CraftingRecipe recipe) {
        TableOrigin origin = TABLE_ORIGINS.get(player.getUUID());
        if (origin == null || !isValidOrigin(player, origin)) {
            TABLE_ORIGINS.remove(player.getUUID());
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(
                    "The Polymer Crafting Table is no longer available."
            ));
            return;
        }

        TABLE_ORIGINS.remove(player.getUUID());
        new PolymerCraftingTableGui(player, origin.pos(), recipe);
    }

    private static boolean isPolydexMenu(ServerPlayer player) {
        return player.containerMenu instanceof SlotBasedWrapperMenu menu
                && menu.getBackingGui() instanceof ExtendedGui;
    }

    private static boolean isValidOrigin(ServerPlayer player, TableOrigin origin) {
        return player.level().dimension().equals(origin.dimension())
                && player.position().distanceToSqr(Vec3.atCenterOf(origin.pos())) <= 18 * 18
                && player.level().getBlockState(origin.pos()).is(PolyCraftingTableMod.TABLE);
    }

    private record TableOrigin(BlockPos pos, ResourceKey<Level> dimension) {
    }
}
