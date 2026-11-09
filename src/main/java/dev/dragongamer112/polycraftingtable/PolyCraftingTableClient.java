package dev.dragongamer112.polycraftingtable;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class PolyCraftingTableClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                OpenPlayerInventoryPayload.TYPE,
                (payload, context) -> {
                    if (context.player() != null) {
                        context.client().gui.setScreen(new InventoryScreen(context.player()));
                    }
                }
        );
    }
}
