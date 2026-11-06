package dev.dragongamer112.polycraftingtable.mixin;

import dev.dragongamer112.polycraftingtable.PolymerRecipes;
import dev.dragongamer112.polycraftingtable.RecipeBookFilter;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundRecipeBookAddPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.CraftingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin {
    // Filter only outgoing book entries, never the server recipe registry or the
    // player's saved unlocks. This covers both initial sync and later unlocks.
    @Redirect(
            method = {"addRecipes", "sendInitialRecipeBook"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;send(Lnet/minecraft/network/protocol/Packet;)V"
            )
    )
    private void polycraftingtable$filterRecipeBook(
            ServerGamePacketListenerImpl connection, Packet<?> packet
    ) {
        if (packet instanceof ClientboundRecipeBookAddPacket book) {
            var player = connection.player;
            var manager = player.level().getServer().getRecipeManager();
            var entries = book.entries().stream().filter(entry -> {
                var info = manager.getRecipeFromDisplay(entry.contents().id());
                return info == null
                        || !(info.parent().value() instanceof CraftingRecipe)
                        || RecipeBookFilter.shouldIncludeCraftingDisplay(
                                PolymerRecipes.isPolymerDisplay(entry.contents().display(), player.level())
                        );
            }).toList();
            connection.send(new ClientboundRecipeBookAddPacket(entries, book.replace()));
        } else {
            connection.send(packet);
        }
    }
}
