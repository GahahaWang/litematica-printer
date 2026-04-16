package me.aleksilassila.litematica.printer.implementation.mixin;

import me.aleksilassila.litematica.printer.LitematicaMixinMod;
import me.aleksilassila.litematica.printer.Printer;
import me.aleksilassila.litematica.printer.actions.PrepareAction;
import me.aleksilassila.litematica.printer.implementation.LocalPlayerRotationWrapper;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {
    @ModifyVariable(method = "useItemOn", at = @At("HEAD"), argsOnly = true)
    private LocalPlayer modifyPlayerForUseItemOn(LocalPlayer originalPlayer) {
        Printer printer = LitematicaMixinMod.printer;
        if (printer == null) {
            return originalPlayer;
        }

        PrepareAction action = printer.actionHandler.lookAction;
        if (action != null && (action.modifyYaw || action.modifyPitch)) {
            float yaw = action.modifyYaw ? action.yaw : originalPlayer.getYRot();
            float pitch = action.modifyPitch ? action.pitch : originalPlayer.getXRot();
            return LocalPlayerRotationWrapper.getInstance().rot(yaw, pitch);
        }

        return originalPlayer;
    }
}
