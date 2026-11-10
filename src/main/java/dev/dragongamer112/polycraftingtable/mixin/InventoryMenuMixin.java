package dev.dragongamer112.polycraftingtable.mixin;

import dev.dragongamer112.polycraftingtable.PolydexReturnSession;
import dev.dragongamer112.polycraftingtable.PolyCraftingTableMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class InventoryMenuMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void polycraftingtable$openPolydexFromInventory(
            int slotId,
            int button,
            ContainerInput input,
            Player player,
            CallbackInfo ci
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || player.containerMenu != (Object) this
                || input != ContainerInput.PICKUP
                || button != 1
                || slotId < 0
                || slotId >= ((AbstractContainerMenu) (Object) this).slots.size()) {
            return;
        }

        if (((AbstractContainerMenu) (Object) this).getSlot(slotId).getItem()
                .is(PolyCraftingTableMod.POLYDEX_ITEM)) {
            PolydexReturnSession.openFromMenu(serverPlayer);
            ci.cancel();
        }
    }
}
