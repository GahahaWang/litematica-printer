package me.aleksilassila.litematica.printer;

import fi.dy.masa.malilib.event.InitializationHandler;
import fi.dy.masa.malilib.event.TickHandler;
import me.aleksilassila.litematica.printer.event.KeyCallbacks;
import me.aleksilassila.litematica.printer.event.PrinterInitHandler;
import me.aleksilassila.litematica.printer.utils.BedrockMinerCompact;
import me.aleksilassila.litematica.printer.utils.Breaker;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;

public class LitematicaMixinMod implements ModInitializer {
    public static Printer printer;
    public static Breaker breaker;
    public static BedrockMinerCompact bedrockMinerCompact;

    @Override
    public void onInitialize() {
        InitializationHandler.getInstance().registerInitializationHandler(new PrinterInitHandler());

        breaker = new Breaker(Minecraft.getInstance());
        bedrockMinerCompact = new BedrockMinerCompact();
        BedrockMinerCompact.init();
        TickHandler.getInstance().registerClientTickHandler(breaker);

        KeyCallbacks.init(Minecraft.getInstance());
        Printer.logger.info("{} initialized.", PrinterReference.MOD_STRING);
    }
}
