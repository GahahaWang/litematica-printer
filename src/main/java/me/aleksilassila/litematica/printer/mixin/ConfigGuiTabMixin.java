package me.aleksilassila.litematica.printer.mixin;

import java.util.Arrays;

import fi.dy.masa.litematica.gui.GuiConfigs.ConfigGuiTab;
import me.aleksilassila.litematica.printer.PrinterReference;
import me.aleksilassila.litematica.printer.gui.PrinterConfigGuiTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends a {@code PRINTER} constant to Litematica's ConfigGuiTab enum, so that the printer
 * settings get their own tab in the config GUI instead of being mixed into the Generic and
 * Hotkeys tabs.
 */
@Mixin(value = ConfigGuiTab.class, remap = false)
public class ConfigGuiTabMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void litematicaPrinter$addPrinterTab(CallbackInfo ci) {
        ConfigGuiTab[] values = ConfigGuiTabInvoker.litematicaPrinter$getValues();
        ConfigGuiTab printer = ConfigGuiTabInvoker.litematicaPrinter$new(
                "PRINTER", values.length, PrinterReference.MOD_KEY + ".gui.button.config_gui.printer");

        ConfigGuiTab[] extended = Arrays.copyOf(values, values.length + 1);
        extended[values.length] = printer;

        ConfigGuiTabInvoker.litematicaPrinter$setValues(extended);
        PrinterConfigGuiTab.set(printer);
    }
}
