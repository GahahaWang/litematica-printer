package me.aleksilassila.litematica.printer.gui;

import fi.dy.masa.litematica.gui.GuiConfigs.ConfigGuiTab;

/**
 * Holder for the {@code ConfigGuiTab.PRINTER} constant that
 * {@link me.aleksilassila.litematica.printer.mixin.ConfigGuiTabMixin} appends to Litematica's
 * {@link ConfigGuiTab} enum while the enum is being initialized.
 */
public final class PrinterConfigGuiTab {
    private static ConfigGuiTab printerTab;

    private PrinterConfigGuiTab() {
    }

    /**
     * @return the PRINTER tab, or null if the mixin failed to append it.
     */
    public static ConfigGuiTab get() {
        if (printerTab == null) {
            // Force the enum's <clinit> to run, which is where the tab gets created.
            ConfigGuiTab.values();
        }

        return printerTab;
    }

    public static void set(ConfigGuiTab tab) {
        printerTab = tab;
    }
}
