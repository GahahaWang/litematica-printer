package me.aleksilassila.litematica.printer.event;

import fi.dy.masa.malilib.config.ConfigManager;
import fi.dy.masa.malilib.event.InputEventHandler;
import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import me.aleksilassila.litematica.printer.PrinterReference;
import me.aleksilassila.litematica.printer.config.Configs;

public class PrinterInitHandler implements IInitializationHandler {
    @Override
    public void registerModHandlers() {
        ConfigManager.getInstance().registerConfigHandler(PrinterReference.MOD_ID, new Configs());
        InputEventHandler.getKeybindManager().registerKeybindProvider(PrinterInputHandler.getInstance());
    }
}
