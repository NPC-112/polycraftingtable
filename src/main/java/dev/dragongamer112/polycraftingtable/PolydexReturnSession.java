package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexEntry;
import eu.pb4.sgui.api.SguiUtils;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        open(player, new Session(player, player.level(), pos.immutable(), ReturnTarget.TABLE),
                () -> PolydexMenu.openMainMenu(player));
    }

    static void openEntry(ServerPlayer player, BlockPos pos, PolydexEntry entry) {
        open(player, new Session(player, player.level(), pos.immutable(), ReturnTarget.TABLE),
                () -> PolydexMenu.openEntryPage(player, entry));
    }

    static void openFromHand(ServerPlayer player) {
        if (SguiUtils.getCurrentGui(player) instanceof PolymerCraftingTableGui crafting) {
            openMain(player, crafting.tablePos());
            return;
        }
        if (player.containerMenu != player.inventoryMenu) {
            return;
        }
        open(player, null, () -> PolydexMenu.openMainMenu(player));
    }

    public static void openFromInventory(ServerPlayer player) {
        if (!ServerPlayNetworking.canSend(player, OpenPlayerInventoryPayload.TYPE)) {
            player.sendOverlayMessage(Component.translatableWithFallback(
                    "message.polycraftingtable.client_required",
                    "Install Polymer Crafting Table on your client to reopen the inventory here."
            ));
            return;
        }
        open(player, new Session(player, player.level(), null, ReturnTarget.PLAYER_INVENTORY),
                () -> PolydexMenu.openMainMenu(player));
    }

    /** Open Polydex from an item in any active menu; third-party menus are not restored. */
    public static void openFromMenu(ServerPlayer player) {
        if (SguiUtils.getCurrentGui(player) instanceof PolymerCraftingTableGui crafting) {
            openMain(player, crafting.tablePos());
            return;
        }
        if (player.containerMenu == player.inventoryMenu) {
            openFromInventory(player);
            return;
        }
        cancel(player);
        open(player, null, () -> PolydexMenu.openMainMenu(player), true);
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

    private static void open(ServerPlayer player, Session session, BooleanSupplier openMenu) {
        open(player, session, openMenu, false);
    }

    private static void open(
            ServerPlayer player,
            Session session,
            BooleanSupplier openMenu,
            boolean allowOtherMenus
    ) {
        cancel(player);
        if (session != null && !validOrigin(session)) {
            return;
        }
        // These entry points may replace our crafting menu, not an unrelated GUI.
        if (!allowOtherMenus
                && player.containerMenu != player.inventoryMenu
                && !(SguiUtils.getCurrentGui(player) instanceof PolymerCraftingTableGui)) {
            return;
        }

        // Install before replacement. Follow the entire menu chain, including
        // layered SGUI backends, search screens, and another mod's inventories.
        if (session != null) {
            SESSIONS.put(player, session);
        }
        boolean opened = false;
        try {
            // Let normal menu replacement run the crafting GUI's onRemoved
            // cleanup. If Polydex is unavailable, the existing grid stays open.
            opened = openMenu.getAsBoolean();
        } catch (RuntimeException exception) {
            PolyCraftingTableMod.LOGGER.warn("Could not open Polydex from the crafting table", exception);
        }

        if (session != null && SESSIONS.get(player) != session) {
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
        if (session != null) {
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
                        if (session.returnTarget == ReturnTarget.TABLE) {
                            new PolymerCraftingTableGui(player, session.pos);
                        } else if (ServerPlayNetworking.canSend(player, OpenPlayerInventoryPayload.TYPE)) {
                            ServerPlayNetworking.send(player, OpenPlayerInventoryPayload.INSTANCE);
                        }
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
        boolean playerIsValid = !player.hasDisconnected()
                && !player.isRemoved()
                && player.isAlive()
                && player.level() == session.level;
        if (!playerIsValid) {
            return false;
        }
        return session.returnTarget == ReturnTarget.PLAYER_INVENTORY
                || (player.position().distanceToSqr(Vec3.atCenterOf(session.pos)) <= 18 * 18
                && session.level.hasChunkAt(session.pos)
                && session.level.getBlockState(session.pos).is(PolyCraftingTableMod.TABLE));
    }

    private enum ReturnTarget {
        TABLE,
        PLAYER_INVENTORY
    }

    private static final class Session {
        private final ServerPlayer player;
        private final Level level;
        private final ReturnTarget returnTarget;
        private final BlockPos pos;
        private boolean exitObserved;

        private Session(ServerPlayer player, Level level, BlockPos pos) {
            this(player, level, pos, ReturnTarget.TABLE);
        }

        private Session(ServerPlayer player, Level level, BlockPos pos, ReturnTarget returnTarget) {
            this.player = player;
            this.level = level;
            this.pos = pos;
            this.returnTarget = returnTarget;
        }
    }
}
