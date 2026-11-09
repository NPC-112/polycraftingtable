package dev.dragongamer112.polycraftingtable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record OpenPlayerInventoryPayload() implements CustomPacketPayload {
    public static final Type<OpenPlayerInventoryPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(PolyCraftingTableMod.MOD_ID, "open_player_inventory")
    );
    public static final OpenPlayerInventoryPayload INSTANCE = new OpenPlayerInventoryPayload();
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPlayerInventoryPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
