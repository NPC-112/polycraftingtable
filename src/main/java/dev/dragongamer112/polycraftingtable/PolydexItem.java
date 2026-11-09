package dev.dragongamer112.polycraftingtable;

import eu.pb4.polymer.core.api.item.SimplePolymerItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class PolydexItem extends SimplePolymerItem {
    public PolydexItem(Item.Properties properties) {
        super(properties, Items.KNOWLEDGE_BOOK);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            PolyCraftingTableMod.openPolydexFromHand(serverPlayer);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}
