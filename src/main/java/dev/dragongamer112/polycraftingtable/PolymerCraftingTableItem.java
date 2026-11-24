package dev.dragongamer112.polycraftingtable;

import eu.pb4.polymer.core.api.item.PolymerBlockItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import org.jspecify.annotations.Nullable;

public final class PolymerCraftingTableItem extends PolymerBlockItem {
    public PolymerCraftingTableItem(Block block, Item.Properties properties) {
        super(block, properties, Items.CRAFTING_TABLE);
    }

    @Override
    public @Nullable Identifier getPolymerItemModel(
            ItemStack stack,
            PacketContext context,
            HolderLookup.Provider lookup
    ) {
        return PolyCraftingTableMod.hasTexturedTableAppearance(context)
                ? PolyCraftingTableMod.TABLE_ITEM_MODEL_ID
                : null;
    }
}
