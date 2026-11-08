package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
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

/** Navigation-only return context; never owns ingredients or modifies another GUI. */
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

    /** Called before replacement removes our crafting menu and returns its inputs. */
    public static void beforeMenuOpened(ServerPlayer player) {
        if (!SESSIONS.containsKey(player)
                && SguiUtils.getCurrentGui(player) instanceof PolymerCraftingTableGui crafting) {
            Session session = new Session(player, player.level(), crafting.tablePos());
            if (validOrigin(session)) {
                SESSIONS.put(player, session);
            }
        }
    }

    /** A rejected opening must not turn closing the unchanged table into a return. */
    public static void afterMenuOpened(ServerPlayer player) {
        if (SguiUtils.getCurrentGui(player) instanceof PolymerCraftingTableGui) {
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

        // Install before replacement. Follow the entire menu chain, including
        // layered SGUI backends, search screens, and another mod's inventories.
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
        if (opened) {
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
            } else if (SguiUtils.getCurrentGui(player) instanceof PolymerCraftingTableGui) {
                // Failed replacement left the original crafting screen intact.
                SESSIONS.remove(player, session);
            } else {
                // Do not replace an active menu. SGUI's layered GUI exposes its
                // BackendSimpleGui here, not the Polydex frontend class.
                session.exitObserved = false;
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
