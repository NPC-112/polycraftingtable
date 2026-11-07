package dev.dragongamer112.polycraftingtable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

final class CraftingTableGuiTextures {
    // ExtendedGui starts an overlay at the container's left edge and expects
    // 176px total advance. Our font advances 133 + 19 (18px bitmap + 1) + 24.
    // The bitmap's ascent places it at y=107: the border of chest slot 52.
    static final Component RECENT_SPACER = Component.literal("\uE001\uE000\uE002")
            .setStyle(Style.EMPTY.withColor(0xFFFFFF).withFont(new FontDescription.Resource(
                    Identifier.fromNamespaceAndPath(PolyCraftingTableMod.MOD_ID, "crafting_gui")
            )));

    private CraftingTableGuiTextures() {
    }
}
