package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.polydex.impl.book.ui.MainIndexGui;
import eu.pb4.polydex.impl.book.ui.PageViewerGui;
import eu.pb4.polydex.impl.book.ui.SearchGui;
import eu.pb4.sgui.api.SguiUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/** Return context owned by this mod, never installed into Polydex's UI. */
public final class PolydexReturnSession {
    private static final Map<ServerPlayer, Session> SESSIONS = new IdentityHashMap<>();

    private PolydexReturnSession() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SESSIONS.clear());
    }

    static void openMain(ServerPlayer player, BlockPos pos) {
        open(player, pos, () -> PolydexMenu.openMainMenu(player));
    }

    static void openEntry(ServerPlayer player, BlockPos pos, PolydexEntry entry) {
        open(player, pos, () -> PolydexMenu.openEntryPage(player, entry));
    }

    public static void cancel(ServerPlayer player) {
        SESSIONS.remove(player);
    }

    /** Called after vanilla opens a menu, including other mods' SGUI screens. */
    public static void onMenuOpened(ServerPlayer player) {
        if (SESSIONS.containsKey(player) && !isPolydexMenu(player)) {
            cancel(player);
        }
    }

    private static void open(ServerPlayer player, BlockPos pos, BooleanSupplier openMenu) {
        cancel(player);
        Session session = new Session(player, player.level(), pos.immutable());
        if (!validOrigin(session)) {
            return;
        }
        // These entry points may replace our crafting menu, not an unrelated GUI.
        if (player.containerMenu != player.inventoryMenu
                && !(SguiUtils.getCurrentGui(player) instanceof PolymerCraftingTableGui)) {
            return;
        }

        // Install first so a mod replacing Polydex during its own opening
        // callbacks can cancel this context, even if that screen closes at once.
        SESSIONS.put(player, session);
        boolean opened = false;
        try {
            // Let normal menu replacement run the crafting GUI's onRemoved
            // cleanup. If Polydex is unavailable, the existing grid stays open.
            opened = openMenu.getAsBoolean();
        } catch (RuntimeException exception) {
            PolyCraftingTableMod.LOGGER.warn("Could not open Polydex from the crafting table", exception);
        }

        if (SESSIONS.get(player) != session) {
            return;
        }
        if (opened && isPolydexMenu(player)) {
            return;
        }
        if (!opened) {
            player.sendOverlayMessage(Component.literal("Polydex is not ready yet. Try again in a moment."));
        }
        // A failed sneak-entry (or an immediately closed menu) has no crafting
        // screen to keep. Restore it later, only if still safe. Otherwise leave
        // the original crafting screen and its ingredients entirely untouched.
        if (player.containerMenu != player.inventoryMenu) {
            SESSIONS.remove(player, session);
        }
    }

    private static void tick() {
        // Snapshot before opening screens: constructors/listeners may cancel or
        // replace a session. Identity checks keep any newer session untouched.
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerPlayer player = session.player;
            if (SESSIONS.get(player) != session) {
                continue;
            }
            if (!validOrigin(session)) {
                SESSIONS.remove(player, session);
                continue;
            }

            if (player.containerMenu == player.inventoryMenu) {
                if (session.exitObserved) {
                    // Two end-of-tick observations leave a full tick for
                    // Polydex's native Back/search navigation to settle.
                    if (SESSIONS.remove(player, session)) {
                        new PolymerCraftingTableGui(player, session.pos);
                    }
                } else {
                    session.exitObserved = true;
                }
            } else if (isPolydexMenu(player)) {
                session.exitObserved = false;
            } else {
                // Also catches menus not opened through vanilla openMenu.
                SESSIONS.remove(player, session);
            }
        }
    }

    private static boolean validOrigin(Session session) {
        ServerPlayer player = session.player;
        return !player.hasDisconnected()
                && !player.isRemoved()
                && player.isAlive()
                && player.level() == session.level
                && player.position().distanceToSqr(Vec3.atCenterOf(session.pos)) <= 18 * 18
                && session.level.hasChunkAt(session.pos)
                && session.level.getBlockState(session.pos).is(PolyCraftingTableMod.TABLE);
    }

    private static boolean isPolydexMenu(ServerPlayer player) {
        var gui = SguiUtils.getCurrentGui(player);
        // Search is an AnvilInputGui, not an ExtendedGui. Do not classify every
        // ExtendedGui as Polydex: another mod might use it for its own screen.
        return gui instanceof MainIndexGui || gui instanceof PageViewerGui || gui instanceof SearchGui;
    }

    private static final class Session {
        private final ServerPlayer player;
        private final Level level;
        private final BlockPos pos;
        private boolean exitObserved;

        private Session(ServerPlayer player, Level level, BlockPos pos) {
            this.player = player;
            this.level = level;
            this.pos = pos;
        }
    }
}
