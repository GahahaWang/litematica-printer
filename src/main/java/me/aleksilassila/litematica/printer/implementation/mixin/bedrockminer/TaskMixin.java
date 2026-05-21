package me.aleksilassila.litematica.printer.implementation.mixin.bedrockminer;

import com.github.bunnyi116.bedrockminer.task.Task;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static me.aleksilassila.litematica.printer.utils.BedrockMinerCompact.overlayMessage;

@Pseudo
@Mixin(value = Task.class, remap = false)
public class TaskMixin {

    @Dynamic
    @Inject(method = "find",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/bunnyi116/bedrockminer/util/MessageUtils;setOverlayMessage(Lnet/minecraft/network/chat/Component;)V"
            )
    , remap = false, cancellable = true)
    public void setOverlayMessage (CallbackInfo ci) {
        if (!overlayMessage)
            ci.cancel();
    }
}
