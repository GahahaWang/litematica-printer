package me.aleksilassila.litematica.printer.event;

import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import me.aleksilassila.litematica.printer.PrinterReference;
import me.aleksilassila.litematica.printer.config.Configs;

public class PrinterInputHandler implements IKeybindProvider {
    private static final PrinterInputHandler INSTANCE = new PrinterInputHandler();

    private PrinterInputHandler() {
    }

    public static PrinterInputHandler getInstance() {
        return INSTANCE;
    }

    @Override
    public void addKeysToMap(IKeybindManager manager) {
        for (IHotkey hotkey : Configs.HOTKEY_LIST) {
            manager.addKeybindToMap(hotkey.getKeybind());
        }
    }

    @Override
    public void addHotkeys(IKeybindManager manager) {
        manager.addHotkeysForCategory(PrinterReference.MOD_NAME, PrinterReference.MOD_KEY + ".hotkeys.category.printer_hotkeys", Configs.HOTKEY_LIST);
    }
}
