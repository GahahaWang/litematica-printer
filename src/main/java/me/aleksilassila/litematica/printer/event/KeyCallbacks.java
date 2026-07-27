package me.aleksilassila.litematica.printer.event;

import fi.dy.masa.malilib.hotkeys.KeyCallbackToggleBooleanConfigWithMessage;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;

public class KeyCallbacks {
    public static void init(Minecraft mc) {
        Configs.TOGGLE_PRINTING_MODE.getKeybind().setCallback(new KeyCallbackToggleBooleanConfigWithMessage(Configs.PRINT_MODE));
    }
}
