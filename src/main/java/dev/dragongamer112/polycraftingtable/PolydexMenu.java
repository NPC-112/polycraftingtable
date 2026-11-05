package dev.dragongamer112.polycraftingtable;

import eu.pb4.polydex.api.v1.recipe.PolydexPageUtils;
import eu.pb4.polydex.impl.book.ui.MainIndexGui;
import net.minecraft.server.level.ServerPlayer;

/** Keeps the Polydex implementation-level main-index dependency in one place. */
final class PolydexMenu {
    private PolydexMenu() {
    }

    static boolean openMainMenu(ServerPlayer player) {
        if (!PolydexPageUtils.isReady()) {
            return false;
        }

        return new MainIndexGui(player, -1).open();
    }
}
