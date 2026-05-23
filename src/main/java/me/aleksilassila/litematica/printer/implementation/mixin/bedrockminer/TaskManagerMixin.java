package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import me.aleksilassila.litematica.printer.utils.BedrockMinerCompact;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.github.bunnyi116.bedrockminer.task.TaskManager", remap = false)
public class TaskManagerMixin {
    @Inject(method = "setRunning(Z)V", at = @At("TAIL"))
    public void setRunning(boolean running, CallbackInfo ci) {
        BedrockMinerCompact.tasks.clear();
    }
}
