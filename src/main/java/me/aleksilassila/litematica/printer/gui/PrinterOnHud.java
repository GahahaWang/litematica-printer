package me.aleksilassila.litematica.printer.gui;

import me.aleksilassila.litematica.printer.PrinterReference;
import me.aleksilassila.litematica.printer.config.Configs;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.profiling.Profiler;

public class PrinterOnHud {
    private static final long PRINTER_FADE_OUT_MS = 3000L;
    private boolean printerWasOn = false;
    private long printerOffAtMs = -1L;
    public static void registerHud() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(PrinterReference.MOD_ID, "printerOnMessage"),
                new PrinterOnHud()::renderPrinerOnHud
        );
    }

    public void renderPrinerOnHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!Configs.PRINTER_ON_HUD_ENABLED.getBooleanValue()) {
            return;
        }

        boolean isOn = Configs.PRINT_MODE.getBooleanValue() || Configs.PRINT.getKeybind().isPressed();
        long now = System.currentTimeMillis();

        if (isOn) {
            this.printerWasOn = true;
            this.printerOffAtMs = -1L;
        } else if (this.printerWasOn) {
            this.printerWasOn = false;
            this.printerOffAtMs = now;
        }

        float fadeFactor;
        if (isOn) {
            fadeFactor = 1.0F;
        } else if (this.printerOffAtMs >= 0) {
            long elapsed = now - this.printerOffAtMs;
            if (elapsed >= PRINTER_FADE_OUT_MS) {
                return;
            }
            fadeFactor = 1.0F - (float) elapsed / PRINTER_FADE_OUT_MS;
        } else {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int color = ARGB.multiplyAlpha(Configs.PRINTER_ON_HUD_COLOR.getIntegerValue(), fadeFactor);

        Profiler.get().push("printerOnMessage");
        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) Configs.PRINTER_ON_HUD_X.getIntegerValue(), (float) Configs.PRINTER_ON_HUD_Y.getIntegerValue());
        float scale = (float) Configs.PRINTER_ON_HUD_SCALE.getDoubleValue();
        graphics.pose().scale(scale, scale);
        final Component message = Component.literal("PrinterOn");
        int width = font.width(message);
        graphics.textWithBackdrop(font, message, 0, 0, width, color);
        graphics.pose().popMatrix();
        Profiler.get().pop();
    }
}
