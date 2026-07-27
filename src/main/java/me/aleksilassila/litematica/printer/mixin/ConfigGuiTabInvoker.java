package me.aleksilassila.litematica.printer.mixin;

import fi.dy.masa.litematica.gui.GuiConfigs.ConfigGuiTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ConfigGuiTab.class, remap = false)
public interface ConfigGuiTabInvoker {
    @Invoker("<init>")
    static ConfigGuiTab litematicaPrinter$new(String internalName, int internalId, String translationKey) {
        throw new AssertionError();
    }

    @Accessor("$VALUES")
    static ConfigGuiTab[] litematicaPrinter$getValues() {
        throw new AssertionError();
    }

    @Mutable
    @Accessor("$VALUES")
    static void litematicaPrinter$setValues(ConfigGuiTab[] values) {
        throw new AssertionError();
    }
}
