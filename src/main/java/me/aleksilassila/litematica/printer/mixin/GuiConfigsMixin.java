package me.aleksilassila.litematica.printer.mixin;

import java.util.List;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.gui.GuiConfigs;
import fi.dy.masa.litematica.gui.GuiConfigs.ConfigGuiTab;
import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import me.aleksilassila.litematica.printer.PrinterReference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.gui.PrinterConfigGuiTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuiConfigs.class, remap = false)
public class GuiConfigsMixin {
    @Inject(method = "getConfigs", at = @At("HEAD"), cancellable = true)
    private void litematicaPrinter$getPrinterConfigs(CallbackInfoReturnable<List<ConfigOptionWrapper>> cir) {
        if (litematicaPrinter$isPrinterTab()) {
            cir.setReturnValue(ConfigOptionWrapper.createFor(Configs.getPrinterTabConfigs()));
        }
    }

    @Inject(method = "getAllConfigs", at = @At("RETURN"))
    private void litematicaPrinter$addPrinterConfigsToAllTab(CallbackInfoReturnable<List<ConfigOptionWrapper>> cir) {
        cir.getReturnValue().addAll(ConfigOptionWrapper.createFor(Configs.getPrinterTabConfigs()));
    }

    @Inject(method = "useKeybindSearch", at = @At("HEAD"), cancellable = true)
    private void litematicaPrinter$useKeybindSearch(CallbackInfoReturnable<Boolean> cir) {
        if (litematicaPrinter$isPrinterTab()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "onSettingsChanged", at = @At("TAIL"))
    private void litematicaPrinter$savePrinterConfigs(CallbackInfo ci) {
        ConfigManager.getInstance().onConfigsChanged(PrinterReference.MOD_ID);
    }

    @Unique
    private static boolean litematicaPrinter$isPrinterTab() {
        ConfigGuiTab printerTab = PrinterConfigGuiTab.get();
        return printerTab != null && DataManager.getConfigGuiTab() == printerTab;
    }
}
