package dev.dragongamer112.polycraftingtable.mixin;

import dev.dragongamer112.polycraftingtable.PolydexReturnSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

/** Observe vanilla transitions; no Polydex classes are modified. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMenuMixin {
    @Inject(method = "openMenu", at = @At("HEAD"))
    private void polycraftingtable$beforeMenuOpened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        PolydexReturnSession.beforeMenuOpened((ServerPlayer) (Object) this);
    }

    @Inject(method = "openMenu", at = @At("RETURN"))
    private void polycraftingtable$afterMenuOpened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        PolydexReturnSession.afterMenuOpened((ServerPlayer) (Object) this);
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void polycraftingtable$cancelReturnOnDeath(DamageSource source, CallbackInfo ci) {
        PolydexReturnSession.cancel((ServerPlayer) (Object) this);
    }
}
